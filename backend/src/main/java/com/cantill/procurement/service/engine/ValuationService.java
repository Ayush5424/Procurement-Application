package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.Item;
import com.cantill.procurement.domain.PoHistory;
import com.cantill.procurement.domain.Supplier;
import com.cantill.procurement.repository.ItemRepository;
import com.cantill.procurement.repository.PoHistoryRepository;
import com.cantill.procurement.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ValuationService {

    private final ItemRepository itemRepository;
    private final PoHistoryRepository poHistoryRepository;
    private final SupplierRepository supplierRepository;
    private final ContractEngineService contractEngineService;

    public record ValuationResult(
            BigDecimal unitPrice,
            BigDecimal totalValue,
            String priceSource, // "CONTRACT_USED", "OUT_OF_CEILING_CONTRACT_LOWEST", "PO_HISTORY", "STANDARD_PRICE", "REQUESTER_ESTIMATE"
            String referenceDoc, // Contract ID or PO number or N/A
            String explanation,
            boolean boughtFromActiveSupplierInLast365Days,
            Optional<ContractEngineService.ContractCandidate> usableContract
    ) {}

    public ValuationService(
            ItemRepository itemRepository,
            PoHistoryRepository poHistoryRepository,
            SupplierRepository supplierRepository,
            ContractEngineService contractEngineService) {
        this.itemRepository = itemRepository;
        this.poHistoryRepository = poHistoryRepository;
        this.supplierRepository = supplierRepository;
        this.contractEngineService = contractEngineService;
    }

    public ValuationResult valueRequirement(
            String resolvedItemCode,
            BigDecimal totalQuantity,
            LocalDate reqDate,
            BigDecimal requesterEstimatePrice,
            ContractEngineService.CeilingTracker tracker) {

        // 1. New items
        if ("NEW".equalsIgnoreCase(resolvedItemCode)) {
            BigDecimal unitPrice = requesterEstimatePrice != null ? requesterEstimatePrice : BigDecimal.ZERO;
            BigDecimal totalValue = unitPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);
            return new ValuationResult(
                    unitPrice,
                    totalValue,
                    "REQUESTER_ESTIMATE",
                    "N/A",
                    "New item valued at requester estimated unit price of ₹" + unitPrice,
                    false,
                    Optional.empty()
            );
        }

        // 2. Check contract rates
        List<ContractEngineService.ContractCandidate> applicableContracts =
                contractEngineService.findApplicableContracts(resolvedItemCode, reqDate, totalQuantity, tracker);

        Optional<ContractEngineService.ContractCandidate> usableContract = applicableContracts.stream()
                .filter(ContractEngineService.ContractCandidate::hasCeilingForRequirement)
                .findFirst();

        if (usableContract.isPresent()) {
            ContractEngineService.ContractCandidate candidate = usableContract.get();
            BigDecimal unitPrice = candidate.rate();
            BigDecimal totalValue = unitPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);
            boolean boughtIn365d = checkBoughtFromActiveSupplierIn365d(resolvedItemCode, reqDate);
            return new ValuationResult(
                    unitPrice,
                    totalValue,
                    "CONTRACT_USED",
                    candidate.contract().getContractId(),
                    "Contract " + candidate.contract().getContractId() + " rate ₹" + unitPrice,
                    boughtIn365d,
                    usableContract
            );
        }

        if (!applicableContracts.isEmpty()) {
            // All applicable contracts are out of ceiling: use the lowest applicable contract rate
            ContractEngineService.ContractCandidate lowestRateContract = applicableContracts.get(0); // Already sorted by rate asc
            BigDecimal unitPrice = lowestRateContract.rate();
            BigDecimal totalValue = unitPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);
            boolean boughtIn365d = checkBoughtFromActiveSupplierIn365d(resolvedItemCode, reqDate);
            return new ValuationResult(
                    unitPrice,
                    totalValue,
                    "OUT_OF_CEILING_CONTRACT_LOWEST",
                    lowestRateContract.contract().getContractId(),
                    "All applicable contracts out of ceiling. Valued at lowest contract rate ₹" + unitPrice + " (" + lowestRateContract.contract().getContractId() + ")",
                    boughtIn365d,
                    Optional.empty()
            );
        }

        // 3. Most recent PO price for the item within 365 days before requirement date
        LocalDate since365 = reqDate.minusDays(365);
        List<PoHistory> recentPos = poHistoryRepository.findRecentPosForItem(resolvedItemCode, since365, reqDate);
        boolean boughtIn365d = false;

        if (!recentPos.isEmpty()) {
            for (PoHistory po : recentPos) {
                Optional<Supplier> sOpt = supplierRepository.findById(po.getSupplierId());
                if (sOpt.isPresent() && sOpt.get().isActive()) {
                    boughtIn365d = true;
                    break;
                }
            }

            PoHistory latestPo = recentPos.get(0);
            BigDecimal unitPrice = latestPo.getUnitPrice();
            BigDecimal totalValue = unitPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);
            return new ValuationResult(
                    unitPrice,
                    totalValue,
                    "PO_HISTORY",
                    latestPo.getPoNumber(),
                    "Most recent PO price ₹" + unitPrice + " (PO " + latestPo.getPoNumber() + " on " + latestPo.getPoDate() + ")",
                    boughtIn365d,
                    Optional.empty()
            );
        }

        // 4. Item master standard price
        Optional<Item> itemOpt = itemRepository.findById(resolvedItemCode);
        BigDecimal standardPrice = itemOpt.map(Item::getStandardPrice).orElse(BigDecimal.ZERO);
        BigDecimal totalValue = standardPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);

        return new ValuationResult(
                standardPrice,
                totalValue,
                "STANDARD_PRICE",
                "Item Master",
                "Item master standard price ₹" + standardPrice,
                false,
                Optional.empty()
        );
    }

    public boolean checkBoughtFromActiveSupplierIn365d(String itemCode, LocalDate asOfDate) {
        LocalDate sinceDate = asOfDate.minusDays(365);
        List<PoHistory> pos = poHistoryRepository.findRecentPosForItem(itemCode, sinceDate, asOfDate);
        for (PoHistory po : pos) {
            Optional<Supplier> s = supplierRepository.findById(po.getSupplierId());
            if (s.isPresent() && s.get().isActive()) {
                return true;
            }
        }
        return false;
    }
}
