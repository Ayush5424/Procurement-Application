package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.*;
import com.cantill.procurement.repository.ItemRepository;
import com.cantill.procurement.repository.PolicyRuleRepository;
import com.cantill.procurement.repository.PolicyVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProcurementDecisionEngine {
    private static final Logger log = LoggerFactory.getLogger(ProcurementDecisionEngine.class);

    private final PolicyVersionRepository policyVersionRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final ItemRepository itemRepository;
    private final ValuationService valuationService;
    private final SupplierRankingService supplierRankingService;
    private final ContractEngineService contractEngineService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record RequirementDecision(
            ClubbingService.RequirementDraft requirementDraft,
            String decision, // "PO", "SOURCING_REQUEST", "NOT_PROCESSED"
            String route,
            String selectedSupplierId,
            BigDecimal unitPrice,
            BigDecimal requirementValue,
            String contractIdUsed,
            String historicalPoUsed,
            List<String> suggestedSupplierIds,
            String policyVersion,
            String reason,
            String rankingExplanation,
            String valuationExplanation,
            boolean isCapexApprovalRequired
    ) {}

    public ProcurementDecisionEngine(
            PolicyVersionRepository policyVersionRepository,
            PolicyRuleRepository policyRuleRepository,
            ItemRepository itemRepository,
            ValuationService valuationService,
            SupplierRankingService supplierRankingService,
            ContractEngineService contractEngineService) {
        this.policyVersionRepository = policyVersionRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.itemRepository = itemRepository;
        this.valuationService = valuationService;
        this.supplierRankingService = supplierRankingService;
        this.contractEngineService = contractEngineService;
    }

    public List<RequirementDecision> evaluateRequirements(
            List<ClubbingService.RequirementDraft> requirements,
            ContractEngineService.CeilingTracker tracker,
            String overridePolicyVersion) {

        // Requirements must consume contract ceiling in order of requirement date!
        List<ClubbingService.RequirementDraft> sortedReqs = new ArrayList<>(requirements);
        sortedReqs.sort(Comparator.comparing((ClubbingService.RequirementDraft r) -> r.getFirstCreatedAt().toLocalDate())
                .thenComparing(ClubbingService.RequirementDraft::getFirstCreatedAt));

        List<RequirementDecision> decisions = new ArrayList<>();

        for (ClubbingService.RequirementDraft draft : sortedReqs) {
            RequirementDecision dec = evaluateSingleRequirement(draft, tracker, overridePolicyVersion);
            decisions.add(dec);
        }

        return decisions;
    }

    public RequirementDecision evaluateSingleRequirement(
            ClubbingService.RequirementDraft draft,
            ContractEngineService.CeilingTracker tracker,
            String overridePolicyVersion) {

        LocalDate reqDate = draft.getFirstCreatedAt().toLocalDate();

        // 1. Resolve Item Master
        String resolvedItemCode = draft.getResolvedItemCode();
        Optional<Item> itemOpt = itemRepository.findById(resolvedItemCode);

        // 2. Handle New Items
        if ("NEW".equalsIgnoreCase(resolvedItemCode) || itemOpt.isEmpty()) {
            BigDecimal estPrice = draft.getPrLines().stream()
                    .map(PrLine::getEstimatedUnitPrice)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal reqValue = estPrice.multiply(draft.getTotalQuantity()).setScale(2, RoundingMode.HALF_UP);
            String polVer = resolvePolicyVersion(reqDate, overridePolicyVersion).getVersionNumber();

            return new RequirementDecision(
                    draft,
                    "SOURCING_REQUEST",
                    "NEW_ITEM_SOURCING",
                    null,
                    estPrice,
                    reqValue,
                    null,
                    null,
                    Collections.emptyList(),
                    polVer,
                    "Clause 1.2: New item not in item master. Sourcing required, valued at requester estimate.",
                    "New items get no supplier suggestions (buyer finds suppliers)",
                    "Valued at requester estimated unit price of ₹" + estPrice,
                    false
            );
        }

        Item item = itemOpt.get();

        // 3. Valuation of Requirement
        ValuationService.ValuationResult valuation = valuationService.valueRequirement(
                resolvedItemCode,
                draft.getTotalQuantity(),
                reqDate,
                null,
                tracker
        );

        // 4. Resolve Active Policy Version & Rules
        PolicyVersion policyVersion = resolvePolicyVersion(reqDate, overridePolicyVersion);
        List<PolicyRule> rules = policyRuleRepository.findByPolicyVersionNumberOrderByPriorityAsc(policyVersion.getVersionNumber());

        // 5. Evaluate Rules in Priority Order
        for (PolicyRule rule : rules) {
            if (matchesRule(rule, item, draft, valuation)) {
                return applyRule(rule, item, draft, valuation, policyVersion, tracker);
            }
        }

        // Fallback if no rule matched
        return new RequirementDecision(
                draft,
                "SOURCING_REQUEST",
                "RFQ",
                null,
                valuation.unitPrice(),
                valuation.totalValue(),
                null,
                null,
                Collections.emptyList(),
                policyVersion.getVersionNumber(),
                "Default RFQ sourcing",
                null,
                valuation.explanation(),
                false
        );
    }

    private boolean matchesRule(
            PolicyRule rule,
            Item item,
            ClubbingService.RequirementDraft draft,
            ValuationService.ValuationResult valuation) {

        try {
            JsonNode cond = objectMapper.readTree(rule.getConditionsJson());

            // 1. Item Type condition (e.g. Capex)
            if (cond.has("itemType")) {
                String expectedType = cond.get("itemType").asText();
                if (!expectedType.equalsIgnoreCase(item.getItemType())) {
                    return false;
                }
            }

            // 2. Category condition (if any)
            if (cond.has("category")) {
                String expectedCat = cond.get("category").asText();
                if (!expectedCat.equalsIgnoreCase(item.getCategory())) {
                    return false;
                }
            }

            // 3. Plant condition (if any)
            if (cond.has("plant")) {
                String expectedPlant = cond.get("plant").asText();
                if (!expectedPlant.equalsIgnoreCase(draft.getPlant())) {
                    return false;
                }
            }

            // 4. Contract eligible & has ceiling (Rule 5.2)
            if (cond.has("contractEligible") && cond.get("contractEligible").asBoolean()) {
                if (valuation.usableContract().isEmpty()) {
                    return false;
                }
            }

            // 5. Urgency condition (e.g. Emergency for Rule 5.3)
            if (cond.has("urgency")) {
                String expectedUrgency = cond.get("urgency").asText();
                if ("Emergency".equalsIgnoreCase(expectedUrgency) && !draft.isEmergency()) {
                    return false;
                }
            }

            // 6. Max Value inclusive
            if (cond.has("maxValue")) {
                BigDecimal maxVal = new BigDecimal(cond.get("maxValue").asText());
                if (valuation.totalValue().compareTo(maxVal) > 0) {
                    return false;
                }
            }

            // 7. Max Value exclusive (< threshold)
            if (cond.has("maxValueExclusive")) {
                BigDecimal maxValExc = new BigDecimal(cond.get("maxValueExclusive").asText());
                if (valuation.totalValue().compareTo(maxValExc) >= 0) {
                    return false;
                }
            }

            // 8. Min Value inclusive (>= threshold)
            if (cond.has("minValue")) {
                BigDecimal minVal = new BigDecimal(cond.get("minValue").asText());
                if (valuation.totalValue().compareTo(minVal) < 0) {
                    return false;
                }
            }

            // 9. Requires past purchase in 365 days from Active supplier
            if (cond.has("requiresPastPurchase365d") && cond.get("requiresPastPurchase365d").asBoolean()) {
                if (!valuation.boughtFromActiveSupplierInLast365Days()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error evaluating rule conditions for rule {}: {}", rule.getRuleCode(), e.getMessage());
            return false;
        }
    }

    private RequirementDecision applyRule(
            PolicyRule rule,
            Item item,
            ClubbingService.RequirementDraft draft,
            ValuationService.ValuationResult valuation,
            PolicyVersion policyVersion,
            ContractEngineService.CeilingTracker tracker) {

        LocalDate reqDate = draft.getFirstCreatedAt().toLocalDate();
        String route = rule.getRoute();
        String outcome = rule.getOutcome();
        String ruleCode = rule.getRuleCode();

        // Branch 1: Contract PO (Rule 5.2)
        if ("CONTRACT_PO".equalsIgnoreCase(route) && valuation.usableContract().isPresent()) {
            ContractEngineService.ContractCandidate cand = valuation.usableContract().get();
            BigDecimal contractRate = cand.rate();
            BigDecimal reqCost = contractRate.multiply(draft.getTotalQuantity()).setScale(2, RoundingMode.HALF_UP);

            // Consume ceiling on this contract!
            tracker.consume(cand.contract().getContractId(), reqCost);

            String reason = String.format("Clause %s: Covered by contract %s with %s (valid until %s, ceiling available)",
                    ruleCode, cand.contract().getContractId(), cand.contract().getSupplierName(), cand.contract().getEndDate());

            return new RequirementDecision(
                    draft,
                    "PO",
                    "CONTRACT_PO",
                    cand.contract().getSupplierId(),
                    contractRate,
                    reqCost,
                    cand.contract().getContractId(),
                    null,
                    Collections.emptyList(),
                    policyVersion.getVersionNumber(),
                    reason,
                    "Direct contract PO",
                    valuation.explanation(),
                    false
            );
        }

        // Branch 2: Emergency PO (Rule 5.3) or Spot PO (Rule 5.4)
        if ("EMERGENCY_PO".equalsIgnoreCase(route) || "SPOT_PO".equalsIgnoreCase(route)) {
            List<SupplierRankingService.RankedSupplier> prefList =
                    supplierRankingService.getPreferredSupplierList(item.getItemCode(), reqDate);

            if (!prefList.isEmpty() && prefList.get(0).referencePrice() != null) {
                SupplierRankingService.RankedSupplier topSupplier = prefList.get(0);
                BigDecimal unitPrice = topSupplier.referencePrice();
                BigDecimal totalValue = unitPrice.multiply(draft.getTotalQuantity()).setScale(2, RoundingMode.HALF_UP);

                String reason = String.format("Clause %s: %s placed with 1st preferred supplier %s (%s) at reference price ₹%s",
                        ruleCode,
                        "EMERGENCY_PO".equalsIgnoreCase(route) ? "Emergency PO (within ₹5L limit)" : "Spot PO (value below ₹2L)",
                        topSupplier.supplier().getName(),
                        topSupplier.supplier().getSupplierId(),
                        unitPrice);

                return new RequirementDecision(
                        draft,
                        "PO",
                        route,
                        topSupplier.supplier().getSupplierId(),
                        unitPrice,
                        totalValue,
                        null,
                        topSupplier.priceSource().equals("PO_HISTORY") ? "PO_REF" : null,
                        Collections.emptyList(),
                        policyVersion.getVersionNumber(),
                        reason,
                        topSupplier.explanation(),
                        valuation.explanation(),
                        false
                );
            }
        }

        // Branch 3: Sourcing Requests (RFQ, RFP, CAPEX_SOURCING)
        int suggestCount = rule.getSupplierSuggestionCount() != null ? rule.getSupplierSuggestionCount() : 0;
        List<Supplier> suggested = supplierRankingService.getSourcingSuggestions(
                item.getItemCode(),
                item.getCategory(),
                reqDate,
                suggestCount
        );

        List<String> suggestedIds = suggested.stream().map(Supplier::getSupplierId).collect(Collectors.toList());

        String reason = switch (route) {
            case "CAPEX_SOURCING" -> String.format("Clause %s: Capex item requires capex sourcing approval. Suggested %d suppliers.", ruleCode, suggestedIds.size());
            case "RFQ" -> String.format("Clause %s: RFQ sourcing (value ₹%s below ₹10L). Suggested %d suppliers.", ruleCode, valuation.totalValue(), suggestedIds.size());
            case "RFP" -> String.format("Clause %s: RFP sourcing (value ₹%s >= ₹10L). Suggested %d suppliers.", ruleCode, valuation.totalValue(), suggestedIds.size());
            default -> String.format("Clause %s: %s sourcing.", ruleCode, route);
        };

        return new RequirementDecision(
                draft,
                "SOURCING_REQUEST",
                route,
                null,
                valuation.unitPrice(),
                valuation.totalValue(),
                null,
                null,
                suggestedIds,
                policyVersion.getVersionNumber(),
                reason,
                "Top suggested suppliers: " + String.join(", ", suggestedIds),
                valuation.explanation(),
                rule.getRequiresCapexApproval() != null && rule.getRequiresCapexApproval()
        );
    }

    public PolicyVersion resolvePolicyVersion(LocalDate reqDate, String overrideVersion) {
        if (overrideVersion != null && !overrideVersion.trim().isEmpty()) {
            Optional<PolicyVersion> v = policyVersionRepository.findByVersionNumber(overrideVersion.trim());
            if (v.isPresent()) return v.get();
        }

        List<PolicyVersion> active = policyVersionRepository.findActivePoliciesApplicableOnDate(reqDate);
        if (!active.isEmpty()) {
            return active.get(0);
        }

        return policyVersionRepository.findByVersionNumber("5.0")
                .orElseGet(() -> new PolicyVersion("5.0", LocalDate.of(2026, 4, 1), "ACTIVE", "Default"));
    }
}
