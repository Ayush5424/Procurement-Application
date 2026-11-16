package com.cantill.procurement;

import com.cantill.procurement.domain.Item;
import com.cantill.procurement.domain.PrLine;
import com.cantill.procurement.service.BatchProcessingService;
import com.cantill.procurement.service.ContractPdfService;
import com.cantill.procurement.service.MasterDataService;
import com.cantill.procurement.service.engine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DecisionEngineWorkedExamplesTest {

    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private ItemMatchingService itemMatchingService;

    @Autowired
    private ClubbingService clubbingService;

    @Autowired
    private ContractEngineService contractEngineService;

    @Autowired
    private ValuationService valuationService;

    @Autowired
    private SupplierRankingService supplierRankingService;

    @Autowired
    private ProcurementDecisionEngine decisionEngine;

    @Autowired
    private ContractPdfService contractPdfService;

    @Autowired
    private BatchProcessingService batchProcessingService;

    @BeforeEach
    void setUp() throws Exception {
        masterDataService.autoImportIfDataAvailable();

        // Also activate the two PDF-only contracts if not already present
        File pdf1 = new File("../task_kit/Cantill_FullStack_Task_Kit/contracts/pdf/RC-2026-031.pdf");
        if (!pdf1.exists()) {
            pdf1 = new File("./task_kit/Cantill_FullStack_Task_Kit/contracts/pdf/RC-2026-031.pdf");
        }
        if (pdf1.exists()) {
            ContractPdfService.ExtractedContractDto dto = contractPdfService.extractContractFromPdfFile(pdf1);
            contractPdfService.activateContract(dto);
        }

        File pdf2 = new File("../task_kit/Cantill_FullStack_Task_Kit/contracts/pdf/RC-2026-033.pdf");
        if (!pdf2.exists()) {
            pdf2 = new File("./task_kit/Cantill_FullStack_Task_Kit/contracts/pdf/RC-2026-033.pdf");
        }
        if (pdf2.exists()) {
            ContractPdfService.ExtractedContractDto dto = contractPdfService.extractContractFromPdfFile(pdf2);
            contractPdfService.activateContract(dto);
        }
    }

    @Test
    void testPreferredSupplierRankingsWorkedExamples() {
        LocalDate asOfDate = LocalDate.of(2026, 11, 1);

        // Example 1: ITM-1048 Nitrile coated gloves
        List<SupplierRankingService.RankedSupplier> nitrileRanked =
                supplierRankingService.getPreferredSupplierList("ITM-1048", asOfDate);
        assertFalse(nitrileRanked.isEmpty(), "Nitrile gloves should have preferred suppliers");
        assertEquals("S029", nitrileRanked.get(0).supplier().getSupplierId(), "Rank 1 should be S029 (SafeGuard Industrial Supplies)");
        assertEquals("S030", nitrileRanked.get(1).supplier().getSupplierId(), "Rank 2 should be S030 (Karam Safety Distributor)");
        assertTrue(nitrileRanked.stream().noneMatch(s -> "S031".equals(s.supplier().getSupplierId())), "S031 (Inactive) must be excluded");

        // Example 2: ITM-1036 Pneumatic cylinder 50x100
        List<SupplierRankingService.RankedSupplier> cylinderRanked =
                supplierRankingService.getPreferredSupplierList("ITM-1036", asOfDate);
        assertEquals(3, cylinderRanked.size(), "Should have 3 eligible preferred suppliers");
        assertEquals("S023", cylinderRanked.get(0).supplier().getSupplierId(), "Rank 1 should be S023 (Hydrax Hydraulics)");
        assertEquals("S022", cylinderRanked.get(1).supplier().getSupplierId(), "Rank 2 should be S022 (Fluidline Hydraulics)");
        assertEquals("S024", cylinderRanked.get(2).supplier().getSupplierId(), "Rank 3 should be S024 (Janatics Dealer Chakan)");

        // Example 3: ITM-1016 MIG wire ER70S-6 1.2mm
        List<SupplierRankingService.RankedSupplier> wireRanked =
                supplierRankingService.getPreferredSupplierList("ITM-1016", asOfDate);
        assertEquals(2, wireRanked.size(), "Should have 2 eligible preferred suppliers");
        assertEquals("S009", wireRanked.get(0).supplier().getSupplierId(), "Rank 1 should be S009 (Weldtech Supplies)");
        assertEquals("S010", wireRanked.get(1).supplier().getSupplierId(), "Rank 2 should be S010 (Inox Gases Dealer Pune)");
    }

    @Test
    void testSampleBatchProcessing() throws Exception {
        File batchFile = new File("../task_kit/Cantill_FullStack_Task_Kit/pr_batches/sample_pr_batch.csv");
        if (!batchFile.exists()) {
            batchFile = new File("./task_kit/Cantill_FullStack_Task_Kit/pr_batches/sample_pr_batch.csv");
        }
        assertTrue(batchFile.exists(), "Sample PR batch file should exist");

        List<BatchProcessingService.BatchOutputRow> rows;
        try (FileInputStream fis = new FileInputStream(batchFile)) {
            rows = batchProcessingService.processBatch(fis, "5.0");
        }

        assertEquals(186, rows.size(), "Sample PR batch must yield exactly 186 output rows (one per line)");

        // Check Example 1: PR-26-00019 line 1 -> CONTRACT_PO, S001
        BatchProcessingService.BatchOutputRow ex1 = rows.stream()
                .filter(r -> "PR-26-00019".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("CONTRACT_PO", ex1.route());
        assertEquals("PO", ex1.decision());
        assertEquals("S001", ex1.supplierId());

        // Check Example 2: PR-26-00045 line 1 -> CONTRACT_PO, S021 (PDF contract RC-2026-031)
        BatchProcessingService.BatchOutputRow ex2 = rows.stream()
                .filter(r -> "PR-26-00045".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("CONTRACT_PO", ex2.route());
        assertEquals("PO", ex2.decision());
        assertEquals("S021", ex2.supplierId());

        // Check Example 7: PR-26-00088 line 1 (hyd hose 1/2 inch 2 wire) -> ITM-1035, SPOT_PO, S023
        BatchProcessingService.BatchOutputRow ex7 = rows.stream()
                .filter(r -> "PR-26-00088".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("ITM-1035", ex7.resolvedItemCode());
        assertEquals("SPOT_PO", ex7.route());
        assertEquals("S023", ex7.supplierId());

        // Check Example 8: PR-26-00059 line 1 (Emergency) -> EMERGENCY_PO, S014
        BatchProcessingService.BatchOutputRow ex8 = rows.stream()
                .filter(r -> "PR-26-00059".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("EMERGENCY_PO", ex8.route());
        assertEquals("S014", ex8.supplierId());

        // Check Example 12: PR-26-00047 line 1 (Capex) -> CAPEX_SOURCING
        BatchProcessingService.BatchOutputRow ex12 = rows.stream()
                .filter(r -> "PR-26-00047".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("CAPEX_SOURCING", ex12.route());
        assertEquals("SOURCING_REQUEST", ex12.decision());

        // Check Example 13: PR-26-00170 line 1 (Drinking water purifier) -> NEW_ITEM_SOURCING, NEW
        BatchProcessingService.BatchOutputRow ex13 = rows.stream()
                .filter(r -> "PR-26-00170".equals(r.prNo()) && r.prLine() == 1)
                .findFirst().orElseThrow();
        assertEquals("NEW", ex13.resolvedItemCode());
        assertEquals("NEW_ITEM_SOURCING", ex13.route());
        assertEquals("SOURCING_REQUEST", ex13.decision());
        assertTrue(ex13.suggestedSupplierIds().isEmpty(), "New items must receive no supplier suggestions");
    }
}
