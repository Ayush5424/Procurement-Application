package com.cantill.procurement.controller;

import com.cantill.procurement.domain.PolicyRule;
import com.cantill.procurement.domain.PolicyVersion;
import com.cantill.procurement.service.PolicyManagementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@CrossOrigin
public class PolicyManagementController {

    private final PolicyManagementService policyManagementService;

    public record ActivatePolicyRequest(
            String versionNumber,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            String description,
            List<PolicyRule> rules
    ) {}

    public PolicyManagementController(PolicyManagementService policyManagementService) {
        this.policyManagementService = policyManagementService;
    }

    @GetMapping
    public ResponseEntity<List<PolicyVersion>> getPolicies() {
        return ResponseEntity.ok(policyManagementService.getAllPolicyVersions());
    }

    @GetMapping("/{versionNumber}")
    public ResponseEntity<Map<String, Object>> getPolicyDetails(@PathVariable("versionNumber") String versionNumber) {
        List<PolicyRule> rules = policyManagementService.getRulesForVersion(versionNumber);
        return ResponseEntity.ok(Map.of(
                "versionNumber", versionNumber,
                "rules", rules
        ));
    }

    @PostMapping("/upload-amendment")
    public ResponseEntity<PolicyManagementService.PolicyChangeProposalDto> uploadAmendmentPdf(
            @RequestParam("file") MultipartFile file) throws Exception {
        PolicyManagementService.PolicyChangeProposalDto proposal =
                policyManagementService.parseAmendmentPdf(file.getInputStream());
        return ResponseEntity.ok(proposal);
    }

    @PostMapping("/parse-text")
    public ResponseEntity<PolicyManagementService.PolicyChangeProposalDto> parseAmendmentText(
            @RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        PolicyManagementService.PolicyChangeProposalDto proposal =
                policyManagementService.parseAmendmentText(text);
        return ResponseEntity.ok(proposal);
    }

    @PostMapping("/activate")
    public ResponseEntity<PolicyVersion> activatePolicy(@RequestBody ActivatePolicyRequest req) {
        PolicyVersion activated = policyManagementService.activatePolicyVersion(
                req.versionNumber(),
                req.effectiveDate(),
                req.description(),
                req.rules()
        );
        return ResponseEntity.ok(activated);
    }
}
