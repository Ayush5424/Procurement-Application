package com.cantill.procurement.service.engine;

import com.cantill.procurement.domain.Item;
import com.cantill.procurement.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ItemMatchingService {
    private static final Logger log = LoggerFactory.getLogger(ItemMatchingService.class);

    private final ItemRepository itemRepository;

    public record MatchResult(String resolvedItemCode, Item matchedItem, double confidence, boolean isNewItem) {}

    public ItemMatchingService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public MatchResult matchItem(String itemCode, String description) {
        // 1. If item code is provided and exists in item master, use it
        if (itemCode != null && !itemCode.trim().isEmpty()) {
            String code = itemCode.trim().toUpperCase();
            Optional<Item> itemOpt = itemRepository.findById(code);
            if (itemOpt.isPresent()) {
                return new MatchResult(code, itemOpt.get(), 1.0, false);
            }
        }

        // 2. If no description provided or empty
        if (description == null || description.trim().isEmpty()) {
            return new MatchResult("NEW", null, 0.0, true);
        }

        String rawDesc = description.trim();
        List<Item> allItems = itemRepository.findAll();

        // 3. Exact description match (case-insensitive)
        for (Item item : allItems) {
            if (item.getDescription().equalsIgnoreCase(rawDesc)) {
                return new MatchResult(item.getItemCode(), item, 1.0, false);
            }
        }

        // 4. Token-based / Normalized abbreviation fuzzy matching
        String normDesc = normalize(rawDesc);
        Set<String> queryTokens = tokenize(normDesc);

        Item bestItem = null;
        double bestScore = 0.0;

        for (Item item : allItems) {
            String normItemDesc = normalize(item.getDescription());
            Set<String> itemTokens = tokenize(normItemDesc);

            double score = calculateMatchScore(queryTokens, itemTokens, normDesc, normItemDesc);
            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            }
        }

        // Threshold for accepting match
        if (bestItem != null && bestScore >= 0.55) {
            log.info("Matched '{}' to '{}' ({}) with score {}", rawDesc, bestItem.getDescription(), bestItem.getItemCode(), String.format("%.2f", bestScore));
            return new MatchResult(bestItem.getItemCode(), bestItem, bestScore, false);
        }

        log.info("No item master match for '{}' (best score {}). Classifying as NEW.", rawDesc, String.format("%.2f", bestScore));
        return new MatchResult("NEW", null, bestScore, true);
    }

    public List<Item> findCandidateItems(String description, int limit) {
        if (description == null || description.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normDesc = normalize(description.trim());
        Set<String> queryTokens = tokenize(normDesc);

        List<Item> allItems = itemRepository.findAll();
        List<Map.Entry<Item, Double>> scored = new ArrayList<>();

        for (Item item : allItems) {
            String normItemDesc = normalize(item.getDescription());
            Set<String> itemTokens = tokenize(normItemDesc);
            double score = calculateMatchScore(queryTokens, itemTokens, normDesc, normItemDesc);
            if (score >= 0.30) {
                scored.add(new AbstractMap.SimpleEntry<>(item, score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return scored.stream().limit(limit).map(Map.Entry::getKey).toList();
    }

    private double calculateMatchScore(Set<String> queryTokens, Set<String> itemTokens, String normQuery, String normItem) {
        if (queryTokens.isEmpty() || itemTokens.isEmpty()) return 0.0;

        // Substring check
        if (normItem.contains(normQuery) || normQuery.contains(normItem)) {
            return 0.95;
        }

        // Token overlap
        int matchedTokens = 0;
        int criticalMatches = 0;
        int queryCriticalTokens = 0;

        for (String q : queryTokens) {
            boolean isCritical = isKeyToken(q);
            if (isCritical) queryCriticalTokens++;

            for (String it : itemTokens) {
                if (q.equals(it) || areEquivalents(q, it)) {
                    matchedTokens++;
                    if (isCritical) criticalMatches++;
                    break;
                }
            }
        }

        if (queryCriticalTokens > 0 && criticalMatches == 0) {
            return 0.1; // Missed key model / dimension tokens
        }

        double tokenScore = (double) (matchedTokens * 2) / (queryTokens.size() + itemTokens.size());
        double queryCoverage = (double) matchedTokens / queryTokens.size();

        return (tokenScore * 0.4) + (queryCoverage * 0.6);
    }

    private boolean isKeyToken(String token) {
        // Model designations like 6308, 6205, 5/2, 1/2, 10mm, 2rs, zz, 1250x2500
        // Standalone numbers under 1000 without units are often quantities, not model numbers
        if (token.matches("^\\d{1,3}$")) {
            return false;
        }
        return token.matches(".*\\d+.*") || token.equals("zz") || token.equals("2rs");
    }

    private boolean areEquivalents(String a, String b) {
        if (a.equals(b)) return true;
        // Check abbreviation expansions
        return expandAbbreviation(a).equals(expandAbbreviation(b));
    }

    private String expandAbbreviation(String term) {
        return switch (term) {
            case "brg" -> "bearing";
            case "hyd" -> "hydraulic";
            case "cyl" -> "cylinder";
            case "sol" -> "solenoid";
            case "vlv" -> "valve";
            case "al", "alu", "alum" -> "aluminium";
            case "gr" -> "grade";
            case "vci" -> "vci";
            default -> term;
        };
    }

    private String normalize(String str) {
        if (str == null) return "";
        String s = str.toLowerCase();
        s = s.replace("-", " ")
             .replace("/", " ")
             .replace("x", " ")
             .replace("×", " ")
             .replace("(", " ")
             .replace(")", " ")
             .replace(",", " ")
             .replace(".", " ")
             .replaceAll("\\s+", " ")
             .trim();
        return s;
    }

    private Set<String> tokenize(String str) {
        Set<String> tokens = new HashSet<>();
        for (String word : str.split("\\s+")) {
            word = word.trim();
            if (!word.isEmpty() && !isStopWord(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private boolean isStopWord(String word) {
        return Set.of("for", "and", "the", "in", "of", "with", "to", "by", "per", "need").contains(word);
    }
}
