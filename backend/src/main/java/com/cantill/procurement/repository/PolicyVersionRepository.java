package com.cantill.procurement.repository;

import com.cantill.procurement.domain.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    Optional<PolicyVersion> findByVersionNumber(String versionNumber);
    List<PolicyVersion> findAllByOrderByEffectiveDateDesc();

    @Query("SELECT pv FROM PolicyVersion pv WHERE pv.status = 'ACTIVE' AND pv.effectiveDate <= :date ORDER BY pv.effectiveDate DESC")
    List<PolicyVersion> findActivePoliciesApplicableOnDate(@Param("date") LocalDate date);
}
