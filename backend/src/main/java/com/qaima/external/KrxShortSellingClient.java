package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qaima.dto.krx.KrxShortSellingMarket;
import com.qaima.dto.krx.KrxShortSellingRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class KrxShortSellingClient {

    private static final DateTimeFormatter KRX_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String BLD = "dbms/MDC_OUT/STAT/srt/MDCSTAT30101_OUT";
    private static final String SCREEN_ID = "MDCSTAT301";
    private static final String REFERER = "https://data.krx.co.kr/comm/srt/srtLoader/index.cmd?screenId=" + SCREEN_ID;
    private static final List<String> REQUIRED_FIELDS = List.of(
            "ISU_CD",
            "ISU_ABBRV",
            "SECUGRP_NM",
            "CVSRTSELL_TRDVOL",
            "UPTICKRULE_APPL_TRDVOL",
            "UPTICKRULE_EXCPT_TRDVOL",
            "ACC_TRDVOL",
            "TRDVOL_WT",
            "CVSRTSELL_TRDVAL",
            "UPTICKRULE_APPL_TRDVAL",
            "UPTICKRULE_EXCPT_TRDVAL",
            "ACC_TRDVAL",
            "TRDVAL_WT"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public KrxShortSellingClient(
            @Qualifier("krxWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Mono<List<KrxShortSellingRow>> fetchDaily(LocalDate date, KrxShortSellingMarket market) {
        if (date == null) {
            return Mono.error(new IllegalArgumentException("date is required"));
        }
        if (market == null) {
            return Mono.error(new IllegalArgumentException("market is required"));
        }

        long startedAt = System.currentTimeMillis();
        log.info("[KRX] short selling fetch start. date={}, market={}, bld={}",
                date, market.exchangeCode(), BLD);

        return webClient.post()
                .uri("/comm/bldAttendant/getJsonData.cmd")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.REFERER, REFERER)
                .header(HttpHeaders.ORIGIN, "https://data.krx.co.kr")
                .header("X-Requested-With", "XMLHttpRequest")
                .body(BodyInserters.fromFormData("bld", BLD)
                        .with("trdDd", KRX_DATE.format(date))
                        .with("mktId", market.krxMarketId())
                        .with("inqCond", "STMFRTSCIFDRFS")
                        .with("share", "1")
                        .with("money", "1")
                        .with("csvxls_isNo", "false"))
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    String contentType = response.headers().contentType()
                            .map(MediaType::toString)
                            .orElse("");
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                if (!response.statusCode().is2xxSuccessful()) {
                                    log.error("[KRX] short selling HTTP error. date={}, market={}, status={}, contentType={}, bld={}, bodySample={}",
                                            date, market.exchangeCode(), status, contentType, BLD, sample(body));
                                    return Mono.error(error(
                                            "KRX_HTTP_ERROR",
                                            "KRX short selling HTTP error. status=" + status
                                    ));
                                }
                                if (isLogoutResponse(body)) {
                                    log.error("[KRX] short selling blocked by LOGOUT response. date={}, market={}, status={}, contentType={}, bld={}, bodySample={}",
                                            date, market.exchangeCode(), status, contentType, BLD, sample(body));
                                    return Mono.error(error(
                                            "KRX_LOGOUT_RESPONSE",
                                            "KRX short selling endpoint returned LOGOUT"
                                    ));
                                }
                                if (isHtmlResponse(body)) {
                                    log.error("[KRX] short selling returned HTML instead of JSON. date={}, market={}, status={}, contentType={}, bld={}, bodySample={}",
                                            date, market.exchangeCode(), status, contentType, BLD, sample(body));
                                    return Mono.error(error(
                                            "KRX_HTML_RESPONSE",
                                            "KRX short selling endpoint returned HTML instead of JSON"
                                    ));
                                }

                                try {
                                    List<KrxShortSellingRow> rows = parse(date, market, body);
                                    long elapsedMs = System.currentTimeMillis() - startedAt;
                                    log.info("[KRX] short selling fetch success. date={}, market={}, status={}, contentType={}, rows={}, elapsedMs={}",
                                            date, market.exchangeCode(), status, contentType, rows.size(), elapsedMs);
                                    return Mono.just(rows);
                                } catch (KrxShortSellingClientException e) {
                                    log.error("[KRX] short selling parse failed. date={}, market={}, errorCode={}, message={}, bld={}, bodySample={}",
                                            date, market.exchangeCode(), e.code(), e.getMessage(), BLD, sample(body), e);
                                    return Mono.error(e);
                                } catch (Exception e) {
                                    log.error("[KRX] short selling parse failed. date={}, market={}, errorCode=KRX_PARSE_ERROR, message={}, bld={}, bodySample={}",
                                            date, market.exchangeCode(), e.getMessage(), BLD, sample(body), e);
                                    return Mono.error(error("KRX_PARSE_ERROR", e.getMessage(), e));
                                }
                            });
                })
                .onErrorMap(error -> {
                    if (error instanceof KrxShortSellingClientException) {
                        return error;
                    }
                    log.error("[KRX] short selling request failed. date={}, market={}, errorCode=KRX_REQUEST_FAILED, message={}, bld={}",
                            date, market.exchangeCode(), error.getMessage(), BLD, error);
                    return error(
                            "KRX_REQUEST_FAILED",
                            "KRX short selling request failed: " + error.getMessage(),
                            error
                    );
                });
    }

    private List<KrxShortSellingRow> parse(LocalDate date, KrxShortSellingMarket market, String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode outBlock = root.path("OutBlock_1");
            if (outBlock.isMissingNode() || outBlock.isNull()) {
                throw error("KRX_RESPONSE_SCHEMA_CHANGED", "KRX response has no OutBlock_1");
            }
            if (!outBlock.isArray()) {
                throw error("KRX_RESPONSE_SCHEMA_CHANGED", "KRX response OutBlock_1 is not an array");
            }

            List<KrxShortSellingRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (JsonNode node : outBlock) {
                rowIndex++;
                validateRequiredFields(node, rowIndex);
                String stockCode = text(node, "ISU_CD");
                if (stockCode == null || stockCode.isBlank()) {
                    continue;
                }

                rows.add(new KrxShortSellingRow(
                        date,
                        stockCode.trim().toUpperCase(),
                        text(node, "ISU_ABBRV"),
                        market.exchangeCode(),
                        text(node, "SECUGRP_NM"),
                        decimal(node, "CVSRTSELL_TRDVOL"),
                        decimal(node, "UPTICKRULE_APPL_TRDVOL"),
                        decimal(node, "UPTICKRULE_EXCPT_TRDVOL"),
                        decimal(node, "ACC_TRDVOL"),
                        decimal(node, "TRDVOL_WT"),
                        decimal(node, "CVSRTSELL_TRDVAL"),
                        decimal(node, "UPTICKRULE_APPL_TRDVAL"),
                        decimal(node, "UPTICKRULE_EXCPT_TRDVAL"),
                        decimal(node, "ACC_TRDVAL"),
                        decimal(node, "TRDVAL_WT")
                ));
            }
            return rows;
        } catch (JsonProcessingException e) {
            throw error("KRX_RESPONSE_NOT_JSON", "KRX response is not valid JSON", e);
        } catch (Exception e) {
            if (e instanceof KrxShortSellingClientException clientException) {
                throw clientException;
            }
            throw error("KRX_PARSE_ERROR", "Failed to parse KRX short selling response", e);
        }
    }

    private void validateRequiredFields(JsonNode node, int rowIndex) {
        List<String> missing = REQUIRED_FIELDS.stream()
                .filter(field -> !node.has(field))
                .toList();
        if (!missing.isEmpty()) {
            throw error(
                    "KRX_COLUMN_MISSING",
                    "KRX response row " + rowIndex + " missing columns: " + String.join(",", missing)
            );
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw error(
                    "KRX_DECIMAL_PARSE_ERROR",
                    "KRX numeric column parse failed. field=" + fieldName + ", value=" + value,
                    e
            );
        }
    }

    private boolean isLogoutResponse(String body) {
        return body != null && body.trim().equalsIgnoreCase("LOGOUT");
    }

    private boolean isHtmlResponse(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("<!DOCTYPE html")
                || trimmed.startsWith("<html")
                || trimmed.startsWith("<script");
    }

    private String sample(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }

    private KrxShortSellingClientException error(String code, String message) {
        return new KrxShortSellingClientException(code, message);
    }

    private KrxShortSellingClientException error(String code, String message, Throwable cause) {
        return new KrxShortSellingClientException(code, message, cause);
    }

    public static class KrxShortSellingClientException extends RuntimeException {
        private final String code;

        public KrxShortSellingClientException(String code, String message) {
            super(message);
            this.code = code;
        }

        public KrxShortSellingClientException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
