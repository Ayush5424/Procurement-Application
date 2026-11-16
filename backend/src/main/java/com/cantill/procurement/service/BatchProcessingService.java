package com.cantill.procurement.service;

import com.cantill.procurement.domain.*;
import com.cantill.procurement.repository.PrLineRepository;
import com.cantill.procurement.repository.PurchaseRequisitionRepository;
import com.cantill.procurement.repository.RequirementGroupRepository;
import com.cantill.procurement.repository.RequirementPrLineRepository;
import com.cantill.procurement.service.engine.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class BatchProcessingService {
    private static final Logger log = LoggerFactory.getLogger(BatchProcessingService.class);

    private final ItemMatchingService itemMatchingService;
    private final ClubbingService clubbingService;
    private final ContractEngineService contractEngineService;
    private final ProcurementDecisionEngine decisionEngine;
    private final PoConsolidationService poConsolidationService;

    private final RequirementGroupRepository requirementGroupRepository;
    private final RequirementPrLineRepository requirementPrLineRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final PrLineRepository prLineRepository;

    public record BatchOutputRow(
            String prNo,
            Integer prLine,
            String resolvedItemCode,
            String groupId,
            String poId,
            String decision,
            String route,
            String supplierId,
            BigDecimal unitPrice,
            BigDecimal groupValueInr,
            String suggestedSupplierIds,
            String policyVersion,
            String reason
    ) {}

    public BatchProcessingService(
            ItemMatchingService itemMatchingService,
            ClubbingService clubbingService,
            ContractEngineService contractEngineService,
            ProcurementDecisionEngine decisionEngine,
            PoConsolidationService poConsolidationService,
            RequirementGroupRepository requirementGroupRepository,
            RequirementPrLineRepository requirementPrLineRepository,
            PurchaseRequisitionRepository purchaseRequisitionRepository,
            PrLineRepository prLineRepository) {
        this.itemMatchingService = itemMatchingService;
        this.clubbingService = clubbingService;
        this.contractEngineService = contractEngineService;
        this.decisionEngine = decisionEngine;
        this.poConsolidationService = poConsolidationService;
        this.requirementGroupRepository = requirementGroupRepository;
        this.requirementPrLineRepository = requirementPrLineRepository;
        this.purchaseRequisitionRepository = purchaseRequisitionRepository;
        this.prLineRepository = prLineRepository;
    }

    @Transactional
    public List<BatchOutputRow> processBatch(InputStream csvInputStream, String overridePolicyVersion) throws IOException {
        List<PrLine> parsedLines = parsePrBatchCsv(csvInputStream);
        return processParsedLines(parsedLines, overridePolicyVersion);
    }

    @Transactional
    public List<BatchOutputRow> processParsedLines(List<PrLine> parsedLines, String overridePolicyVersion) {
        log.info("Processing PR batch with {} lines (policy override: {})...", parsedLines.size(), overridePolicyVersion);

        // 1. Resolve item code for each line
        Map<PrLine, String> resolvedItemMap = new HashMap<>();
        for (PrLine line : parsedLines) {
            ItemMatchingService.MatchResult match = itemMatchingService.matchItem(line.getItemCode(), line.getDescription());
            resolvedItemMap.put(line, match.resolvedItemCode());
        }

        // 2. Club PR lines into requirements
        List<ClubbingService.RequirementDraft> drafts = clubbingService.clubLines(parsedLines, resolvedItemMap);
        log.info("Clubbed {} PR lines into {} requirements.", parsedLines.size(), drafts.size());

        // 3. Create clean ceiling tracker
        ContractEngineService.CeilingTracker tracker = contractEngineService.createCeilingTracker();

        // 4. Run decision engine
        List<ProcurementDecisionEngine.RequirementDecision> decisions =
                decisionEngine.evaluateRequirements(drafts, tracker, overridePolicyVersion);

        // Map draft groupId -> Decision
        Map<String, ProcurementDecisionEngine.RequirementDecision> decisionMap = new HashMap<>();
        for (ProcurementDecisionEngine.RequirementDecision d : decisions) {
            decisionMap.put(d.requirementDraft().getGroupId(), d);
        }

        // 5. PO Consolidation
        List<PoConsolidationService.ConsolidatableRequirement> forConsolidation = new ArrayList<>();
        for (ProcurementDecisionEngine.RequirementDecision d : decisions) {
            if ("PO".equalsIgnoreCase(d.decision())) {
                forConsolidation.add(new PoConsolidationService.ConsolidatableRequirement(
                        d.requirementDraft().getGroupId(),
                        d.decision(),
                        d.route(),
                        d.selectedSupplierId(),
                        d.requirementDraft().getPlant(),
                        d.requirementDraft().getFirstCreatedAt()
                ));
            }
        }

        Map<String, String> poConsolidationMap = poConsolidationService.consolidate(forConsolidation);

        // 6. Persist RequirementGroups & PrLines for Buyer Workspace
        persistRequirementsAndLines(decisions, poConsolidationMap);

        // 7. Generate output rows matching original input order
        List<BatchOutputRow> outputRows = new ArrayList<>();
        for (PrLine line : parsedLines) {
            String resolvedItem = resolvedItemMap.get(line);

            // Find which requirement draft this line belongs to
            ClubbingService.RequirementDraft parentDraft = null;
            for (ClubbingService.RequirementDraft draft : drafts) {
                if (draft.getPrLines().contains(line)) {
                    parentDraft = draft;
                    break;
                }
            }

            if (parentDraft != null) {
                ProcurementDecisionEngine.RequirementDecision d = decisionMap.get(parentDraft.getGroupId());
                String poId = poConsolidationMap.get(parentDraft.getGroupId());

                String suggestedStr = (d.suggestedSupplierIds() != null && !d.suggestedSupplierIds().isEmpty())
                        ? String.join(";", d.suggestedSupplierIds())
                        : "";

                outputRows.add(new BatchOutputRow(
                        line.getPrNo(),
                        line.getPrLine(),
                        resolvedItem,
                        parentDraft.getGroupId(),
                        poId != null ? poId : "",
                        d.decision(),
                        d.route(),
                        d.selectedSupplierId() != null ? d.selectedSupplierId() : "",
                        d.unitPrice(),
                        d.requirementValue(),
                        suggestedStr,
                        d.policyVersion(),
                        d.reason()
                ));
            } else {
                outputRows.add(new BatchOutputRow(
                        line.getPrNo(),
                        line.getPrLine(),
                        resolvedItem,
                        "",
                        "",
                        "NOT_PROCESSED",
                        "NOT_PROCESSED",
                        "",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "",
                        overridePolicyVersion != null ? overridePolicyVersion : "5.0",
                        "Failed to associate PR line with requirement group"
                ));
            }
        }

        log.info("Batch processing complete: generated {} output rows.", outputRows.size());
        return outputRows;
    }

    private void persistRequirementsAndLines(
            List<ProcurementDecisionEngine.RequirementDecision> decisions,
            Map<String, String> poMap) {

        for (ProcurementDecisionEngine.RequirementDecision d : decisions) {
            ClubbingService.RequirementDraft draft = d.requirementDraft();
            String poId = poMap.get(draft.getGroupId());

            RequirementGroup group = new RequirementGroup();
            group.setGroupId(draft.getGroupId());
            group.setResolvedItemCode(draft.getResolvedItemCode());
            group.setPlant(draft.getPlant());
            group.setFirstPrCreatedAt(draft.getFirstCreatedAt());
            group.setFirstPrNeedByDate(draft.getFirstNeedByDate());
            group.setTotalQuantity(draft.getTotalQuantity());
            group.setUnitPrice(d.unitPrice());
            group.setRequirementValue(d.requirementValue());
            group.setUrgency(draft.isEmergency() ? "Emergency" : "Normal");
            group.setDecision(d.decision());
            group.setRoute(d.route());
            group.setSelectedSupplierId(d.selectedSupplierId());
            group.setContractIdUsed(d.contractIdUsed());
            group.setHistoricalPoUsed(d.historicalPoUsed());
            group.setSuggestedSupplierIds(d.suggestedSupplierIds() != null ? String.join(";", d.suggestedSupplierIds()) : null);
            group.setPoId(poId);
            group.setPolicyVersion(d.policyVersion());
            group.setReason(d.reason());
            group.setRankingExplanation(d.rankingExplanation());
            group.setValuationExplanation(d.valuationExplanation());
            group.setStatus("PENDING_REVIEW");

            requirementGroupRepository.save(group);

            // Save line mappings
            for (PrLine l : draft.getPrLines()) {
                requirementPrLineRepository.save(new RequirementPrLine(draft.getGroupId(), l.getPrNo(), l.getPrLine()));
            }
        }
    }

    public List<PrLine> parsePrBatchCsv(InputStream inputStream) throws IOException {
        List<PrLine> lines = new ArrayList<>();
        DateTimeFormatter tsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord rec : parser) {
                String prNo = rec.get("pr_no");
                int prLine = Integer.parseInt(rec.get("pr_line"));

                String createdStr = rec.get("created_at");
                LocalDateTime createdAt;
                try {
                    createdAt = LocalDateTime.parse(createdStr, tsFormatter);
                } catch (DateTimeParseException e) {
                    createdAt = LocalDate.parse(createdStr, dateOnlyFormatter).atStartOfDay();
                }

                String plant = rec.get("plant");
                String itemCode = rec.isSet("item_code") ? rec.get("item_code") : null;
                if (itemCode != null && itemCode.trim().isEmpty()) itemCode = null;

                String desc = rec.get("description");
                BigDecimal qty = new BigDecimal(rec.get("quantity"));
                String uom = rec.isSet("uom") ? rec.get("uom") : "";

                BigDecimal estPrice = null;
                if (rec.isSet("estimated_unit_price") && !rec.get("estimated_unit_price").trim().isEmpty()) {
                    estPrice = new BigDecimal(rec.get("estimated_unit_price").trim());
                }

                String needByStr = rec.get("need_by_date");
                LocalDate needBy = LocalDate.parse(needByStr, dateOnlyFormatter);

                String urgency = rec.isSet("urgency") ? rec.get("urgency") : "Normal";

                PrLine line = new PrLine(prNo, prLine, createdAt, plant, itemCode, desc, qty, uom, estPrice, needBy, urgency);
                lines.add(line);
            }
        }

        return lines;
    }

    public String exportOutputCsv(List<BatchOutputRow> rows) throws IOException {
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("pr_no", "pr_line", "resolved_item_code", "group_id", "po_id", "decision", "route", "supplier_id", "unit_price", "group_value_inr", "suggested_supplier_ids", "policy_version", "reason")
                .build();

        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (BatchOutputRow r : rows) {
                printer.printRecord(
                        r.prNo(),
                        r.prLine(),
                        r.resolvedItemCode(),
                        r.groupId(),
                        r.poId(),
                        r.decision(),
                        r.route(),
                        r.supplierId(),
                        r.unitPrice() != null ? r.unitPrice().toPlainString() : "",
                        r.groupValueInr() != null ? r.groupValueInr().toPlainString() : "",
                        r.suggestedSupplierIds(),
                        r.policyVersion(),
                        r.reason()
                );
            }
        }

        return writer.toString();
    }
}
