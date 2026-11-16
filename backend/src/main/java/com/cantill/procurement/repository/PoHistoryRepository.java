package com.cantill.procurement.repository;

import com.cantill.procurement.domain.PoHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PoHistoryRepository extends JpaRepository<PoHistory, Long> {
    List<PoHistory> findByItemCodeAndPoDateBetweenOrderByPoDateDesc(String itemCode, LocalDate startDate, LocalDate endDate);
    List<PoHistory> findByItemCodeAndPoDateLessThanEqualOrderByPoDateDesc(String itemCode, LocalDate poDate);
    List<PoHistory> findByItemCodeOrderByPoDateDesc(String itemCode);

    @Query("SELECT p FROM PoHistory p WHERE p.itemCode = :itemCode AND p.poDate >= :sinceDate AND p.poDate <= :asOfDate ORDER BY p.poDate DESC")
    List<PoHistory> findRecentPosForItem(@Param("itemCode") String itemCode, @Param("sinceDate") LocalDate sinceDate, @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT p FROM PoHistory p WHERE p.supplierId = :supplierId AND p.itemCode = :itemCode AND p.poDate >= :sinceDate AND p.poDate <= :asOfDate ORDER BY p.poDate DESC")
    List<PoHistory> findSupplierRecentPosForItem(@Param("supplierId") String supplierId, @Param("itemCode") String itemCode, @Param("sinceDate") LocalDate sinceDate, @Param("asOfDate") LocalDate asOfDate);
}
