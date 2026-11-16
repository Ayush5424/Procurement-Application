package com.cantill.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "requirement_groups")
public class RequirementGroup {
    @Id
    @Column(name = "group_id", length = 50)
    private String groupId;

    @Column(name = "resolved_item_code", length = 50, nullable = false)
    private String resolvedItemCode;

    @Column(name = "plant", length = 10, nullable = false)
    private String plant;

    @Column(name = "first_pr_created_at", nullable = false)
    private LocalDateTime firstPrCreatedAt;

    @Column(name = "first_pr_need_by_date", nullable = false)
    private LocalDate firstPrNeedByDate;

    @Column(name = "total_quantity", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalQuantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "requirement_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal requirementValue;

    @Column(name = "urgency", length = 20, nullable = false)
    private String urgency; // Normal, Emergency

    @Column(name = "decision", length = 50, nullable = false)
    private String decision; // PO, SOURCING_REQUEST, NOT_PROCESSED

    @Column(name = "route", length = 50, nullable = false)
    private String route; // CONTRACT_PO, EMERGENCY_PO, SPOT_PO, RFQ, RFP, CAPEX_SOURCING, NEW_ITEM_SOURCING, NOT_PROCESSED

    @Column(name = "selected_supplier_id", length = 50)
    private String selectedSupplierId;

    @Column(name = "contract_id_used", length = 50)
    private String contractIdUsed;

    @Column(name = "historical_po_used", length = 50)
    private String historicalPoUsed;

    @Column(name = "suggested_supplier_ids", length = 500)
    private String suggestedSupplierIds;

    @Column(name = "po_id", length = 50)
    private String poId;

    @Column(name = "policy_version", length = 20, nullable = false)
    private String policyVersion;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "ranking_explanation", columnDefinition = "TEXT")
    private String rankingExplanation;

    @Column(name = "valuation_explanation", columnDefinition = "TEXT")
    private String valuationExplanation;

    @Column(name = "status", length = 50, nullable = false)
    private String status = "PENDING_REVIEW"; // PENDING_REVIEW, APPROVED, OVERRIDDEN, PO_CREATED, SOURCING_CREATED

    public RequirementGroup() {}

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getResolvedItemCode() { return resolvedItemCode; }
    public void setResolvedItemCode(String resolvedItemCode) { this.resolvedItemCode = resolvedItemCode; }

    public String getPlant() { return plant; }
    public void setPlant(String plant) { this.plant = plant; }

    public LocalDateTime getFirstPrCreatedAt() { return firstPrCreatedAt; }
    public void setFirstPrCreatedAt(LocalDateTime firstPrCreatedAt) { this.firstPrCreatedAt = firstPrCreatedAt; }

    public LocalDate getFirstPrNeedByDate() { return firstPrNeedByDate; }
    public void setFirstPrNeedByDate(LocalDate firstPrNeedByDate) { this.firstPrNeedByDate = firstPrNeedByDate; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getRequirementValue() { return requirementValue; }
    public void setRequirementValue(BigDecimal requirementValue) { this.requirementValue = requirementValue; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getSelectedSupplierId() { return selectedSupplierId; }
    public void setSelectedSupplierId(String selectedSupplierId) { this.selectedSupplierId = selectedSupplierId; }

    public String getContractIdUsed() { return contractIdUsed; }
    public void setContractIdUsed(String contractIdUsed) { this.contractIdUsed = contractIdUsed; }

    public String getHistoricalPoUsed() { return historicalPoUsed; }
    public void setHistoricalPoUsed(String historicalPoUsed) { this.historicalPoUsed = historicalPoUsed; }

    public String getSuggestedSupplierIds() { return suggestedSupplierIds; }
    public void setSuggestedSupplierIds(String suggestedSupplierIds) { this.suggestedSupplierIds = suggestedSupplierIds; }

    public String getPoId() { return poId; }
    public void setPoId(String poId) { this.poId = poId; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRankingExplanation() { return rankingExplanation; }
    public void setRankingExplanation(String rankingExplanation) { this.rankingExplanation = rankingExplanation; }

    public String getValuationExplanation() { return valuationExplanation; }
    public void setValuationExplanation(String valuationExplanation) { this.valuationExplanation = valuationExplanation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
