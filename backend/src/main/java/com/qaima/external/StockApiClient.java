package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Exchange;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.kis.KisSearchInfoResponseDto;
import com.qaima.dto.kis.KisTickerMetaDto;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class StockApiClient implements StockClient {

    private final KrStockClient krClient;
    private final GlobalStockClient globalClient;

    private static final Duration KIS_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MARKETSTACK_TIMEOUT = Duration.ofSeconds(4);

    /* =========================
       StockDto (realtime/quote)
       - inquire-price(시세) 조회
       - search-info 절대 호출 금지
       ========================= */

    @Override
    public Mono<StockDto> fetchStock(Stock stock) {
        final String canonical = canonicalizeStockCode(stock.getStockCode());

        Mono<StockDto> fromKis = krClient
                .fetchStockByCanonical(canonical, stock.getExchange())
                .timeout(KIS_TIMEOUT)
                .onErrorResume(ex -> Mono.empty());

        Mono<StockDto> fromGlobal = globalClient
                .fetchStockByMkstackCode(mkstackCodeOf(canonical))
                .timeout(MARKETSTACK_TIMEOUT)
                .onErrorResume(ex -> Mono.empty());

        return fromKis.switchIfEmpty(fromGlobal)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "KIS/Marketstack 모두에서 종목 정보를 가져오지 못했습니다: " + canonical
                )))
                .map(quote -> mergeStaticFromDb(stock, quote));
    }

    private StockDto mergeStaticFromDb(Stock stock, StockDto quote) {
        return StockDto.builder()
                // ===== static (DB) =====
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName()) //DB
                .exchangeId(stock.getExchange() != null ? stock.getExchange().getExchangeId() : null)
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(stock.getIndustry() != null ? stock.getIndustry().getIndustryId() : null)
                .listedAt(stock.getListedAt())
                .delistedAt(stock.getDelistedAt())

                // ===== realtime (quote) =====
                .price(quote.getPrice())
                .changeRate(quote.getChangeRate())
                .build();
    }

    /* =========================
       StockMeta (create/load)
       - inquire-price 1회 (cache)
       - search-info 0~1회 (soft fail)
       ========================= */

    @Override
    public Mono<ApiResponse<StockMeta>> fetchTickerMeta(String rawStockCode) {
        final String canonical = canonicalizeStockCode(rawStockCode);

        if (canonical == null || canonical.isBlank()) {
            return Mono.just(ApiResponse.internalError(
                    "INVALID_STOCK_CODE",
                    "[fetchTickerMeta] 종목코드가 비어있습니다."
            ));
        }

        final boolean isKorean = canonical.matches("^[0-9]{6}$");
        final String mkstackCode = mkstackCodeOf(canonical);

        // 해외면 Marketstack만
        if (!isKorean) {
            return globalClient.fetchTickerMetaByMkstackCode(mkstackCode)
                    .timeout(MARKETSTACK_TIMEOUT)
                    .map(data -> ApiResponse.success(toStockMetaFromMarketstack(data, canonical)))
                    .switchIfEmpty(Mono.just(ApiResponse.internalError(
                            "META_NOT_FOUND",
                            "[fetchTickerMeta] 티커 메타 정보를 가져오지 못했습니다: " + canonical
                    )))
                    .onErrorResume(ex -> Mono.just(ApiResponse.internalError(
                            "META_INTERNAL_ERROR",
                            safe(ex.getMessage())
                    )));
        }

        // ===== 국내: inquire-price 1회 + (옵션) search-info 1회 =====
        Mono<KisTickerMetaDto> priceMono = krClient.fetchTickerMeta(canonical)
                .timeout(KIS_TIMEOUT)
                .cache();

        Mono<KisSearchInfoResponseDto.Output> infoMono = priceMono
                .map(meta -> {
                    Exchange ex = new Exchange();
                    ex.setCode(meta.getExchangeCode()); // KOSPI/KOSDAQ/KONEX
                    return ex;
                })
                .map(this::toKisMarketDivCode) // J/Q/K
                .flatMap(marketDiv -> krClient.fetchSearchInfoRaw(canonical, marketDiv))
                .timeout(KIS_TIMEOUT)
                .onErrorResume(ErrorException.class, e -> {
                    if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);
                    log.warn("[StockApiClient] search-info soft-fail. code={}, ec={}, msg={}",
                            canonical, e.getErrorCode().code(), e.getMessage());
                    return Mono.just(new KisSearchInfoResponseDto.Output());
                })
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] search-info soft-fail. code={}, cause={}",
                            canonical, safe(ex.getMessage()));
                    return Mono.just(new KisSearchInfoResponseDto.Output());
                });

        Mono<StockMeta> fromKis = Mono.zip(priceMono, infoMono.defaultIfEmpty(new KisSearchInfoResponseDto.Output()))
                .map(t -> toStockMetaFromKis(t.getT1(), t.getT2(), canonical))
                .onErrorResume(ErrorException.class, e -> {
                    if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);
                    return Mono.empty();
                })
                .onErrorResume(ex -> Mono.empty());

        Mono<StockMeta> fromGlobal = globalClient.fetchTickerMetaByMkstackCode(mkstackCode)
                .timeout(MARKETSTACK_TIMEOUT)
                .map(data -> toStockMetaFromMarketstack(data, canonical))
                .onErrorResume(ex -> Mono.empty());

        return fromKis.switchIfEmpty(fromGlobal)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.internalError(
                        "META_NOT_FOUND",
                        "[fetchTickerMeta] 티커 메타 정보를 가져오지 못했습니다: " + canonical
                )))
                .onErrorResume(ex -> Mono.just(ApiResponse.internalError(
                        "META_INTERNAL_ERROR",
                        safe(ex.getMessage())
                )));
    }

    @Override
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }

    @Override
    public Mono<CandleFetchResult> fetchCandles(Stock stock, Freq freq, OffsetDateTime from, OffsetDateTime to) {
        final String canonical = canonicalizeStockCode(stock.getStockCode());
        final String marketDivCode = toKisMarketDivCode(stock.getExchange());
        final String mkstackCode = mkstackCodeOf(canonical);

        Mono<List<PriceOhlcvDto>> fromKis = krClient
                .fetchCandles(canonical, marketDivCode, freq, from, to)
                .timeout(KIS_TIMEOUT)
                .onErrorResume(ErrorException.class, e -> {
                    if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);
                    return Mono.empty();
                })
                .onErrorResume(ex -> Mono.empty());

        Mono<List<PriceOhlcvDto>> fromGlobal = globalClient
                .fetchCandlesByMkstackCode(mkstackCode, freq, from, to)
                .timeout(MARKETSTACK_TIMEOUT)
                .onErrorResume(ex -> Mono.empty());

        return fromKis.map(list -> new CandleFetchResult(list, CandleSource.KIS))
                .switchIfEmpty(fromGlobal.map(list -> new CandleFetchResult(list, CandleSource.MARKETSTACK)))
                .switchIfEmpty(Mono.just(new CandleFetchResult(List.of(), CandleSource.EMPTY)));
    }

    /* ========================= helpers ========================= */

    private String canonicalizeStockCode(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toUpperCase();
        int dotIdx = clean.indexOf('.');
        return (dotIdx > 0) ? clean.substring(0, dotIdx) : clean;
    }

    private String mkstackCodeOf(String canonical) {
        if (canonical == null) return null;
        String c = canonical.trim().toUpperCase();
        if (c.matches("^[0-9]{6}$")) return c + ".XKRX";
        return c;
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null) return "J";
        String code = exchange.getCode();
        if (code == null || code.isBlank()) return "J";

        return switch (code) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            case "KRX" -> "J";
            default -> "J";
        };
    }

    private StockMeta toStockMetaFromKis(
            KisTickerMetaDto priceMeta,
            KisSearchInfoResponseDto.Output info,
            String canonicalStockCode
    ) {
        String prdtName = (info != null) ? info.getPrdt_name() : null;

        //companyName 확정로직
        String companyName =
                (info.getPrdt_abrv_name() != null && !info.getPrdt_abrv_name().isBlank())
                        ? info.getPrdt_abrv_name()
                        : (prdtName != null && !prdtName.isBlank())
                        ? prdtName
                        : (priceMeta != null ? priceMeta.getCompanyName() : canonicalStockCode);

        String kospi200Yn = (info != null) ? info.getKospi200_item_yn() : null;

        return StockMeta.builder()
                .stockCode(canonicalStockCode)
                .companyName(companyName)
                .exchangeCode(priceMeta != null ? priceMeta.getExchangeCode() : null)
                .countryCode("KR")
                .currency(priceMeta != null ? priceMeta.getCurrency() : "KRW")
                .price(priceMeta != null ? priceMeta.getPrice() : null)
                .changeRate(priceMeta != null ? priceMeta.getChangeRate() : null)
                .source("KIS")

                // search-info soft-fail이면 null 가능
                .sectorCode(info != null ? info.getIdx_bztp_mcls_cd() : null)
                .sectorName(info != null ? info.getIdx_bztp_mcls_cd_name() : null)
                .industryCode(info != null ? info.getIdx_bztp_scls_cd() : null)
                .industryName(info != null ? info.getIdx_bztp_scls_cd_name() : null)
                .kospi200("Y".equalsIgnoreCase(kospi200Yn))
                .build();
    }

    private StockMeta toStockMetaFromMarketstack(
            MarketStackTickersResponse.TickerData data,
            String canonicalStockCode
    ) {
        MarketStackTickersResponse.StockExchange ex = data != null ? data.getStock_exchange() : null;

        String exchangeCode = null;
        String countryCode = null;
        if (ex != null) {
            exchangeCode = ex.getAcronym() != null ? ex.getAcronym() : ex.getMic();
            countryCode = ex.getCountry_code();
        }

        return StockMeta.builder()
                .stockCode(canonicalStockCode)
                .companyName(data != null ? data.getName() : canonicalStockCode)
                .exchangeCode(normalizeExchangeCode(exchangeCode))
                .countryCode(countryCode)
                .currency(null)
                .price(data != null ? data.getPrice() : null)
                .changeRate(data != null ? data.getChangeRate() : null)
                .source("MARKETSTACK")
                .build();
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;
        String c = exchangeCode.trim().toUpperCase().replace(" ", "");
        return switch (c) {
            case "KOSPI" -> "KOSPI";
            case "KOSDAQ" -> "KOSDAQ";
            case "KONEX" -> "KONEX";
            case "KRX", "XKRX" -> "KRX";
            case "NASDAQ", "XNAS" -> "NASDAQ";
            case "NYSE", "XNYS" -> "NYSE";
            default -> c;
        };
    }

    private String safe(String msg) {
        return (msg == null || msg.isBlank()) ? "n/a" : msg;
    }
}
