package com.cantill.procurement.repository;

import com.cantill.procurement.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {
    List<Contract> findByIsActiveTrue();

    @Query("SELECT c FROM Contract c WHERE c.isActive = true AND c.startDate <= :reqDate AND c.endDate >= :reqDate")
    List<Contract> findValidContractsOnDate(@Param("reqDate") LocalDate reqDate);

    @Query("SELECT c FROM Contract c WHERE c.supplierId = :supplierId AND c.startDate <= :asOfDate ORDER BY c.startDate DESC")
    List<Contract> findContractsForSupplierOnOrBeforeDate(@Param("supplierId") String supplierId, @Param("asOfDate") LocalDate asOfDate);
}
