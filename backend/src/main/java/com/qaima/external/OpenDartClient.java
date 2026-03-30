package com.qaima.external;

import com.qaima.common.Blocking;
import com.qaima.dto.opendart.OpenDartStockTotalStatusResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class OpenDartClient {

    public static final String STATUS_OK = "000";
    public static final String STATUS_NO_DATA = "013";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final WebClient webClient;

    @Value("${opendart.api-key:${OPENDART_API_KEY:}}")
    private String apiKey;

    public OpenDartClient(@Qualifier("opendartWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<OpenDartStockTotalStatusResponse.Row>> fetchStockTotalStatus(
            String corpCode,
            int businessYear,
            String reportCode
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("opendart.api-key is missing"));
        }
        if (corpCode == null || corpCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("corpCode is required"));
        }
        if (reportCode == null || reportCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("reportCode is required"));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stockTotqySttus.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", businessYear)
                        .queryParam("reprt_code", reportCode)
                        .build())
                .retrieve()
                .bodyToMono(OpenDartStockTotalStatusResponse.class)
                .flatMap(response -> {
                    String status = safeTrim(response.getStatus());
                    if (STATUS_OK.equals(status)) {
                        List<OpenDartStockTotalStatusResponse.Row> rows =
                                response.getList() == null ? List.of() : response.getList();
                        log.info("[OpenDART] stock total status fetched. corpCode={}, businessYear={}, reportCode={}, rows={}",
                                corpCode, businessYear, reportCode, rows.size());
                        return Mono.just(rows);
                    }

                    if (STATUS_NO_DATA.equals(status)) {
                        log.info("[OpenDART] stock total status empty. corpCode={}, businessYear={}, reportCode={}, message={}",
                                corpCode, businessYear, reportCode, response.getMessage());
                        return Mono.just(List.of());
                    }

                    return Mono.error(new IllegalStateException(
                            "OpenDART stock total status failed. status=" + status + ", message=" + response.getMessage()
                    ));
                });
    }

    public Mono<List<OpenDartStockTotalStatusResponse.Row>> fetchStockTotalStatus(
            String corpCode,
            int businessYear
    ) {
        return fetchStockTotalStatus(corpCode, businessYear, "11011");
    }

    public Mono<List<CorpCodeEntry>> fetchCorpCodes() {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("opendart.api-key is missing"));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/corpCode.xml")
                        .queryParam("crtfc_key", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(bytes -> Blocking.call(() -> parseCorpCodes(bytes)))
                .doOnNext(entries -> log.info("[OpenDART] corp codes fetched. entries={}", entries.size()));
    }

    private List<CorpCodeEntry> parseCorpCodes(byte[] zipBytes) throws Exception {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalStateException("OpenDART corpCode response is empty");
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && name.toLowerCase().endsWith(".xml")) {
                    return parseCorpCodeXml(zipInputStream);
                }
            }
        }

        throw new IllegalStateException("OpenDART corpCode zip does not contain xml");
    }

    private List<CorpCodeEntry> parseCorpCodeXml(InputStream xmlInputStream) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        List<CorpCodeEntry> results = new ArrayList<>();
        XMLStreamReader reader = factory.createXMLStreamReader(xmlInputStream);

        String corpCode = null;
        String corpName = null;
        String stockCode = null;
        LocalDate modifyDate = null;
        String currentElement = null;

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentElement = reader.getLocalName();
                    if ("list".equals(currentElement)) {
                        corpCode = null;
                        corpName = null;
                        stockCode = null;
                        modifyDate = null;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (currentElement == null) {
                        continue;
                    }

                    String text = reader.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }

                    String normalized = text.trim();
                    switch (currentElement) {
                        case "corp_code" -> corpCode = normalized;
                        case "corp_name" -> corpName = normalized;
                        case "stock_code" -> stockCode = normalized;
                        case "modify_date" -> modifyDate = LocalDate.parse(normalized, BASIC_DATE);
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("list".equals(localName)) {
                        results.add(new CorpCodeEntry(corpCode, corpName, blankToNull(stockCode), modifyDate));
                    }
                    currentElement = null;
                }
            }
        } finally {
            try {
                reader.close();
            } catch (Exception e) {
                log.debug("[OpenDART] failed to close XML reader cleanly", e);
            }
        }

        return results;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CorpCodeEntry(
            String corpCode,
            String corpName,
            String stockCode,
            LocalDate modifyDate
    ) {
    }
}
