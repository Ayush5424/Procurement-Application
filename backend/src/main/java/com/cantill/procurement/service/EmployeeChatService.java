package com.cantill.procurement.service;

import com.cantill.procurement.domain.*;
import com.cantill.procurement.repository.*;
import com.cantill.procurement.service.engine.ItemMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmployeeChatService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ItemMatchingService itemMatchingService;
    private final ItemRepository itemRepository;
    private final PlantRepository plantRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final PrLineRepository prLineRepository;

    public record ChatResponseDto(
            String sessionId,
            String reply,
            String status, // ACTIVE, CLARIFICATION_NEEDED, CONFIRMATION_PENDING, COMPLETED
            ChatSession session,
            List<Item> candidateItems,
            PurchaseRequisition createdPr
    ) {}

    public EmployeeChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ItemMatchingService itemMatchingService,
            ItemRepository itemRepository,
            PlantRepository plantRepository,
            PurchaseRequisitionRepository purchaseRequisitionRepository,
            PrLineRepository prLineRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.itemMatchingService = itemMatchingService;
        this.itemRepository = itemRepository;
        this.plantRepository = plantRepository;
        this.purchaseRequisitionRepository = purchaseRequisitionRepository;
        this.prLineRepository = prLineRepository;
    }

    @Transactional
    public ChatResponseDto processMessage(String sessionId, String userMessage) {
        String activeSessionId = (sessionId == null || sessionId.trim().isEmpty())
                ? "chat-" + UUID.randomUUID().toString().substring(0, 8)
                : sessionId.trim();

        ChatSession session = chatSessionRepository.findById(activeSessionId)
                .orElseGet(() -> chatSessionRepository.save(new ChatSession(activeSessionId)));

        // Record incoming user message
        chatMessageRepository.save(new ChatMessage(activeSessionId, "USER", userMessage));

        String reply;
        List<Item> candidates = Collections.emptyList();
        PurchaseRequisition createdPr = null;

        // Check if user is confirming a pending PR
        if ("CONFIRMATION_PENDING".equals(session.getStatus())) {
            String lower = userMessage.trim().toLowerCase();
            if (lower.matches("^(yes|confirm|proceed|ok|sure|go ahead|yep|y).*")) {
                createdPr = finalizeAndCreatePr(session);
                session.setStatus("COMPLETED");
                session.setCreatedPrNo(createdPr.getPrNo());
                chatSessionRepository.save(session);

                reply = String.format("Purchase Requisition **%s** has been successfully created!\n\n" +
                                "- **PR Number:** %s\n" +
                                "- **Item:** %s (%s)\n" +
                                "- **Quantity:** %s %s\n" +
                                "- **Plant:** %s\n" +
                                "- **Need-by Date:** %s\n" +
                                "- **Urgency:** %s\n\n" +
                                "Procurement has received your requisition for policy evaluation.",
                        createdPr.getPrNo(), createdPr.getPrNo(),
                        session.getDraftDescription(), session.getDraftItemCode() != null ? session.getDraftItemCode() : "NEW",
                        session.getDraftQuantity(), session.getDraftUom() != null ? session.getDraftUom() : "NOS",
                        session.getDraftPlant(), session.getDraftNeedByDate(), session.getDraftUrgency());

                chatMessageRepository.save(new ChatMessage(activeSessionId, "ASSISTANT", reply));
                return new ChatResponseDto(activeSessionId, reply, session.getStatus(), session, candidates, createdPr);
            } else if (lower.matches("^(no|cancel|abort|stop|change).*")) {
                session.setStatus("ACTIVE");
                chatSessionRepository.save(session);
                reply = "Requisition cancelled. What would you like to change? (You can specify a different item, quantity, plant, or date).";
                chatMessageRepository.save(new ChatMessage(activeSessionId, "ASSISTANT", reply));
                return new ChatResponseDto(activeSessionId, reply, session.getStatus(), session, candidates, null);
            }
        }

        // Entity extraction from user message
        extractEntities(userMessage, session);

        // Check missing details and formulate response
        if (session.getDraftDescription() == null && session.getDraftItemCode() == null) {
            reply = "Hello! I can help you create a purchase requisition. What item do you need, for which plant, and by when? (e.g. *'Need 200 nitrile gloves for Chakan by next Friday'*).";
            chatMessageRepository.save(new ChatMessage(activeSessionId, "ASSISTANT", reply));
            chatSessionRepository.save(session);
            return new ChatResponseDto(activeSessionId, reply, session.getStatus(), session, candidates, null);
        }

        // Check if item matching is ambiguous
        if (session.getDraftItemCode() == null && session.getDraftDescription() != null) {
            ItemMatchingService.MatchResult match = itemMatchingService.matchItem(null, session.getDraftDescription());
            if (!match.isNewItem()) {
                session.setDraftItemCode(match.resolvedItemCode());
                session.setDraftDescription(match.matchedItem().getDescription());
                session.setDraftUom(match.matchedItem().getUom());
            } else {
                // Check if candidate items could match
                List<Item> potentialMatches = itemMatchingService.findCandidateItems(session.getDraftDescription(), 3);
                if (!potentialMatches.isEmpty()) {
                    candidates = potentialMatches;
                    StringBuilder sb = new StringBuilder("I found multiple potential matches for **'")
                            .append(session.getDraftDescription()).append("'**:\n");
                    for (int i = 0; i < potentialMatches.size(); i++) {
                        Item item = potentialMatches.get(i);
                        sb.append(String.format("%d. **%s** — %s (%s, ₹%s)\n", i + 1, item.getItemCode(), item.getDescription(), item.getUom(), item.getStandardPrice()));
                    }
                    sb.append("\nPlease reply with the number or item code, or let me know if this is a new item not in catalog.");
                    reply = sb.toString();
                    chatMessageRepository.save(new ChatMessage(activeSessionId, "ASSISTANT", reply));
                    chatSessionRepository.save(session);
                    return new ChatResponseDto(activeSessionId, reply, "CLARIFICATION_NEEDED", session, candidates, null);
                } else {
                    // Confirmed new item
                    session.setDraftItemCode("NEW");
                }
            }
        }

        // Follow up questions for missing information
        if (session.getDraftQuantity() == null) {
            reply = String.format("Got it for **%s**. How many units do you need?", session.getDraftDescription());
        } else if (session.getDraftPlant() == null) {
            reply = "Which plant is this requirement for? (e.g. **CHK1** for Chakan, or **PUN1** for Pune/Bhosari).";
        } else if (session.getDraftNeedByDate() == null) {
            reply = "What is the need-by date for this requirement? (e.g. *'by next Friday'* or *'2026-12-05'*).";
        } else if ("NEW".equalsIgnoreCase(session.getDraftItemCode()) && session.getDraftEstimatedUnitPrice() == null) {
            reply = "This item is not in our standard item catalog (New Item). Please provide an estimated unit price in INR.";
        } else {
            // All information collected! Show summary and ask confirmation
            session.setStatus("CONFIRMATION_PENDING");
            String estPriceLine = "NEW".equalsIgnoreCase(session.getDraftItemCode())
                    ? String.format("- **Estimated Unit Price:** ₹%s\n", session.getDraftEstimatedUnitPrice())
                    : "";

            reply = String.format("Here is a summary of your Purchase Requisition:\n\n" +
                            "- **Item:** %s\n" +
                            "- **Item Code:** %s\n" +
                            "- **Quantity:** %s %s\n" +
                            "- **Plant:** %s\n" +
                            "- **Need-by Date:** %s\n" +
                            "- **Urgency:** %s\n" +
                            "%s\n" +
                            "Would you like me to submit this requisition? Please reply **Yes** to confirm or **No** to modify.",
                    session.getDraftDescription(),
                    session.getDraftItemCode() != null ? session.getDraftItemCode() : "NEW",
                    session.getDraftQuantity(),
                    session.getDraftUom() != null ? session.getDraftUom() : "NOS",
                    session.getDraftPlant(),
                    session.getDraftNeedByDate(),
                    session.getDraftUrgency(),
                    estPriceLine);
        }

        chatMessageRepository.save(new ChatMessage(activeSessionId, "ASSISTANT", reply));
        chatSessionRepository.save(session);
        return new ChatResponseDto(activeSessionId, reply, session.getStatus(), session, candidates, null);
    }

    private void extractEntities(String text, ChatSession session) {
        String lower = text.toLowerCase();

        // 1. Plant extraction
        if (lower.contains("chakan") || lower.contains("chk1") || lower.contains("chk")) {
            session.setDraftPlant("CHK1");
        } else if (lower.contains("pune") || lower.contains("bhosari") || lower.contains("pun1") || lower.contains("pun")) {
            session.setDraftPlant("PUN1");
        }

        // 2. Urgency extraction
        if (lower.contains("urgent") || lower.contains("emergency") || lower.contains("critical") || lower.contains("asap")) {
            session.setDraftUrgency("Emergency");
        }

        // 3. Quantity extraction (e.g. "200 nitrile gloves", "need 50", "quantity 100", "15 nos")
        Pattern qtyPat = Pattern.compile("(?:need|require|qty|quantity)?\\s*(\\d+)\\s*(?:nos|pcs|kg|mtr|pair|bkt|set|ltr|job|pieces|units)?", Pattern.CASE_INSENSITIVE);
        Matcher qtyMat = qtyPat.matcher(text);
        if (qtyMat.find()) {
            try {
                String numStr = qtyMat.group(1);
                // Ensure it's not a year or item code digits
                int val = Integer.parseInt(numStr);
                if (val < 2020 || val > 2035) {
                    if (session.getDraftQuantity() == null) {
                        session.setDraftQuantity(new BigDecimal(val));
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Need-by Date extraction (e.g. "by next Friday", "by tomorrow", "2026-11-25", "next Monday")
        LocalDate baseDate = LocalDate.now();
        if (lower.contains("next friday")) {
            session.setDraftNeedByDate(baseDate.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)));
        } else if (lower.contains("next monday")) {
            session.setDraftNeedByDate(baseDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        } else if (lower.contains("tomorrow")) {
            session.setDraftNeedByDate(baseDate.plusDays(1));
        } else if (lower.contains("next week")) {
            session.setDraftNeedByDate(baseDate.plusDays(7));
        } else {
            // ISO date matching: yyyy-MM-dd
            Pattern datePat = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
            Matcher dateMat = datePat.matcher(text);
            if (dateMat.find()) {
                try {
                    session.setDraftNeedByDate(LocalDate.parse(dateMat.group(1)));
                } catch (Exception ignored) {}
            }
        }

        // 5. Estimated unit price (e.g. "price 150", "₹141100", "estimate 500", "500 inr")
        Pattern pricePat = Pattern.compile("(?:price|rate|estimate|estimated|cost|₹)?\\s*₹?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:inr|rs|rupees)?", Pattern.CASE_INSENSITIVE);
        Matcher priceMat = pricePat.matcher(text);
        if (priceMat.find() && session.getDraftQuantity() != null && "NEW".equalsIgnoreCase(session.getDraftItemCode())) {
            try {
                BigDecimal p = new BigDecimal(priceMat.group(1));
                if (!p.equals(session.getDraftQuantity())) {
                    session.setDraftEstimatedUnitPrice(p);
                }
            } catch (Exception ignored) {}
        }

        // 6. Direct item code extraction (e.g. ITM-1048)
        Pattern itemCodePat = Pattern.compile("(ITM-\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher itemCodeMat = itemCodePat.matcher(text);
        if (itemCodeMat.find()) {
            String code = itemCodeMat.group(1).toUpperCase();
            Optional<Item> item = itemRepository.findById(code);
            if (item.isPresent()) {
                session.setDraftItemCode(code);
                session.setDraftDescription(item.get().getDescription());
                session.setDraftUom(item.get().getUom());
                return;
            }
        }

        // 7. Item description extraction
        if (session.getDraftDescription() == null) {
            String cleaned = text.replaceAll("(?i)(need|require|please order|for chakan|for pune|for bhosari|by next friday|by tomorrow|urgent|emergency|asap)", " ");
            if (session.getDraftQuantity() != null) {
                cleaned = cleaned.replaceAll("\\b" + session.getDraftQuantity().intValue() + "\\b", " ");
            }
            cleaned = cleaned.replaceAll("\\s+", " ").trim();
            if (cleaned.length() > 2) {
                session.setDraftDescription(cleaned);
            }
        }
    }

    private PurchaseRequisition finalizeAndCreatePr(ChatSession session) {
        String prNo = "PR-CHAT-" + System.currentTimeMillis() % 100000;
        LocalDateTime now = LocalDateTime.now();

        PurchaseRequisition pr = new PurchaseRequisition(
                prNo,
                "Employee",
                "Operations",
                session.getDraftPlant() != null ? session.getDraftPlant() : "CHK1",
                now
        );
        purchaseRequisitionRepository.save(pr);

        PrLine line = new PrLine(
                prNo,
                1,
                now,
                session.getDraftPlant() != null ? session.getDraftPlant() : "CHK1",
                session.getDraftItemCode(),
                session.getDraftDescription() != null ? session.getDraftDescription() : "Item",
                session.getDraftQuantity() != null ? session.getDraftQuantity() : BigDecimal.ONE,
                session.getDraftUom() != null ? session.getDraftUom() : "NOS",
                session.getDraftEstimatedUnitPrice(),
                session.getDraftNeedByDate() != null ? session.getDraftNeedByDate() : now.toLocalDate().plusDays(7),
                session.getDraftUrgency() != null ? session.getDraftUrgency() : "Normal"
        );
        prLineRepository.save(line);

        return pr;
    }
}
