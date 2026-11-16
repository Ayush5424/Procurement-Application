package com.cantill.procurement.service.engine;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PoConsolidationService {

    public static class OpenPo {
        private final String poId;
        private final String supplierId;
        private final String plant;
        private final String route;
        private final LocalDateTime firstRequirementCreatedAt;
        private final List<String> groupIds = new ArrayList<>();

        public OpenPo(String poId, String supplierId, String plant, String route, LocalDateTime firstCreatedAt, String firstGroupId) {
            this.poId = poId;
            this.supplierId = supplierId;
            this.plant = plant;
            this.route = route;
            this.firstRequirementCreatedAt = firstCreatedAt;
            this.groupIds.add(firstGroupId);
        }

        public boolean canConsolidate(String supplierId, String plant, String route, LocalDateTime reqCreatedAt) {
            if (!this.supplierId.equalsIgnoreCase(supplierId)) return false;
            if (!this.plant.equalsIgnoreCase(plant)) return false;
            if (!this.route.equalsIgnoreCase(route)) return false;

            // Raised within 24 hours of the first requirement on that PO
            long secondsDiff = Duration.between(this.firstRequirementCreatedAt, reqCreatedAt).getSeconds();
            return secondsDiff >= 0 && secondsDiff <= (24 * 3600);
        }

        public void addRequirement(String groupId) {
            this.groupIds.add(groupId);
        }

        public String getPoId() { return poId; }
        public String getSupplierId() { return supplierId; }
        public String getPlant() { return plant; }
        public String getRoute() { return route; }
        public LocalDateTime getFirstRequirementCreatedAt() { return firstRequirementCreatedAt; }
        public List<String> getGroupIds() { return groupIds; }
    }

    public static class ConsolidatableRequirement {
        private final String groupId;
        private final String decision;
        private final String route;
        private final String supplierId;
        private final String plant;
        private final LocalDateTime firstCreatedAt;

        public ConsolidatableRequirement(String groupId, String decision, String route, String supplierId, String plant, LocalDateTime firstCreatedAt) {
            this.groupId = groupId;
            this.decision = decision;
            this.route = route;
            this.supplierId = supplierId;
            this.plant = plant;
            this.firstCreatedAt = firstCreatedAt;
        }

        public String getGroupId() { return groupId; }
        public String getDecision() { return decision; }
        public String getRoute() { return route; }
        public String getSupplierId() { return supplierId; }
        public String getPlant() { return plant; }
        public LocalDateTime getFirstCreatedAt() { return firstCreatedAt; }
    }

    /**
     * Consolidates qualifying PO requirements into PO IDs.
     * Returns a map of groupId -> poId.
     */
    public Map<String, String> consolidate(List<ConsolidatableRequirement> requirements) {
        // Sort by firstCreatedAt ascending
        List<ConsolidatableRequirement> sorted = new ArrayList<>(requirements);
        sorted.sort(Comparator.comparing(ConsolidatableRequirement::getFirstCreatedAt));

        Map<String, String> groupToPoMap = new HashMap<>();
        List<OpenPo> openPos = new ArrayList<>();
        int poCounter = 1;

        for (ConsolidatableRequirement req : sorted) {
            // Sourcing requests are NEVER consolidated into POs
            if (!"PO".equalsIgnoreCase(req.getDecision()) || req.getSupplierId() == null) {
                continue;
            }

            OpenPo matchingPo = null;
            for (OpenPo po : openPos) {
                if (po.canConsolidate(req.getSupplierId(), req.getPlant(), req.getRoute(), req.getFirstCreatedAt())) {
                    matchingPo = po;
                    break; // Earliest open PO
                }
            }

            if (matchingPo != null) {
                matchingPo.addRequirement(req.getGroupId());
                groupToPoMap.put(req.getGroupId(), matchingPo.getPoId());
            } else {
                String poId = String.format("PO-%04d", poCounter++);
                OpenPo newPo = new OpenPo(poId, req.getSupplierId(), req.getPlant(), req.getRoute(), req.getFirstCreatedAt(), req.getGroupId());
                openPos.add(newPo);
                groupToPoMap.put(req.getGroupId(), poId);
            }
        }

        return groupToPoMap;
    }
}
