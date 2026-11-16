package com.cantill.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contracts")
public class Contract {
    @Id
    @Column(name = "contract_id", length = 50)
    private String contractId;

    @Column(name = "supplier_id", length = 50, nullable = false)
    private String supplierId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "value_cap", precision = 15, scale = 2, nullable = false)
    private BigDecimal valueCap;

    @Column(name = "consumed_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal consumedValue;

    @Column(name = "source_type", length = 20, nullable = false)
    private String sourceType = "REGISTER"; // REGISTER or PDF_UPLOAD

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Contract() {}

    public Contract(String contractId, String supplierId, String supplierName, LocalDate startDate, LocalDate endDate, BigDecimal valueCap, BigDecimal consumedValue, String sourceType, Boolean isActive) {
        this.contractId = contractId;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.valueCap = valueCap;
        this.consumedValue = consumedValue;
        this.sourceType = sourceType != null ? sourceType : "REGISTER";
        this.isActive = isActive != null ? isActive : true;
    }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getValueCap() { return valueCap; }
    public void setValueCap(BigDecimal valueCap) { this.valueCap = valueCap; }

    public BigDecimal getConsumedValue() { return consumedValue; }
    public void setConsumedValue(BigDecimal consumedValue) { this.consumedValue = consumedValue; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public BigDecimal getRemainingCeiling() {
        if (valueCap == null) return BigDecimal.ZERO;
        BigDecimal consumed = consumedValue != null ? consumedValue : BigDecimal.ZERO;
        return valueCap.subtract(consumed);
    }
}
