package com.qaima.external;

import com.qaima.dto.finra.FinraShortSaleVolumeRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FinraShortSaleVolumeParser {

    private static final DateTimeFormatter FINRA_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String EXPECTED_HEADER = "Date|Symbol|ShortVolume|ShortExemptVolume|TotalVolume|Market";

    public List<FinraShortSaleVolumeRow> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] lines = text.replace("\uFEFF", "").split("\\R");
        if (lines.length == 0 || !EXPECTED_HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("Unsupported FINRA short sale volume header");
        }

        List<FinraShortSaleVolumeRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            String[] columns = line.split("\\|", -1);
            if (columns.length == 1 && columns[0].trim().matches("\\d+")) {
                continue;
            }
            if (columns.length != 6) {
                throw new IllegalArgumentException("Malformed FINRA short sale volume row at line " + (i + 1));
            }

            String symbol = columns[1].trim().toUpperCase(Locale.ROOT);
            if (symbol.isBlank()) {
                continue;
            }

            rows.add(new FinraShortSaleVolumeRow(
                    LocalDate.parse(columns[0].trim(), FINRA_DATE),
                    symbol,
                    decimal(columns[2]),
                    decimal(columns[3]),
                    decimal(columns[4]),
                    columns[5].trim()
            ));
        }
        return rows;
    }

    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim());
    }
}
