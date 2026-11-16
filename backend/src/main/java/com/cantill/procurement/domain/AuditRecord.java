package com.cantill.procurement.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_records")
public class AuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", length = 50, nullable = false)
    private String requirementId;

    @Column(name = "original_decision", length = 50, nullable = false)
    private String originalDecision;

    @Column(name = "original_route", length = 50, nullable = false)
    private String originalRoute;

    @Column(name = "original_supplier_id", length = 50)
    private String originalSupplierId;

    @Column(name = "action", length = 50, nullable = false)
    private String action; // APPROVED, OVERRIDDEN

    @Column(name = "actor", length = 100, nullable = false)
    private String actor;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "new_decision", length = 50)
    private String newDecision;

    @Column(name = "new_route", length = 50)
    private String newRoute;

    @Column(name = "new_supplier_id", length = 50)
    private String newSupplierId;

    public AuditRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequirementId() { return requirementId; }
    public void setRequirementId(String requirementId) { this.requirementId = requirementId; }

    public String getOriginalDecision() { return originalDecision; }
    public void setOriginalDecision(String originalDecision) { this.originalDecision = originalDecision; }

    public String getOriginalRoute() { return originalRoute; }
    public void setOriginalRoute(String originalRoute) { this.originalRoute = originalRoute; }

    public String getOriginalSupplierId() { return originalSupplierId; }
    public void setOriginalSupplierId(String originalSupplierId) { this.originalSupplierId = originalSupplierId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }

    public String getNewDecision() { return newDecision; }
    public void setNewDecision(String newDecision) { this.newDecision = newDecision; }

    public String getNewRoute() { return newRoute; }
    public void setNewRoute(String newRoute) { this.newRoute = newRoute; }

    public String getNewSupplierId() { return newSupplierId; }
    public void setNewSupplierId(String newSupplierId) { this.newSupplierId = newSupplierId; }
}
