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

    @Autowired
    private com.cantill.procurement.repository.ItemRepository itemRepository;

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
    void testAll14WorkedExamplesInSampleBatch() throws Exception {
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

        // Worked Example 1: Contract PO (PR-26-00019 line 1)
        BatchProcessingService.BatchOutputRow ex1 = findRow(rows, "PR-26-00019", 1);
        assertEquals("CONTRACT_PO", ex1.route(), "Ex 1 Route");
        assertEquals("PO", ex1.decision(), "Ex 1 Decision");
        assertEquals("S001", ex1.supplierId(), "Ex 1 Supplier");
        assertEquals(9169, Math.round(ex1.unitPrice().doubleValue()), "Ex 1 Unit Price rounded");
        assertEquals(137531, Math.round(ex1.groupValueInr().doubleValue()), "Ex 1 Value ~137,531");

        // Worked Example 2: Contract that exists only as a PDF (PR-26-00045 line 1)
        BatchProcessingService.BatchOutputRow ex2 = findRow(rows, "PR-26-00045", 1);
        assertEquals("CONTRACT_PO", ex2.route(), "Ex 2 Route");
        assertEquals("PO", ex2.decision(), "Ex 2 Decision");
        assertEquals("S021", ex2.supplierId(), "Ex 2 Supplier");
        assertEquals(2439, Math.round(ex2.unitPrice().doubleValue()), "Ex 2 Unit Price");
        assertEquals(26829, Math.round(ex2.groupValueInr().doubleValue()), "Ex 2 Value");

        // Worked Example 3: Contract ceiling nearly used up (PR-26-00004/1 and PR-26-00026/1 clubbed)
        BatchProcessingService.BatchOutputRow ex3a = findRow(rows, "PR-26-00004", 1);
        BatchProcessingService.BatchOutputRow ex3b = findRow(rows, "PR-26-00026", 1);
        assertEquals(ex3a.groupId(), ex3b.groupId(), "Ex 3 lines must be clubbed");
        assertEquals("RFQ", ex3a.route(), "Ex 3 Route");
        assertEquals("SOURCING_REQUEST", ex3a.decision(), "Ex 3 Decision");
        assertTrue(ex3a.suggestedSupplierIds().contains("S014") && ex3a.suggestedSupplierIds().contains("S013"), "Ex 3 Suppliers S014, S013, S016");

        // Worked Example 4: Two contracts for same item (PR-26-00034/1 and PR-26-00032/1)
        BatchProcessingService.BatchOutputRow ex4a = findRow(rows, "PR-26-00034", 1);
        assertEquals("CONTRACT_PO", ex4a.route(), "Ex 4 Route");
        assertEquals("PO", ex4a.decision(), "Ex 4 Decision");
        assertEquals("S007", ex4a.supplierId(), "Ex 4 Supplier S007 (Precision Fasteners)");
        assertEquals(7, Math.round(ex4a.unitPrice().doubleValue()), "Ex 4 Unit Price ₹7");

        // Worked Example 5: Split PRs clubbed into one requirement (PR-26-00146, PR-26-00144, PR-26-00145)
        BatchProcessingService.BatchOutputRow ex5a = findRow(rows, "PR-26-00146", 1);
        BatchProcessingService.BatchOutputRow ex5b = findRow(rows, "PR-26-00144", 1);
        BatchProcessingService.BatchOutputRow ex5c = findRow(rows, "PR-26-00145", 1);
        assertEquals(ex5a.groupId(), ex5b.groupId(), "Ex 5 lines clubbed");
        assertEquals(ex5a.groupId(), ex5c.groupId(), "Ex 5 lines clubbed");
        assertEquals("SPOT_PO", ex5a.route(), "Ex 5 Route");
        assertEquals("S024", ex5a.supplierId(), "Ex 5 Supplier");
        assertEquals(166, Math.round(ex5a.unitPrice().doubleValue()), "Ex 5 Unit Price");
        assertEquals(126472, Math.round(ex5a.groupValueInr().doubleValue()), "Ex 5 Total Value ~126,472");

        // Worked Example 6: Same item, but not clubbed (PR-26-00106 PUN1 vs PR-26-00103 CHK1)
        BatchProcessingService.BatchOutputRow ex6a = findRow(rows, "PR-26-00106", 1);
        BatchProcessingService.BatchOutputRow ex6b = findRow(rows, "PR-26-00103", 1);
        assertNotEquals(ex6a.groupId(), ex6b.groupId(), "Ex 6 lines must not be clubbed across plants");
        assertEquals("SPOT_PO", ex6a.route(), "Ex 6a Route");
        assertEquals("S027", ex6a.supplierId(), "Ex 6a Supplier S027");

        // Worked Example 7: Free-text item matched (PR-26-00088/1 -> ITM-1035)
        BatchProcessingService.BatchOutputRow ex7 = findRow(rows, "PR-26-00088", 1);
        assertEquals("ITM-1035", ex7.resolvedItemCode(), "Ex 7 Resolved Item");
        assertEquals("SPOT_PO", ex7.route(), "Ex 7 Route");
        assertEquals("S023", ex7.supplierId(), "Ex 7 Supplier");
        assertEquals(392, Math.round(ex7.unitPrice().doubleValue()), "Ex 7 Unit Price");

        // Worked Example 8: Emergency PO (PR-26-00059/1)
        BatchProcessingService.BatchOutputRow ex8 = findRow(rows, "PR-26-00059", 1);
        assertEquals("EMERGENCY_PO", ex8.route(), "Ex 8 Route");
        assertEquals("PO", ex8.decision(), "Ex 8 Decision");
        assertEquals("S014", ex8.supplierId(), "Ex 8 Supplier");
        assertEquals(106, Math.round(ex8.unitPrice().doubleValue()), "Ex 8 Unit Price");

        // Worked Example 9: RFQ above the PO limit (PR-26-00089/1 -> CMM calibration service)
        BatchProcessingService.BatchOutputRow ex9 = findRow(rows, "PR-26-00089", 1);
        assertEquals("RFQ", ex9.route(), "Ex 9 Route");
        assertEquals("SOURCING_REQUEST", ex9.decision(), "Ex 9 Decision");
        assertTrue(ex9.suggestedSupplierIds().contains("S043"), "Ex 9 Sourcing Suggestions");

        // Worked Example 10: RFQ small but never bought (PR-26-00050/1)
        BatchProcessingService.BatchOutputRow ex10 = findRow(rows, "PR-26-00050", 1);
        assertEquals("RFQ", ex10.route(), "Ex 10 Route");
        assertEquals("SOURCING_REQUEST", ex10.decision(), "Ex 10 Decision");
        assertTrue(ex10.suggestedSupplierIds().contains("S003") || ex10.suggestedSupplierIds().contains("S002"), "Ex 10 Suggestions");

        // Worked Example 11: RFP (PR-26-00100/1 -> Encoder for CNC spindle)
        BatchProcessingService.BatchOutputRow ex11 = findRow(rows, "PR-26-00100", 1);
        assertEquals("RFP", ex11.route(), "Ex 11 Route");
        assertEquals("SOURCING_REQUEST", ex11.decision(), "Ex 11 Decision");
        assertFalse(ex11.suggestedSupplierIds().isEmpty(), "Ex 11 RFP Suggestions");

        // Worked Example 12: Capex (PR-26-00047/1 -> eot crane 5 ton)
        BatchProcessingService.BatchOutputRow ex12 = findRow(rows, "PR-26-00047", 1);
        assertEquals("CAPEX_SOURCING", ex12.route(), "Ex 12 Route");
        assertEquals("SOURCING_REQUEST", ex12.decision(), "Ex 12 Decision");
        assertTrue(ex12.suggestedSupplierIds().split(";").length >= 5, "Ex 12 Capex Sourcing 5 suppliers");

        // Worked Example 13: New item (PR-26-00170/1 -> Drinking water purifier)
        BatchProcessingService.BatchOutputRow ex13 = findRow(rows, "PR-26-00170", 1);
        assertEquals("NEW", ex13.resolvedItemCode(), "Ex 13 Resolved Item");
        assertEquals("NEW_ITEM_SOURCING", ex13.route(), "Ex 13 Route");
        assertEquals("SOURCING_REQUEST", ex13.decision(), "Ex 13 Decision");
        assertTrue(ex13.suggestedSupplierIds() == null || ex13.suggestedSupplierIds().isEmpty(), "New items get no suggestions");

        // Worked Example 14: Several requirements on one PO (PR-26-00031, PR-26-00035, PR-26-00034, PR-26-00032)
        BatchProcessingService.BatchOutputRow ex14a = findRow(rows, "PR-26-00031", 1);
        BatchProcessingService.BatchOutputRow ex14b = findRow(rows, "PR-26-00035", 1);
        assertNotNull(ex14a.poId(), "Ex 14a PO ID should not be null");
        assertNotNull(ex14b.poId(), "Ex 14b PO ID should not be null");
        assertEquals("S007", ex14a.supplierId(), "Ex 14a Supplier S007");
    }

    @Test
    void testBlindTestCsvOutputs() throws Exception {
        File batchFile = new File("../task_kit/Cantill_FullStack_Task_Kit/pr_batches/sample_pr_batch.csv");
        if (!batchFile.exists()) {
            batchFile = new File("./task_kit/Cantill_FullStack_Task_Kit/pr_batches/sample_pr_batch.csv");
        }
        assertTrue(batchFile.exists(), "Sample PR batch file should exist");

        // 1. Run 1: Policy 5.0
        List<BatchProcessingService.BatchOutputRow> rows1;
        try (FileInputStream fis = new FileInputStream(batchFile)) {
            rows1 = batchProcessingService.processBatch(fis, "5.0");
        }
        String csvRun1 = batchProcessingService.exportOutputCsv(rows1);
        assertNotNull(csvRun1);
        String[] lines1 = csvRun1.split("\r?\n");
        assertEquals(187, lines1.length, "Run 1 must have 1 header line + 186 data lines");
        assertEquals("pr_no,pr_line,resolved_item_code,group_id,po_id,decision,route,supplier_id,unit_price,group_value_inr,suggested_supplier_ids,policy_version,reason", lines1[0].trim());

        // 2. Run 2: Policy 5.1
        List<BatchProcessingService.BatchOutputRow> rows2;
        try (FileInputStream fis = new FileInputStream(batchFile)) {
            rows2 = batchProcessingService.processBatch(fis, "5.1");
        }
        String csvRun2 = batchProcessingService.exportOutputCsv(rows2);
        assertNotNull(csvRun2);
        String[] lines2 = csvRun2.split("\r?\n");
        assertEquals(187, lines2.length, "Run 2 must have 1 header line + 186 data lines");
        assertEquals("pr_no,pr_line,resolved_item_code,group_id,po_id,decision,route,supplier_id,unit_price,group_value_inr,suggested_supplier_ids,policy_version,reason", lines2[0].trim());

        // 3. Preferred Suppliers CSV as of 2026-11-01
        LocalDate asOfDate = LocalDate.of(2026, 11, 1);
        List<Item> allItems = itemRepository.findAll();
        allItems.sort(java.util.Comparator.comparing(Item::getItemCode));

        java.io.StringWriter sw = new java.io.StringWriter();
        org.apache.commons.csv.CSVFormat format = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                .setHeader("item_code", "rank", "supplier_id", "reference_price")
                .build();

        try (org.apache.commons.csv.CSVPrinter printer = new org.apache.commons.csv.CSVPrinter(sw, format)) {
            for (Item item : allItems) {
                List<SupplierRankingService.RankedSupplier> ranked =
                        supplierRankingService.getPreferredSupplierList(item.getItemCode(), asOfDate);
                int limit = Math.min(3, ranked.size());
                for (int i = 0; i < limit; i++) {
                    SupplierRankingService.RankedSupplier s = ranked.get(i);
                    printer.printRecord(
                            item.getItemCode(),
                            s.rank(),
                            s.supplier().getSupplierId(),
                            s.referencePrice() != null ? s.referencePrice().toPlainString() : ""
                    );
                }
            }
        }
        String preferredCsv = sw.toString();
        assertNotNull(preferredCsv);
        String[] prefLines = preferredCsv.split("\r?\n");
        assertTrue(prefLines.length > 50, "Preferred supplier export must have records for active items");
        assertEquals("item_code,rank,supplier_id,reference_price", prefLines[0].trim());
    }

    private BatchProcessingService.BatchOutputRow findRow(List<BatchProcessingService.BatchOutputRow> rows, String prNo, int line) {
        return rows.stream()
                .filter(r -> prNo.equals(r.prNo()) && r.prLine() == line)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find row for " + prNo + "/" + line));
    }
}
