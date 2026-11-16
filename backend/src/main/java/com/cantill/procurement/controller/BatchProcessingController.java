package com.cantill.procurement.controller;

import com.cantill.procurement.service.BatchProcessingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/process-batch")
@CrossOrigin
public class BatchProcessingController {

    private final BatchProcessingService batchProcessingService;

    public BatchProcessingController(BatchProcessingService batchProcessingService) {
        this.batchProcessingService = batchProcessingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<BatchProcessingService.BatchOutputRow>> processBatchMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "policyVersion", required = false) String policyVersion) throws Exception {

        try (InputStream is = file.getInputStream()) {
            List<BatchProcessingService.BatchOutputRow> results = batchProcessingService.processBatch(is, policyVersion);
            return ResponseEntity.ok(results);
        }
    }

    @PostMapping(consumes = "text/csv")
    public ResponseEntity<List<BatchProcessingService.BatchOutputRow>> processBatchRawCsv(
            @RequestBody String rawCsv,
            @RequestParam(value = "policyVersion", required = false) String policyVersion) throws Exception {

        try (InputStream is = new ByteArrayInputStream(rawCsv.getBytes(StandardCharsets.UTF_8))) {
            List<BatchProcessingService.BatchOutputRow> results = batchProcessingService.processBatch(is, policyVersion);
            return ResponseEntity.ok(results);
        }
    }

    @PostMapping(value = "/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processBatchAndExportCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "policyVersion", required = false) String policyVersion) throws Exception {

        try (InputStream is = file.getInputStream()) {
            List<BatchProcessingService.BatchOutputRow> results = batchProcessingService.processBatch(is, policyVersion);
            String csvContent = batchProcessingService.exportOutputCsv(results);

            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"decisions.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csvBytes);
        }
    }
}
