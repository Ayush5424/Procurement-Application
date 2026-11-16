package com.cantill.procurement.service;

import com.cantill.procurement.domain.PolicyRule;
import com.cantill.procurement.domain.PolicyVersion;
import com.cantill.procurement.repository.PolicyRuleRepository;
import com.cantill.procurement.repository.PolicyVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PolicyManagementService {
    private static final Logger log = LoggerFactory.getLogger(PolicyManagementService.class);

    private final PolicyVersionRepository policyVersionRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record PolicyChangeProposalDto(
            String newVersionNumber,
            LocalDate effectiveDate,
            String title,
            String plainLanguageExplanation,
            List<PolicyRule> proposedRules,
            List<String> detectedModifications,
            String rawText,
            boolean requiresManualReview
    ) {}

    public PolicyManagementService(
            PolicyVersionRepository policyVersionRepository,
            PolicyRuleRepository policyRuleRepository) {
        this.policyVersionRepository = policyVersionRepository;
        this.policyRuleRepository = policyRuleRepository;
    }

    public List<PolicyVersion> getAllPolicyVersions() {
        return policyVersionRepository.findAllByOrderByEffectiveDateDesc();
    }

    public List<PolicyRule> getRulesForVersion(String versionNumber) {
        return policyRuleRepository.findByPolicyVersionNumberOrderByPriorityAsc(versionNumber);
    }

    public PolicyChangeProposalDto parseAmendmentPdf(InputStream pdfInputStream) throws IOException {
        byte[] bytes = pdfInputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            return parseAmendmentText(rawText);
        }
    }

    public PolicyChangeProposalDto parseAmendmentText(String rawText) {
        // Clone current base rules from 5.0
        List<PolicyRule> baseRules = policyRuleRepository.findByPolicyVersionNumberOrderByPriorityAsc("5.0");
        List<PolicyRule> proposedRules = new ArrayList<>();
        for (PolicyRule r : baseRules) {
            proposedRules.add(new PolicyRule(
                    "5.1",
                    r.getPriority(),
                    r.getRuleCode(),
                    r.getName(),
                    r.getDescription(),
                    r.getOutcome(),
                    r.getRoute(),
                    r.getConditionsJson(),
                    r.getSupplierSuggestionCount(),
                    r.getRequiresCapexApproval()
            ));
        }

        String newVersion = "5.1";
        LocalDate effectiveDate = LocalDate.now();
        List<String> modifications = new ArrayList<>();

        // Detect Version (e.g. Version 5.1 or Policy Amendment v5.1)
        Pattern verPat = Pattern.compile("(?:Version|v|Amendment)\\s*([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher verMat = verPat.matcher(rawText);
        if (verMat.find()) {
            newVersion = verMat.group(1).trim();
        }

        // Detect Effective Date (e.g. Effective 19 November 2026 or 2026-11-19)
        Pattern datePat = Pattern.compile("Effective\\s*(?:Date)?[:\\s]*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher dateMat = datePat.matcher(rawText);
        if (dateMat.find()) {
            effectiveDate = LocalDate.parse(dateMat.group(1).trim());
        }

        // Check for Spot PO threshold modifications (e.g. Spot PO limit ₹3,00,000 or 300000 or 3 lakh)
        Pattern spotPat = Pattern.compile("(?:Spot(?:\\s+PO)?|5\\.4).*?(?:limit|threshold|below|under)?\\s*(?:INR|₹)?\\s*([0-9,]+|\\d+\\s*lakh)", Pattern.CASE_INSENSITIVE);
        Matcher spotMat = spotPat.matcher(rawText);
        if (spotMat.find()) {
            String valStr = spotMat.group(1).toLowerCase().replace(",", "").trim();
            BigDecimal newSpotLimit;
            if (valStr.contains("lakh")) {
                int lakh = Integer.parseInt(valStr.replace("lakh", "").trim());
                newSpotLimit = new BigDecimal(lakh * 100000);
            } else {
                newSpotLimit = new BigDecimal(valStr);
            }

            for (PolicyRule rule : proposedRules) {
                if ("5.4".equals(rule.getRuleCode()) || "SPOT_PO".equalsIgnoreCase(rule.getRoute())) {
                    rule.setConditionsJson("{\"maxValueExclusive\": " + newSpotLimit + ", \"requiresPastPurchase365d\": true}");
                    rule.setDescription("Value below ₹" + newSpotLimit + ", and item bought from Active supplier in last 365 days.");
                    modifications.add("Spot PO threshold updated to ₹" + newSpotLimit);
                }
            }
        }

        // Check for Emergency PO threshold modifications
        Pattern emergPat = Pattern.compile("(?:Emergency|5\\.3).*?(?:limit|threshold|below|under|upto)?\\s*(?:INR|₹)?\\s*([0-9,]+|\\d+\\s*lakh)", Pattern.CASE_INSENSITIVE);
        Matcher emergMat = emergPat.matcher(rawText);
        if (emergMat.find()) {
            String valStr = emergMat.group(1).toLowerCase().replace(",", "").trim();
            BigDecimal newEmergLimit;
            if (valStr.contains("lakh")) {
                int lakh = Integer.parseInt(valStr.replace("lakh", "").trim());
                newEmergLimit = new BigDecimal(lakh * 100000);
            } else {
                newEmergLimit = new BigDecimal(valStr);
            }

            for (PolicyRule rule : proposedRules) {
                if ("5.3".equals(rule.getRuleCode()) || "EMERGENCY_PO".equalsIgnoreCase(rule.getRoute())) {
                    rule.setConditionsJson("{\"urgency\": \"Emergency\", \"maxValue\": " + newEmergLimit + ", \"requiresPastPurchase365d\": true}");
                    rule.setDescription("Emergency, value <= ₹" + newEmergLimit + ", and item bought from Active supplier in last 365 days.");
                    modifications.add("Emergency PO limit updated to ₹" + newEmergLimit);
                }
            }
        }

        // Check for Capex supplier suggestions (e.g. Suggest 6 suppliers for Capex)
        Pattern capexSuggestPat = Pattern.compile("(?:Capex|5\\.1).*?(?:suggest|suppliers)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher capexSuggestMat = capexSuggestPat.matcher(rawText);
        if (capexSuggestMat.find()) {
            int count = Integer.parseInt(capexSuggestMat.group(1).trim());
            for (PolicyRule rule : proposedRules) {
                if ("5.1".equals(rule.getRuleCode())) {
                    rule.setSupplierSuggestionCount(count);
                    modifications.add("Capex supplier suggestion count updated to " + count);
                }
            }
        }

        // Plain language summary
        StringBuilder explanation = new StringBuilder("Proposed Policy Amendment **Version ")
                .append(newVersion).append("**:\n\n")
                .append("- **Effective Date:** ").append(effectiveDate).append("\n");

        if (modifications.isEmpty()) {
            explanation.append("- No automated rule changes detected. Please review or adjust rules manually using the editor.");
        } else {
            explanation.append("- **Detected Rule Changes:**\n");
            for (String mod : modifications) {
                explanation.append("  - ").append(mod).append("\n");
            }
        }

        return new PolicyChangeProposalDto(
                newVersion,
                effectiveDate,
                "Policy Amendment v" + newVersion,
                explanation.toString(),
                proposedRules,
                modifications,
                rawText,
                modifications.isEmpty()
        );
    }

    @Transactional
    public PolicyVersion activatePolicyVersion(String versionNumber, LocalDate effectiveDate, String description, List<PolicyRule> rules) {
        // Check if version already exists
        PolicyVersion version = policyVersionRepository.findByVersionNumber(versionNumber)
                .orElseGet(() -> new PolicyVersion(versionNumber, effectiveDate, "ACTIVE", description));

        version.setEffectiveDate(effectiveDate);
        version.setStatus("ACTIVE");
        version.setDescription(description);
        policyVersionRepository.save(version);

        // Delete existing rules for this version if re-activating
        policyRuleRepository.deleteByPolicyVersionNumber(versionNumber);

        for (int i = 0; i < rules.size(); i++) {
            PolicyRule r = rules.get(i);
            r.setPolicyVersionNumber(versionNumber);
            r.setPriority(i + 1);
            policyRuleRepository.save(r);
        }

        log.info("Successfully activated policy version {} with {} rules effective {}", versionNumber, rules.size(), effectiveDate);
        return version;
    }
}
