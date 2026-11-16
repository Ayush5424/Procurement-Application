package com.cantill.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "items")
public class Item {
    @Id
    @Column(name = "item_code", length = 50)
    private String itemCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "uom", length = 20, nullable = false)
    private String uom;

    @Column(name = "category", length = 100, nullable = false)
    private String category;

    @Column(name = "item_type", length = 50, nullable = false)
    private String itemType;

    @Column(name = "standard_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal standardPrice;

    public Item() {}

    public Item(String itemCode, String description, String uom, String category, String itemType, BigDecimal standardPrice) {
        this.itemCode = itemCode;
        this.description = description;
        this.uom = uom;
        this.category = category;
        this.itemType = itemType;
        this.standardPrice = standardPrice;
    }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public BigDecimal getStandardPrice() { return standardPrice; }
    public void setStandardPrice(BigDecimal standardPrice) { this.standardPrice = standardPrice; }
}
