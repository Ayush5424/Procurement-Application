package com.cantill.procurement.repository;

import com.cantill.procurement.domain.PrLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrLineRepository extends JpaRepository<PrLine, Long> {
    List<PrLine> findByPrNoOrderByPrLineAsc(String prNo);
    List<PrLine> findByStatusOrderByCreatedAtAsc(String status);
    List<PrLine> findAllByOrderByCreatedAtAscPrNoAscPrLineAsc();
}
