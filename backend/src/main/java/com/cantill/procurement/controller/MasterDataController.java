package com.cantill.procurement.controller;

import com.cantill.procurement.service.MasterDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/master-data")
@CrossOrigin
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(masterDataService.getMasterDataStatus());
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadMasterData(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (filename.endsWith(".zip")) {
                Map<String, Integer> counts = masterDataService.importFromZip(file.getInputStream());
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", "Master data archive processed successfully",
                        "counts", counts,
                        "overall", masterDataService.getMasterDataStatus()
                ));
            } else if (filename.endsWith("plants.csv")) {
                int count = masterDataService.importPlants(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "plants", "count", count));
            } else if (filename.endsWith("item_master.csv")) {
                int count = masterDataService.importItems(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "items", "count", count));
            } else if (filename.endsWith("supplier_master.csv")) {
                int count = masterDataService.importSuppliers(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "suppliers", "count", count));
            } else if (filename.endsWith("po_history.csv")) {
                int count = masterDataService.importPoHistory(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "po_history", "count", count));
            } else if (filename.endsWith("contract_register.csv")) {
                int count = masterDataService.importContracts(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "contracts", "count", count));
            } else if (filename.endsWith("contract_lines.csv")) {
                int count = masterDataService.importContractLines(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "type", "contract_lines", "count", count));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unrecognized file format. Please upload a .zip archive or one of: plants.csv, item_master.csv, supplier_master.csv, po_history.csv, contract_register.csv, contract_lines.csv"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process master data: " + e.getMessage()));
        }
    }
}
