package com.cantill.procurement.repository;

import com.cantill.procurement.domain.ContractLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractLineRepository extends JpaRepository<ContractLine, Long> {
    List<ContractLine> findByContractId(String contractId);
    List<ContractLine> findByItemCode(String itemCode);
    List<ContractLine> findByContractIdAndItemCode(String contractId, String itemCode);
}
