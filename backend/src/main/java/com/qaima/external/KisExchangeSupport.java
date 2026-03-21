package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisExchangeSupport {

    private final ObjectMapper objectMapper;

    /**
     * 표준 KIS 호출:
     * - status/headers/raw body 로깅
     * - HTTP 에러 분기
     * - JSON 디코딩 에러 분기
     *
     * rt_cd 분기는 별도(checkRtCd)로 분리 (DTO마다 인터페이스/구조가 다를 수 있어서)
     */
    public <T> Mono<KisRaw<T>> exchangeRaw(
            WebClient.RequestHeadersSpec<?> spec,
            String endpointName,
            Class<T> clazz
    ) {
        return spec.exchangeToMono(resp -> handle(resp, endpointName, clazz));
    }

    private <T> Mono<KisRaw<T>> handle(ClientResponse resp, String endpointName, Class<T> clazz) {
        HttpStatusCode status = resp.statusCode();
        HttpHeaders headers = resp.headers().asHttpHeaders();

        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(raw -> {

                    // status + headers + raw body
                    log.debug("[KIS RAW] endpoint={} status={} headers={} body={}",
                            endpointName, status, safeHeaders(headers), truncate(raw, 4000));

                    // HTTP 에러
                    if (!status.is2xxSuccessful()) {
                        throw new ErrorException(
                                ErrorCode.KIS_HTTP_ERROR,
                                "KIS HTTP error. endpoint=" + endpointName
                                        + " status=" + status
                                        + " body=" + truncate(raw, 800)
                        );
                    }

                    // 디코딩
                    final T parsed;
                    try {
                        parsed = objectMapper.readValue(raw, clazz);
                    } catch (Exception ex) {
                        throw new ErrorException(
                                ErrorCode.KIS_DECODE_ERROR,
                                "KIS decode error. endpoint=" + endpointName
                                        + " body=" + truncate(raw, 800)
                        );
                    }

                    return Mono.just(new KisRaw<>(parsed, raw, headers, status));
                });
    }

    private String safeHeaders(HttpHeaders h) {
        HttpHeaders copy = new HttpHeaders();
        h.forEach((k, v) -> {
            String key = k.toLowerCase();
            if (key.contains("authorization") || key.contains("appsecret") || key.contains("appkey")) return;
            copy.put(k, v);
        });
        return copy.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated)";
    }

    private <T extends com.qaima.external.KisRtHeader> T requireRtOk(
            T parsed,
            String endpointName,
            String rawBody
    ) {
        String rt = parsed.getRt_cd();
        if (rt == null || rt.isBlank() || !"0".equals(rt)) {
            String msg1 = parsed.getMsg1();

            // 시장 닫힘 감지(패턴은 운영하면서 msg1 보고 확정)
            if (msg1 != null && (msg1.contains("장") && msg1.contains("시간"))) {
                throw new ErrorException(ErrorCode.KIS_MARKET_CLOSED,
                        "KIS market closed. endpoint=" + endpointName + " msg1=" + msg1);
            }

            throw new ErrorException(
                    ErrorCode.KIS_BIZ_ERROR,
                    "KIS biz error. endpoint=" + endpointName
                            + " rt_cd=" + rt
                            + " msg_cd=" + parsed.getMsg_cd()
                            + " msg1=" + parsed.getMsg1()
                            + " body=" + (rawBody == null ? "" : rawBody.substring(0, Math.min(rawBody.length(), 800)))
            );
        }
        return parsed;
    }

    /** raw + parsed를 같이 들고 다니는 래퍼 */
    public record KisRaw<T>(T parsed, String rawBody, HttpHeaders headers, HttpStatusCode status) {}
}
