package com.cantill.procurement.repository;

import com.cantill.procurement.domain.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    List<AuditRecord> findByRequirementIdOrderByTimestampDesc(String requirementId);
    List<AuditRecord> findAllByOrderByTimestampDesc();
}
