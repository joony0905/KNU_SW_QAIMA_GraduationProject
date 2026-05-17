package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.qaima.dto.sec.SecIssuedSharesFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SecCompanyFactsParser {

    private static final String SHARES_OUTSTANDING_TAG = "EntityCommonStockSharesOutstanding";

    public Optional<SecIssuedSharesFact> parseLatestCommonSharesOutstanding(String cik, JsonNode root) {
        List<SecIssuedSharesFact> facts = parseCommonSharesOutstanding(cik, root);
        return facts.stream()
                .max(Comparator
                        .comparing(SecIssuedSharesFact::endDate)
                        .thenComparing(fact -> nullsLast(fact.filedDate()))
                        .thenComparing(fact -> nullsLast(fact.accessionNumber())));
    }

    public List<SecIssuedSharesFact> parseCommonSharesOutstanding(String cik, JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }

        String normalizedCik = cik10(firstNonBlank(cik, root.path("cik").asText(null)));
        String entityName = trimToNull(root.path("entityName").asText(null));
        JsonNode units = root.path("facts").path("dei").path(SHARES_OUTSTANDING_TAG).path("units");
        if (!units.isObject()) {
            return List.of();
        }

        List<SecIssuedSharesFact> facts = new ArrayList<>();
        Iterator<String> unitNames = units.fieldNames();
        while (unitNames.hasNext()) {
            String unitName = unitNames.next();
            if (!"shares".equals(unitName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            JsonNode rows = units.path(unitName);
            if (!rows.isArray()) {
                continue;
            }

            for (JsonNode row : rows) {
                SecIssuedSharesFact fact = parseFact(normalizedCik, entityName, row);
                if (fact != null) {
                    facts.add(fact);
                }
            }
        }
        return facts;
    }

    private SecIssuedSharesFact parseFact(String cik, String entityName, JsonNode row) {
        LocalDate endDate = parseDate(row.path("end").asText(null));
        Long shares = parseLong(row.path("val"));
        if (cik == null || endDate == null || shares == null || shares <= 0) {
            return null;
        }

        return new SecIssuedSharesFact(
                cik,
                entityName,
                trimToNull(row.path("accn").asText(null)),
                endDate,
                parseDate(row.path("filed").asText(null)),
                trimToNull(row.path("form").asText(null)),
                parseInteger(row.path("fy")),
                trimToNull(row.path("fp").asText(null)),
                shares
        );
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        try {
            BigDecimal value = new BigDecimal(node.asText().replace(",", "").trim());
            return value.longValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer parseInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        try {
            return Integer.parseInt(node.asText().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String cik10(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    private String firstNonBlank(String first, String second) {
        String firstValue = trimToNull(first);
        return firstValue != null ? firstValue : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullsLast(String value) {
        return value == null ? "" : value;
    }

    private LocalDate nullsLast(LocalDate value) {
        return value == null ? LocalDate.MIN : value;
    }
}
