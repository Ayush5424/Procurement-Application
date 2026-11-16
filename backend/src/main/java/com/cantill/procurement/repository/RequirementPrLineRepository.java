package com.cantill.procurement.repository;

import com.cantill.procurement.domain.RequirementPrLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequirementPrLineRepository extends JpaRepository<RequirementPrLine, Long> {
    List<RequirementPrLine> findByGroupId(String groupId);
    List<RequirementPrLine> findByPrNoAndPrLine(String prNo, Integer prLine);
}
