package com.cantill.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_requisitions")
public class PurchaseRequisition {
    @Id
    @Column(name = "pr_no", length = 50)
    private String prNo;

    @Column(name = "requester", length = 100, nullable = false)
    private String requester;

    @Column(name = "department", length = 100, nullable = false)
    private String department;

    @Column(name = "plant", length = 10, nullable = false)
    private String plant;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PurchaseRequisition() {}

    public PurchaseRequisition(String prNo, String requester, String department, String plant, LocalDateTime createdAt) {
        this.prNo = prNo;
        this.requester = requester;
        this.department = department;
        this.plant = plant;
        this.createdAt = createdAt;
    }

    public String getPrNo() { return prNo; }
    public void setPrNo(String prNo) { this.prNo = prNo; }

    public String getRequester() { return requester; }
    public void setRequester(String requester) { this.requester = requester; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPlant() { return plant; }
    public void setPlant(String plant) { this.plant = plant; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
