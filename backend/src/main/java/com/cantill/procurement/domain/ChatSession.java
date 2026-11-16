package com.cantill.procurement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    @Column(name = "session_id", length = 50)
    private String sessionId;

    @Column(name = "status", length = 50, nullable = false)
    private String status = "ACTIVE"; // ACTIVE, CONFIRMATION_PENDING, COMPLETED

    @Column(name = "draft_item_code", length = 50)
    private String draftItemCode;

    @Column(name = "draft_description", length = 255)
    private String draftDescription;

    @Column(name = "draft_quantity", precision = 15, scale = 2)
    private BigDecimal draftQuantity;

    @Column(name = "draft_uom", length = 20)
    private String draftUom;

    @Column(name = "draft_plant", length = 10)
    private String draftPlant;

    @Column(name = "draft_need_by_date")
    private LocalDate draftNeedByDate;

    @Column(name = "draft_urgency", length = 20)
    private String draftUrgency = "Normal";

    @Column(name = "draft_estimated_unit_price", precision = 15, scale = 2)
    private BigDecimal draftEstimatedUnitPrice;

    @Column(name = "created_pr_no", length = 50)
    private String createdPrNo;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ChatSession() {}

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDraftItemCode() { return draftItemCode; }
    public void setDraftItemCode(String draftItemCode) { this.draftItemCode = draftItemCode; }

    public String getDraftDescription() { return draftDescription; }
    public void setDraftDescription(String draftDescription) { this.draftDescription = draftDescription; }

    public BigDecimal getDraftQuantity() { return draftQuantity; }
    public void setDraftQuantity(BigDecimal draftQuantity) { this.draftQuantity = draftQuantity; }

    public String getDraftUom() { return draftUom; }
    public void setDraftUom(String draftUom) { this.draftUom = draftUom; }

    public String getDraftPlant() { return draftPlant; }
    public void setDraftPlant(String draftPlant) { this.draftPlant = draftPlant; }

    public LocalDate getDraftNeedByDate() { return draftNeedByDate; }
    public void setDraftNeedByDate(LocalDate draftNeedByDate) { this.draftNeedByDate = draftNeedByDate; }

    public String getDraftUrgency() { return draftUrgency; }
    public void setDraftUrgency(String draftUrgency) { this.draftUrgency = draftUrgency; }

    public BigDecimal getDraftEstimatedUnitPrice() { return draftEstimatedUnitPrice; }
    public void setDraftEstimatedUnitPrice(BigDecimal draftEstimatedUnitPrice) { this.draftEstimatedUnitPrice = draftEstimatedUnitPrice; }

    public String getCreatedPrNo() { return createdPrNo; }
    public void setCreatedPrNo(String createdPrNo) { this.createdPrNo = createdPrNo; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
