package com.cantill.procurement;

import com.cantill.procurement.domain.PolicyRule;
import com.cantill.procurement.domain.PolicyVersion;
import com.cantill.procurement.domain.PrLine;
import com.cantill.procurement.service.MasterDataService;
import com.cantill.procurement.service.PolicyManagementService;
import com.cantill.procurement.service.engine.ClubbingService;
import com.cantill.procurement.service.engine.ContractEngineService;
import com.cantill.procurement.service.engine.ProcurementDecisionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PolicyAmendmentTest {

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private PolicyManagementService policyManagementService;

    @Autowired
    private ProcurementDecisionEngine decisionEngine;

    @Autowired
    private ContractEngineService contractEngineService;

    @BeforeEach
    void setUp() {
        masterDataService.autoImportIfDataAvailable();
    }

    @Test
    void testPolicyAmendmentChangesDecisionWithoutJavaCodeChange() {
        // Line with value ₹2,60,829 (450 * 579.62) for ITM-1022 (Ball bearing 6308-ZZ) where contract ceiling is nearly exhausted,
        // but item was bought in last 365 days.
        // Under Policy 5.0 (Spot PO limit < ₹2,00,000), ₹2,60,829 cannot be Spot PO -> RFQ!
        // Under Policy 5.1 (Spot PO limit < ₹3,00,000), ₹2,60,829 becomes SPOT_PO!
        LocalDateTime reqTime = LocalDateTime.of(2026, 11, 2, 15, 15);
        PrLine line = new PrLine("PR-TEST-01", 1, reqTime, "CHK1", "ITM-1022", "Ball bearing 6308-ZZ",
                new BigDecimal(450), "NOS", null, LocalDate.of(2026, 11, 26), "Normal");

        ClubbingService.RequirementDraft draft = new ClubbingService.RequirementDraft("REQ-TEST-01", line, "ITM-1022");

        // 1. Evaluate under Policy v5.0
        ContractEngineService.CeilingTracker trackerV5 = contractEngineService.createCeilingTracker();
        ProcurementDecisionEngine.RequirementDecision decV5 = decisionEngine.evaluateSingleRequirement(draft, trackerV5, "5.0");

        assertEquals("SOURCING_REQUEST", decV5.decision(), "Under 5.0, value >= 2L should go to sourcing");
        assertEquals("RFQ", decV5.route(), "Under 5.0, route should be RFQ");

        // 2. Parse and activate amendment v5.1: Spot PO threshold increased to ₹3,00,000
        String amendmentText = """
                Corvo Engineering Pvt Ltd
                Policy Amendment v5.1
                Effective Date: 2026-11-01
                Amendment 1: Spot PO limit increased to INR 3,00,000 for standard items bought in last 365 days.
                """;

        PolicyManagementService.PolicyChangeProposalDto proposal = policyManagementService.parseAmendmentText(amendmentText);
        assertEquals("5.1", proposal.newVersionNumber());
        assertFalse(proposal.detectedModifications().isEmpty());

        PolicyVersion activatedV51 = policyManagementService.activatePolicyVersion(
                proposal.newVersionNumber(),
                proposal.effectiveDate(),
                "Amended Policy with ₹3L Spot limit",
                proposal.proposedRules()
        );
        assertNotNull(activatedV51);

        // 3. Evaluate the exact same requirement under Policy v5.1
        ContractEngineService.CeilingTracker trackerV51 = contractEngineService.createCeilingTracker();
        ProcurementDecisionEngine.RequirementDecision decV51 = decisionEngine.evaluateSingleRequirement(draft, trackerV51, "5.1");

        assertEquals("PO", decV51.decision(), "Under 5.1 with ₹3L limit, it should be direct PO!");
        assertEquals("SPOT_PO", decV51.route(), "Under 5.1, route should be SPOT_PO without any Java code change!");
        assertNotNull(decV51.selectedSupplierId(), "Supplier must be selected for SPOT_PO");
    }
}
