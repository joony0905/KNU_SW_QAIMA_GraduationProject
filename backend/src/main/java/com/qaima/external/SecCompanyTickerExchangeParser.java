package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.dto.sec.SecCompanyTickerExchangeEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecCompanyTickerExchangeParser {

    private final ObjectMapper objectMapper;

    public List<SecCompanyTickerExchangeEntry> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode fields = root.path("fields");
            JsonNode data = root.path("data");
            if (!fields.isArray() || !data.isArray()) {
                throw new IllegalArgumentException("Unsupported SEC ticker exchange payload");
            }

            Map<String, Integer> index = buildFieldIndex(fields);
            int cikIndex = requiredIndex(index, "cik");
            int nameIndex = requiredIndex(index, "name");
            int tickerIndex = requiredIndex(index, "ticker");
            int exchangeIndex = requiredIndex(index, "exchange");

            List<SecCompanyTickerExchangeEntry> entries = new ArrayList<>();
            for (JsonNode row : data) {
                if (!row.isArray() || row.size() <= exchangeIndex) {
                    continue;
                }

                String ticker = text(row.get(tickerIndex));
                String companyName = text(row.get(nameIndex));
                String exchange = text(row.get(exchangeIndex));
                if (ticker == null || companyName == null || exchange == null) {
                    continue;
                }

                entries.add(new SecCompanyTickerExchangeEntry(
                        cik(row.get(cikIndex)),
                        companyName,
                        ticker.toUpperCase(Locale.ROOT),
                        exchange
                ));
            }
            return entries;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SEC ticker exchange payload", e);
        }
    }

    private Map<String, Integer> buildFieldIndex(JsonNode fields) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String name = text(fields.get(i));
            if (name != null) {
                index.put(name.toLowerCase(Locale.ROOT), i);
            }
        }
        return index;
    }

    private int requiredIndex(Map<String, Integer> index, String fieldName) {
        Integer value = index.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException("SEC ticker exchange field is missing: " + fieldName);
        }
        return value;
    }

    private String cik(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return String.format("%010d", node.asLong());
        }

        String raw = text(node);
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
