package com.cantill.procurement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pr_lines", indexes = {
    @Index(name = "idx_pr_lines_pr_no", columnList = "pr_no, pr_line")
})
public class PrLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_no", length = 50, nullable = false)
    private String prNo;

    @Column(name = "pr_line", nullable = false)
    private Integer prLine;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "plant", length = 10, nullable = false)
    private String plant;

    @Column(name = "item_code", length = 50)
    private String itemCode;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "quantity", precision = 15, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "uom", length = 20)
    private String uom;

    @Column(name = "estimated_unit_price", precision = 15, scale = 2)
    private BigDecimal estimatedUnitPrice;

    @Column(name = "need_by_date", nullable = false)
    private LocalDate needByDate;

    @Column(name = "urgency", length = 20, nullable = false)
    private String urgency; // Normal, Emergency

    @Column(name = "status", length = 50, nullable = false)
    private String status = "PENDING";

    public PrLine() {}

    public PrLine(String prNo, Integer prLine, LocalDateTime createdAt, String plant, String itemCode, String description, BigDecimal quantity, String uom, BigDecimal estimatedUnitPrice, LocalDate needByDate, String urgency) {
        this.prNo = prNo;
        this.prLine = prLine;
        this.createdAt = createdAt;
        this.plant = plant;
        this.itemCode = itemCode;
        this.description = description;
        this.quantity = quantity;
        this.uom = uom;
        this.estimatedUnitPrice = estimatedUnitPrice;
        this.needByDate = needByDate;
        this.urgency = urgency;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrNo() { return prNo; }
    public void setPrNo(String prNo) { this.prNo = prNo; }

    public Integer getPrLine() { return prLine; }
    public void setPrLine(Integer prLine) { this.prLine = prLine; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPlant() { return plant; }
    public void setPlant(String plant) { this.plant = plant; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public BigDecimal getEstimatedUnitPrice() { return estimatedUnitPrice; }
    public void setEstimatedUnitPrice(BigDecimal estimatedUnitPrice) { this.estimatedUnitPrice = estimatedUnitPrice; }

    public LocalDate getNeedByDate() { return needByDate; }
    public void setNeedByDate(LocalDate needByDate) { this.needByDate = needByDate; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isEmergency() {
        return "Emergency".equalsIgnoreCase(this.urgency);
    }
}
