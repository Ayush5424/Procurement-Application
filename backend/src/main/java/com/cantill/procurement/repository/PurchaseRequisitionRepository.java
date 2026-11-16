package com.cantill.procurement.repository;

import com.cantill.procurement.domain.PurchaseRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, String> {
}
