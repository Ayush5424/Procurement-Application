package com.cantill.procurement.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "policy_rules")
public class PolicyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_version_number", length = 50, nullable = false)
    private String policyVersionNumber;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "rule_code", length = 50, nullable = false)
    private String ruleCode; // e.g. "5.1", "5.2", "5.3", "5.4", "5.5", "5.6"

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "outcome", length = 50, nullable = false)
    private String outcome; // PO, SOURCING_REQUEST

    @Column(name = "route", length = 50, nullable = false)
    private String route; // CAPEX_SOURCING, CONTRACT_PO, EMERGENCY_PO, SPOT_PO, RFQ, RFP, NEW_ITEM_SOURCING

    @Column(name = "conditions_json", columnDefinition = "TEXT", nullable = false)
    private String conditionsJson;

    @Column(name = "supplier_suggestion_count")
    private Integer supplierSuggestionCount = 0;

    @Column(name = "requires_capex_approval")
    private Boolean requiresCapexApproval = false;

    public PolicyRule() {}

    public PolicyRule(String policyVersionNumber, Integer priority, String ruleCode, String name, String description, String outcome, String route, String conditionsJson, Integer supplierSuggestionCount, Boolean requiresCapexApproval) {
        this.policyVersionNumber = policyVersionNumber;
        this.priority = priority;
        this.ruleCode = ruleCode;
        this.name = name;
        this.description = description;
        this.outcome = outcome;
        this.route = route;
        this.conditionsJson = conditionsJson;
        this.supplierSuggestionCount = supplierSuggestionCount;
        this.requiresCapexApproval = requiresCapexApproval;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPolicyVersionNumber() { return policyVersionNumber; }
    public void setPolicyVersionNumber(String policyVersionNumber) { this.policyVersionNumber = policyVersionNumber; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }

    public Integer getSupplierSuggestionCount() { return supplierSuggestionCount; }
    public void setSupplierSuggestionCount(Integer supplierSuggestionCount) { this.supplierSuggestionCount = supplierSuggestionCount; }

    public Boolean getRequiresCapexApproval() { return requiresCapexApproval; }
    public void setRequiresCapexApproval(Boolean requiresCapexApproval) { this.requiresCapexApproval = requiresCapexApproval; }
}
