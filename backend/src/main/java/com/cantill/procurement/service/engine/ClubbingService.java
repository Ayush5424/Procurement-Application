package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.PrLine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ClubbingService {

    public static class RequirementDraft {
        private final String groupId;
        private final String resolvedItemCode;
        private final String plant;
        private final LocalDateTime firstCreatedAt;
        private final LocalDate firstNeedByDate;
        private final List<PrLine> prLines = new ArrayList<>();
        private BigDecimal totalQuantity = BigDecimal.ZERO;
        private boolean isEmergency = false;

        public RequirementDraft(String groupId, PrLine firstLine, String resolvedItemCode) {
            this.groupId = groupId;
            this.resolvedItemCode = resolvedItemCode;
            this.plant = firstLine.getPlant();
            this.firstCreatedAt = firstLine.getCreatedAt();
            this.firstNeedByDate = firstLine.getNeedByDate();
            addLine(firstLine);
        }

        public void addLine(PrLine line) {
            this.prLines.add(line);
            if (line.getQuantity() != null) {
                this.totalQuantity = this.totalQuantity.add(line.getQuantity());
            }
            if (line.isEmergency()) {
                this.isEmergency = true;
            }
        }

        public boolean canJoin(PrLine line, String lineResolvedItem) {
            // New items must never be clubbed
            if ("NEW".equalsIgnoreCase(this.resolvedItemCode) || "NEW".equalsIgnoreCase(lineResolvedItem)) {
                return false;
            }

            // Same item
            if (!this.resolvedItemCode.equalsIgnoreCase(lineResolvedItem)) {
                return false;
            }

            // Same plant
            if (!this.plant.equalsIgnoreCase(line.getPlant())) {
                return false;
            }

            // Created within 14 calendar days of requirement's first line
            LocalDate reqCreationDate = this.firstCreatedAt.toLocalDate();
            LocalDate lineCreationDate = line.getCreatedAt().toLocalDate();
            long daysFromFirstCreation = ChronoUnit.DAYS.between(reqCreationDate, lineCreationDate);
            if (daysFromFirstCreation < 0 || daysFromFirstCreation > 14) {
                return false;
            }

            // Need-by date within 7 days either side of first line's need-by date
            long daysFromFirstNeedBy = Math.abs(ChronoUnit.DAYS.between(this.firstNeedByDate, line.getNeedByDate()));
            return daysFromFirstNeedBy <= 7;
        }

        public String getGroupId() { return groupId; }
        public String getResolvedItemCode() { return resolvedItemCode; }
        public String getPlant() { return plant; }
        public LocalDateTime getFirstCreatedAt() { return firstCreatedAt; }
        public LocalDate getFirstNeedByDate() { return firstNeedByDate; }
        public List<PrLine> getPrLines() { return prLines; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }
        public boolean isEmergency() { return isEmergency; }
    }

    public List<RequirementDraft> clubLines(List<PrLine> rawLines, Map<PrLine, String> resolvedItemCodes) {
        // 1. Sort lines in order of: creation time, then PR number, then PR line number
        List<PrLine> sortedLines = new ArrayList<>(rawLines);
        sortedLines.sort(Comparator.comparing(PrLine::getCreatedAt)
                .thenComparing(PrLine::getPrNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(PrLine::getPrLine, Comparator.nullsLast(Integer::compareTo)));

        List<RequirementDraft> requirements = new ArrayList<>();
        int groupCounter = 1;

        for (PrLine line : sortedLines) {
            String resolvedItem = resolvedItemCodes.getOrDefault(line, line.getItemCode() != null ? line.getItemCode() : "NEW");

            RequirementDraft joinedReq = null;
            for (RequirementDraft req : requirements) {
                if (req.canJoin(line, resolvedItem)) {
                    joinedReq = req;
                    break; // Join the earliest requirement
                }
            }

            if (joinedReq != null) {
                joinedReq.addLine(line);
            } else {
                String groupId = String.format("REQ-%04d", groupCounter++);
                RequirementDraft newReq = new RequirementDraft(groupId, line, resolvedItem);
                requirements.add(newReq);
            }
        }

        return requirements;
    }
}
