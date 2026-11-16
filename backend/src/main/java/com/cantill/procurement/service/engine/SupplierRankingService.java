package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.Contract;
import com.cantill.procurement.domain.ContractLine;
import com.cantill.procurement.domain.PoHistory;
import com.cantill.procurement.domain.Supplier;
import com.cantill.procurement.repository.ContractLineRepository;
import com.cantill.procurement.repository.ContractRepository;
import com.cantill.procurement.repository.PoHistoryRepository;
import com.cantill.procurement.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SupplierRankingService {

    private final SupplierRepository supplierRepository;
    private final PoHistoryRepository poHistoryRepository;
    private final ContractRepository contractRepository;
    private final ContractLineRepository contractLineRepository;

    public record RankedSupplier(
            int rank,
            Supplier supplier,
            double totalScore,
            int poCount,
            double recency,
            boolean hasContract,
            BigDecimal onTimePct,
            BigDecimal qualityRating,
            BigDecimal referencePrice,
            String priceSource, // "PO_HISTORY", "CONTRACT", "NONE"
            String explanation
    ) {}

    public SupplierRankingService(
            SupplierRepository supplierRepository,
            PoHistoryRepository poHistoryRepository,
            ContractRepository contractRepository,
            ContractLineRepository contractLineRepository) {
        this.supplierRepository = supplierRepository;
        this.poHistoryRepository = poHistoryRepository;
        this.contractRepository = contractRepository;
        this.contractLineRepository = contractLineRepository;
    }

    public List<RankedSupplier> getPreferredSupplierList(String itemCode, LocalDate asOfDate) {
        if (itemCode == null || "NEW".equalsIgnoreCase(itemCode)) {
            return Collections.emptyList();
        }

        LocalDate since548 = asOfDate.minusDays(548);
        List<PoHistory> allRecentPos = poHistoryRepository.findRecentPosForItem(itemCode, since548, asOfDate);

        // Group POs by supplier
        Map<String, List<PoHistory>> posBySupplier = new HashMap<>();
        for (PoHistory po : allRecentPos) {
            posBySupplier.computeIfAbsent(po.getSupplierId(), k -> new ArrayList<>()).add(po);
        }

        // Contracts covering item that started on or before asOfDate
        List<ContractLine> contractLines = contractLineRepository.findByItemCode(itemCode);
        Map<String, List<Contract>> contractsBySupplier = new HashMap<>();
        for (ContractLine cl : contractLines) {
            Optional<Contract> cOpt = contractRepository.findById(cl.getContractId());
            if (cOpt.isPresent()) {
                Contract c = cOpt.get();
                if (!c.getStartDate().isAfter(asOfDate)) {
                    contractsBySupplier.computeIfAbsent(c.getSupplierId(), k -> new ArrayList<>()).add(c);
                }
            }
        }

        // Union of suppliers with POs or contracts
        Set<String> eligibleSupplierIds = new HashSet<>();
        eligibleSupplierIds.addAll(posBySupplier.keySet());
        eligibleSupplierIds.addAll(contractsBySupplier.keySet());

        List<RankedSupplier> scoredSuppliers = new ArrayList<>();

        for (String supplierId : eligibleSupplierIds) {
            Optional<Supplier> sOpt = supplierRepository.findById(supplierId);
            if (sOpt.isEmpty()) continue;
            Supplier supplier = sOpt.get();

            // Exclude Blocked and Inactive suppliers
            if (!supplier.isActive()) {
                continue;
            }

            List<PoHistory> supplierPos = posBySupplier.getOrDefault(supplierId, Collections.emptyList());
            int poCount = supplierPos.size();

            // Calculate recency
            double recency = 0.0;
            if (!supplierPos.isEmpty()) {
                LocalDate mostRecentPoDate = supplierPos.get(0).getPoDate();
                long daysAgo = ChronoUnit.DAYS.between(mostRecentPoDate, asOfDate);
                if (daysAgo <= 90) {
                    recency = 1.0;
                } else if (daysAgo <= 365) {
                    recency = 0.5;
                } else {
                    recency = 0.0;
                }
            }

            boolean hasContract = contractsBySupplier.containsKey(supplierId) && !contractsBySupplier.get(supplierId).isEmpty();

            BigDecimal onTime = supplier.getOnTimePct() != null ? supplier.getOnTimePct() : BigDecimal.ZERO;
            BigDecimal quality = supplier.getQualityRating() != null ? supplier.getQualityRating() : BigDecimal.ZERO;

            // Score formula:
            // PO count: max 5 POs = 1.0 (weight 25)
            // Recency: 1.0, 0.5, 0.0 (weight 25)
            // Contract history: 1.0 or 0.0 (weight 20)
            // On-time: % (weight 15)
            // Quality: / 5.0 (weight 15)
            double poFactor = Math.min((double) poCount / 5.0, 1.0);
            double contractFactor = hasContract ? 1.0 : 0.0;
            double onTimeFactor = onTime.doubleValue() / 100.0;
            double qualityFactor = quality.doubleValue() / 5.0;

            double score = (poFactor * 25.0)
                         + (recency * 25.0)
                         + (contractFactor * 20.0)
                         + (onTimeFactor * 15.0)
                         + (qualityFactor * 15.0);

            // Determine reference price
            BigDecimal refPrice = null;
            String priceSource = "NONE";
            if (!supplierPos.isEmpty()) {
                refPrice = supplierPos.get(0).getUnitPrice();
                priceSource = "PO_HISTORY";
            } else if (hasContract) {
                // Latest contract rate
                List<Contract> scList = contractsBySupplier.get(supplierId);
                scList.sort(Comparator.comparing(Contract::getStartDate).reversed());
                String latestContractId = scList.get(0).getContractId();
                List<ContractLine> cls = contractLineRepository.findByContractIdAndItemCode(latestContractId, itemCode);
                if (!cls.isEmpty()) {
                    refPrice = cls.get(0).getRate();
                    priceSource = "CONTRACT";
                }
            }

            String explanation = String.format("Score %.1f (POs: %d, Recency: %.1f, Contract: %s, On-Time: %s%%, Quality: %s)",
                    score, poCount, recency, hasContract ? "Yes" : "No", onTime, quality);

            scoredSuppliers.add(new RankedSupplier(
                    0, supplier, score, poCount, recency, hasContract, onTime, quality, refPrice, priceSource, explanation
            ));
        }

        // Sort by score descending, then by quality desc, then by onTime desc, then by supplierId
        scoredSuppliers.sort((a, b) -> {
            int cmp = Double.compare(b.totalScore(), a.totalScore());
            if (cmp != 0) return cmp;
            int qCmp = b.qualityRating().compareTo(a.qualityRating());
            if (qCmp != 0) return qCmp;
            int otCmp = b.onTimePct().compareTo(a.onTimePct());
            if (otCmp != 0) return otCmp;
            return a.supplier().getSupplierId().compareTo(b.supplier().getSupplierId());
        });

        // Assign ranks (1, 2, 3...)
        List<RankedSupplier> ranked = new ArrayList<>();
        for (int i = 0; i < scoredSuppliers.size(); i++) {
            RankedSupplier s = scoredSuppliers.get(i);
            ranked.add(new RankedSupplier(
                    i + 1, s.supplier(), s.totalScore(), s.poCount(), s.recency(), s.hasContract(),
                    s.onTimePct(), s.qualityRating(), s.referencePrice(), s.priceSource(), s.explanation()
            ));
        }

        return ranked;
    }

    public List<Supplier> getSourcingSuggestions(String itemCode, String category, LocalDate asOfDate, int countNeeded) {
        if ("NEW".equalsIgnoreCase(itemCode)) {
            return Collections.emptyList();
        }

        List<RankedSupplier> preferred = getPreferredSupplierList(itemCode, asOfDate);
        List<Supplier> result = new ArrayList<>();
        Set<String> includedIds = new HashSet<>();

        for (RankedSupplier rs : preferred) {
            if (result.size() >= countNeeded) break;
            result.add(rs.supplier());
            includedIds.add(rs.supplier().getSupplierId());
        }

        if (result.size() < countNeeded && category != null && !category.trim().isEmpty()) {
            List<Supplier> activeSuppliers = supplierRepository.findByStatusIgnoreCase("Active");
            List<Supplier> categorySuppliers = new ArrayList<>();

            for (Supplier s : activeSuppliers) {
                if (includedIds.contains(s.getSupplierId())) continue;
                if (s.getCategories() != null && s.getCategories().toLowerCase().contains(category.toLowerCase())) {
                    categorySuppliers.add(s);
                }
            }

            // Rank category fallback suppliers by quality and on-time
            categorySuppliers.sort((a, b) -> {
                double scoreA = (a.getQualityRating().doubleValue() / 5.0 * 50.0) + (a.getOnTimePct().doubleValue());
                double scoreB = (b.getQualityRating().doubleValue() / 5.0 * 50.0) + (b.getOnTimePct().doubleValue());
                int cmp = Double.compare(scoreB, scoreA);
                if (cmp != 0) return cmp;
                return a.getSupplierId().compareTo(b.getSupplierId());
            });

            for (Supplier cs : categorySuppliers) {
                if (result.size() >= countNeeded) break;
                result.add(cs);
                includedIds.add(cs.getSupplierId());
            }
        }

        return result;
    }
}
