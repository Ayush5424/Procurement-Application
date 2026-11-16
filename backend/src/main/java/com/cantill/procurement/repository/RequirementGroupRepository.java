package com.cantill.procurement.repository;

import com.cantill.procurement.domain.RequirementGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequirementGroupRepository extends JpaRepository<RequirementGroup, String> {
    List<RequirementGroup> findAllByOrderByFirstPrCreatedAtDesc();
    List<RequirementGroup> findByStatusOrderByFirstPrCreatedAtDesc(String status);
}
