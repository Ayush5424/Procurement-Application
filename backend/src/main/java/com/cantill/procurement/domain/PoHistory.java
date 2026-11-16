package com.cantill.procurement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "po_history", indexes = {
    @Index(name = "idx_po_history_item_date", columnList = "item_code, po_date"),
    @Index(name = "idx_po_history_supplier", columnList = "supplier_id")
})
public class PoHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", length = 50, nullable = false)
    private String poNumber;

    @Column(name = "po_date", nullable = false)
    private LocalDate poDate;

    @Column(name = "supplier_id", length = 50, nullable = false)
    private String supplierId;

    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    @Column(name = "quantity", precision = 15, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "uom", length = 20, nullable = false)
    private String uom;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "plant", length = 10, nullable = false)
    private String plant;

    @Column(name = "delivered_on_time", nullable = false)
    private Boolean deliveredOnTime;

    public PoHistory() {}

    public PoHistory(String poNumber, LocalDate poDate, String supplierId, String itemCode, BigDecimal quantity, String uom, BigDecimal unitPrice, String plant, Boolean deliveredOnTime) {
        this.poNumber = poNumber;
        this.poDate = poDate;
        this.supplierId = supplierId;
        this.itemCode = itemCode;
        this.quantity = quantity;
        this.uom = uom;
        this.unitPrice = unitPrice;
        this.plant = plant;
        this.deliveredOnTime = deliveredOnTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public String getPlant() { return plant; }
    public void setPlant(String plant) { this.plant = plant; }

    public Boolean getDeliveredOnTime() { return deliveredOnTime; }
    public void setDeliveredOnTime(Boolean deliveredOnTime) { this.deliveredOnTime = deliveredOnTime; }
}
