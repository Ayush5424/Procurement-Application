package com.cantill.procurement.controller;

import com.cantill.procurement.domain.*;
import com.cantill.procurement.repository.*;
import com.cantill.procurement.service.ContractPdfService;
import com.cantill.procurement.service.engine.SupplierRankingService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class BuyerWorkspaceController {

    private final RequirementGroupRepository requirementGroupRepository;
    private final RequirementPrLineRepository requirementPrLineRepository;
    private final PrLineRepository prLineRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final ContractRepository contractRepository;
    private final ContractLineRepository contractLineRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final SupplierRankingService supplierRankingService;
    private final ContractPdfService contractPdfService;

    public record RequirementDetailDto(
            RequirementGroup requirement,
            List<RequirementPrLine> mappedPrLines,
            List<AuditRecord> auditRecords,
            List<SupplierRankingService.RankedSupplier> preferredSuppliers
    ) {}

    public record OverrideRequestDto(
            String actor,
            String overrideReason,
            String newDecision,
            String newRoute,
            String newSupplierId,
            BigDecimal newUnitPrice
    ) {}

    public BuyerWorkspaceController(
            RequirementGroupRepository requirementGroupRepository,
            RequirementPrLineRepository requirementPrLineRepository,
            PrLineRepository prLineRepository,
            AuditRecordRepository auditRecordRepository,
            ContractRepository contractRepository,
            ContractLineRepository contractLineRepository,
            SupplierRepository supplierRepository,
            ItemRepository itemRepository,
            SupplierRankingService supplierRankingService,
            ContractPdfService contractPdfService) {
        this.requirementGroupRepository = requirementGroupRepository;
        this.requirementPrLineRepository = requirementPrLineRepository;
        this.prLineRepository = prLineRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.contractRepository = contractRepository;
        this.contractLineRepository = contractLineRepository;
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
        this.supplierRankingService = supplierRankingService;
        this.contractPdfService = contractPdfService;
    }

    @GetMapping("/requirements")
    public ResponseEntity<List<RequirementGroup>> getRequirements(@RequestParam(value = "status", required = false) String status) {
        if (status != null && !status.trim().isEmpty()) {
            return ResponseEntity.ok(requirementGroupRepository.findByStatusOrderByFirstPrCreatedAtDesc(status.trim()));
        }
        return ResponseEntity.ok(requirementGroupRepository.findAllByOrderByFirstPrCreatedAtDesc());
    }

    @GetMapping("/requirements/{groupId}")
    public ResponseEntity<RequirementDetailDto> getRequirementDetail(@PathVariable("groupId") String groupId) {
        Optional<RequirementGroup> reqOpt = requirementGroupRepository.findById(groupId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RequirementGroup req = reqOpt.get();
        List<RequirementPrLine> lines = requirementPrLineRepository.findByGroupId(groupId);
        List<AuditRecord> audits = auditRecordRepository.findByRequirementIdOrderByTimestampDesc(groupId);
        List<SupplierRankingService.RankedSupplier> ranked = supplierRankingService.getPreferredSupplierList(
                req.getResolvedItemCode(),
                req.getFirstPrCreatedAt().toLocalDate()
        );

        return ResponseEntity.ok(new RequirementDetailDto(req, lines, audits, ranked));
    }

    @PostMapping("/requirements/{groupId}/approve")
    @Transactional
    public ResponseEntity<RequirementGroup> approveRequirement(
            @PathVariable("groupId") String groupId,
            @RequestParam(value = "actor", defaultValue = "Buyer") String actor) {

        Optional<RequirementGroup> reqOpt = requirementGroupRepository.findById(groupId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RequirementGroup req = reqOpt.get();
        String oldStatus = req.getStatus();
        req.setStatus("PO".equalsIgnoreCase(req.getDecision()) ? "PO_CREATED" : "SOURCING_CREATED");
        requirementGroupRepository.save(req);

        AuditRecord audit = new AuditRecord();
        audit.setRequirementId(groupId);
        audit.setOriginalDecision(req.getDecision());
        audit.setOriginalRoute(req.getRoute());
        audit.setOriginalSupplierId(req.getSelectedSupplierId());
        audit.setAction("APPROVED");
        audit.setActor(actor);
        audit.setTimestamp(LocalDateTime.now());
        audit.setOverrideReason("Buyer approved system decision: " + req.getDecision() + " via " + req.getRoute());
        audit.setNewDecision(req.getDecision());
        audit.setNewRoute(req.getRoute());
        audit.setNewSupplierId(req.getSelectedSupplierId());
        auditRecordRepository.save(audit);

        return ResponseEntity.ok(req);
    }

    @PostMapping("/requirements/{groupId}/override")
    @Transactional
    public ResponseEntity<RequirementGroup> overrideRequirement(
            @PathVariable("groupId") String groupId,
            @RequestBody OverrideRequestDto dto) {

        Optional<RequirementGroup> reqOpt = requirementGroupRepository.findById(groupId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RequirementGroup req = reqOpt.get();

        AuditRecord audit = new AuditRecord();
        audit.setRequirementId(groupId);
        audit.setOriginalDecision(req.getDecision());
        audit.setOriginalRoute(req.getRoute());
        audit.setOriginalSupplierId(req.getSelectedSupplierId());
        audit.setAction("OVERRIDDEN");
        audit.setActor(dto.actor() != null ? dto.actor() : "Buyer");
        audit.setTimestamp(LocalDateTime.now());
        audit.setOverrideReason(dto.overrideReason());
        audit.setNewDecision(dto.newDecision());
        audit.setNewRoute(dto.newRoute());
        audit.setNewSupplierId(dto.newSupplierId());
        auditRecordRepository.save(audit);

        req.setDecision(dto.newDecision());
        req.setRoute(dto.newRoute());
        if (dto.newSupplierId() != null) req.setSelectedSupplierId(dto.newSupplierId());
        if (dto.newUnitPrice() != null) {
            req.setUnitPrice(dto.newUnitPrice());
            req.setRequirementValue(dto.newUnitPrice().multiply(req.getTotalQuantity()));
        }
        req.setStatus("OVERRIDDEN");
        req.setReason("Buyer Override: " + dto.overrideReason());
        requirementGroupRepository.save(req);

        return ResponseEntity.ok(req);
    }

    @GetMapping("/audit-trail")
    public ResponseEntity<List<AuditRecord>> getAuditTrail() {
        return ResponseEntity.ok(auditRecordRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/contracts")
    public ResponseEntity<List<Contract>> getContracts() {
        return ResponseEntity.ok(contractRepository.findAll());
    }

    @PostMapping("/contracts/upload-pdf")
    public ResponseEntity<ContractPdfService.ExtractedContractDto> extractContractPdf(
            @RequestParam("file") MultipartFile file) throws Exception {
        ContractPdfService.ExtractedContractDto dto = contractPdfService.extractContractFromPdf(file.getInputStream());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/contracts/activate")
    public ResponseEntity<Contract> activateContract(
            @RequestBody ContractPdfService.ExtractedContractDto dto) {
        Contract contract = contractPdfService.activateContract(dto);
        return ResponseEntity.ok(contract);
    }

    @GetMapping("/preferred-suppliers/export")
    public ResponseEntity<byte[]> exportPreferredSuppliersCsv(
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) throws Exception {

        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<Item> allItems = itemRepository.findAll();
        allItems.sort(Comparator.comparing(Item::getItemCode));

        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("item_code", "rank", "supplier_id", "reference_price")
                .build();

        try (CSVPrinter printer = new CSVPrinter(sw, format)) {
            for (Item item : allItems) {
                List<SupplierRankingService.RankedSupplier> ranked =
                        supplierRankingService.getPreferredSupplierList(item.getItemCode(), asOfDate);

                // Top 3 suppliers for each item
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

        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"preferred_suppliers_" + asOfDate + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
