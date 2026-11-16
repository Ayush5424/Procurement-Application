package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.Contract;
import com.cantill.procurement.domain.ContractLine;
import com.cantill.procurement.domain.Supplier;
import com.cantill.procurement.repository.ContractLineRepository;
import com.cantill.procurement.repository.ContractRepository;
import com.cantill.procurement.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ContractEngineService {
    private static final Logger log = LoggerFactory.getLogger(ContractEngineService.class);

    private final ContractRepository contractRepository;
    private final ContractLineRepository contractLineRepository;
    private final SupplierRepository supplierRepository;

    public record ContractCandidate(
            Contract contract,
            ContractLine contractLine,
            BigDecimal rate,
            BigDecimal currentConsumed,
            BigDecimal valueCap,
            BigDecimal remainingCeiling,
            boolean hasCeilingForRequirement
    ) {}

    public static class CeilingTracker {
        private final Map<String, BigDecimal> consumedMap = new HashMap<>();

        public CeilingTracker(List<Contract> contracts) {
            for (Contract c : contracts) {
                consumedMap.put(c.getContractId(), c.getConsumedValue() != null ? c.getConsumedValue() : BigDecimal.ZERO);
            }
        }

        public BigDecimal getConsumed(String contractId, BigDecimal defaultConsumed) {
            return consumedMap.getOrDefault(contractId, defaultConsumed != null ? defaultConsumed : BigDecimal.ZERO);
        }

        public void consume(String contractId, BigDecimal amount) {
            BigDecimal current = consumedMap.getOrDefault(contractId, BigDecimal.ZERO);
            consumedMap.put(contractId, current.add(amount));
        }

        public void reset(List<Contract> contracts) {
            consumedMap.clear();
            for (Contract c : contracts) {
                consumedMap.put(c.getContractId(), c.getConsumedValue() != null ? c.getConsumedValue() : BigDecimal.ZERO);
            }
        }
    }

    public ContractEngineService(
            ContractRepository contractRepository,
            ContractLineRepository contractLineRepository,
            SupplierRepository supplierRepository) {
        this.contractRepository = contractRepository;
        this.contractLineRepository = contractLineRepository;
        this.supplierRepository = supplierRepository;
    }

    public CeilingTracker createCeilingTracker() {
        return new CeilingTracker(contractRepository.findAll());
    }

    /**
     * Finds all contracts covering the item valid on the requirement date whose supplier is Active.
     */
    public List<ContractCandidate> findApplicableContracts(
            String itemCode,
            LocalDate reqDate,
            BigDecimal totalQuantity,
            CeilingTracker tracker) {

        if (itemCode == null || "NEW".equalsIgnoreCase(itemCode)) {
            return Collections.emptyList();
        }

        List<ContractLine> lines = contractLineRepository.findByItemCode(itemCode);
        List<ContractCandidate> candidates = new ArrayList<>();

        for (ContractLine line : lines) {
            Optional<Contract> cOpt = contractRepository.findById(line.getContractId());
            if (cOpt.isEmpty()) continue;
            Contract contract = cOpt.get();

            // 1. Contract active flag
            if (contract.getIsActive() != null && !contract.getIsActive()) continue;

            // 2. Requirement date between start and end date (inclusive)
            if (reqDate.isBefore(contract.getStartDate()) || reqDate.isAfter(contract.getEndDate())) {
                continue;
            }

            // 3. Supplier must be Active
            Optional<Supplier> sOpt = supplierRepository.findById(contract.getSupplierId());
            if (sOpt.isEmpty() || !sOpt.get().isActive()) {
                continue;
            }

            // 4. Ceiling check
            BigDecimal currentConsumed = tracker.getConsumed(contract.getContractId(), contract.getConsumedValue());
            BigDecimal reqCost = line.getRate().multiply(totalQuantity);
            BigDecimal projectedConsumed = currentConsumed.add(reqCost);
            boolean hasCeiling = projectedConsumed.compareTo(contract.getValueCap()) <= 0;
            BigDecimal remaining = contract.getValueCap().subtract(currentConsumed);

            candidates.add(new ContractCandidate(
                    contract,
                    line,
                    line.getRate(),
                    currentConsumed,
                    contract.getValueCap(),
                    remaining,
                    hasCeiling
            ));
        }

        // Sort by rate ascending (lowest rate first)
        candidates.sort(Comparator.comparing(ContractCandidate::rate));
        return candidates;
    }

    /**
     * Selects the lowest-rate contract that still has sufficient ceiling.
     */
    public Optional<ContractCandidate> selectBestUsableContract(
            String itemCode,
            LocalDate reqDate,
            BigDecimal totalQuantity,
            CeilingTracker tracker) {

        List<ContractCandidate> applicable = findApplicableContracts(itemCode, reqDate, totalQuantity, tracker);
        for (ContractCandidate cand : applicable) {
            if (cand.hasCeilingForRequirement()) {
                return Optional.of(cand);
            }
        }
        return Optional.empty();
    }
}
