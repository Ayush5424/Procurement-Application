package com.cantill.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    @Column(name = "supplier_id", length = 50)
    private String supplierId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "categories", length = 500, nullable = false)
    private String categories;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // Active, Blocked, Inactive

    @Column(name = "on_time_pct", precision = 5, scale = 2, nullable = false)
    private BigDecimal onTimePct;

    @Column(name = "quality_rating", precision = 3, scale = 1, nullable = false)
    private BigDecimal qualityRating;

    public Supplier() {}

    public Supplier(String supplierId, String name, String city, String categories, String status, BigDecimal onTimePct, BigDecimal qualityRating) {
        this.supplierId = supplierId;
        this.name = name;
        this.city = city;
        this.categories = categories;
        this.status = status;
        this.onTimePct = onTimePct;
        this.qualityRating = qualityRating;
    }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getOnTimePct() { return onTimePct; }
    public void setOnTimePct(BigDecimal onTimePct) { this.onTimePct = onTimePct; }

    public BigDecimal getQualityRating() { return qualityRating; }
    public void setQualityRating(BigDecimal qualityRating) { this.qualityRating = qualityRating; }

    public boolean isActive() {
        return "Active".equalsIgnoreCase(this.status);
    }
}
