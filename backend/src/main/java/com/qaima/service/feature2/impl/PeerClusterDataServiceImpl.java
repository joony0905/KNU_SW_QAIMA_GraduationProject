package com.qaima.service.feature2.impl;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.Stock;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.repository.IndustryIndexMapRepository;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.feature2.PeerClusterDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerClusterDataServiceImpl implements PeerClusterDataService {

    private final StockRepository stockRepository;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final IndustryIndexMapRepository industryIndexMapRepository;
    private final IndustryIndexOhlcvRepository industryIndexOhlcvRepository;

    // 휴장/결측 대비로 range를 넉넉히 당겨서 가져온 뒤, 각 종목별로 tail(window)로 자른다.
    private static final int RANGE_MULTIPLIER = 3;

    // 너무 큰 산업이면 비용 폭발 방지 (MVP 상한)
    private static final int MAX_UNIVERSE = 300;

    // 시계열이 너무 짧으면 계산 의미 없음
    private static final int MIN_POINTS = 30;

    @Override
    public Mono<Map<String, Object>> buildPack(PeerClusterRequestDto req) {
        return Mono.fromCallable(() -> safeBuild(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> safeBuild(PeerClusterRequestDto req) {
        final List<String> warnings = new ArrayList<>();

        try {
            log.info("[PeerClusterData] buildPack start req={}", req);

            // -----------------------
            // 1) Input guard
            // -----------------------
            if (req == null) {
                warnings.add("REQ_NULL");
                log.warn("[PeerClusterData] req is null");
                return emptyPack(warnings);
            }

            final Long industryId = req.getIndustryId();
            final String anchor = safeTrim(req.getAnchorStockCode());
            final Freq freq = req.getFreq();
            final Integer windowObj = req.getWindow();
            final int window = (windowObj == null) ? 0 : windowObj;

            if (industryId == null || industryId <= 0) warnings.add("BAD_INDUSTRY_ID");
            if (anchor == null || anchor.isBlank()) warnings.add("BAD_ANCHOR_STOCK_CODE");
            if (freq == null) warnings.add("BAD_FREQ");
            if (window < 30) warnings.add("BAD_WINDOW");

            if (!warnings.isEmpty()) {
                log.warn("[PeerClusterData] bad request warnings={}", warnings);
                return emptyPack(warnings);
            }

            // -----------------------
            // 2) Industry members
            // -----------------------
            List<Stock> stocks;
            try {
                stocks = stockRepository.findAllByIndustryIndustryId(industryId);
            } catch (Exception e) {
                log.error("[PeerClusterData] stockRepository failed", e);
                warnings.add("STOCK_REPO_FAILED");
                warnings.add(e.getClass().getSimpleName());
                return emptyPack(warnings);
            }

            if (stocks == null || stocks.isEmpty()) {
                warnings.add("INDUSTRY_MEMBERS_EMPTY");
                log.warn("[PeerClusterData] no stocks for industryId={}", industryId);
                return emptyPack(warnings);
            }

            if (stocks.size() > MAX_UNIVERSE) {
                warnings.add("UNIVERSE_CAPPED:" + MAX_UNIVERSE);
                stocks = capUniverseKeepingAnchor(stocks, anchor);
            }

            List<String> allCodes = stocks.stream()
                    .map(Stock::getStockCode)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .collect(Collectors.toList());

            if (allCodes.isEmpty()) {
                warnings.add("MEMBERS_EMPTY_AFTER_FILTER");
                log.warn("[PeerClusterData] allCodes empty after filter");
                return emptyPack(warnings);
            }

            if (!allCodes.contains(anchor)) {
                warnings.add("ANCHOR_NOT_IN_MEMBERS");
            }

            log.info("[PeerClusterData] stocks size={}", stocks.size());
            log.info("[PeerClusterData] allCodes size={}", allCodes.size());

            // -----------------------
            // 3) Bulk range fetch
            // -----------------------
            final OffsetDateTime to = OffsetDateTime.now();
            final OffsetDateTime from = calcFrom(to, freq, window);

            List<PriceOhlcv> rows;
            try {
                rows = priceOhlcvRepository.findRangeBulk(allCodes, freq, from, to);
            } catch (Exception e) {
                log.error("[PeerClusterData] price bulk fetch failed", e);
                warnings.add("PRICE_BULK_FETCH_FAILED");
                warnings.add(e.getClass().getSimpleName());
                return packMembersMetaOnly(stocks, warnings);
            }

            if (rows == null || rows.isEmpty()) {
                warnings.add("PRICE_ROWS_EMPTY");
                log.warn("[PeerClusterData] rows empty. industryId={}, freq={}, from={}, to={}",
                        industryId, freq, from, to);
                return packMembersMetaOnly(stocks, warnings);
            }

            log.info("[PeerClusterData] rows size={}", rows.size());

            // group by stock_code
            Map<String, List<PriceOhlcv>> byCode = new HashMap<>();
            for (PriceOhlcv p : rows) {
                try {
                    String code = safeStockCode(p);
                    if (code == null) continue;
                    byCode.computeIfAbsent(code, k -> new ArrayList<>()).add(p);
                } catch (Exception e) {
                    log.error("[PeerClusterData] stock access failed while grouping", e);
                    warnings.add("GROUP_BY_CODE_FAILED");
                    warnings.add(e.getClass().getSimpleName());
                }
            }

            log.info("[PeerClusterData] byCode size={}", byCode.size());

            // -----------------------
            // 4) Build response lists
            // -----------------------
            List<Map<String, Object>> metas = new ArrayList<>();
            List<Map<String, Object>> prices = new ArrayList<>();
            List<Map<String, Object>> liquidity = new ArrayList<>();
            List<String> members = new ArrayList<>();
            Map<String, Object> industryIndex = industryIndexItem(industryId, freq, window, warnings);

            for (Stock s : stocks) {
                String code = (s == null) ? null : safeTrim(s.getStockCode());
                if (code == null || code.isBlank()) continue;

                // metas는 항상 넣기
                metas.add(metaItem(code, safeCompanyName(s)));

                List<PriceOhlcv> rawSeries = byCode.get(code);
                if (rawSeries == null || rawSeries.isEmpty()) {
                    continue;
                }

                log.debug("[PeerClusterData] code={}, rawSeriesSize={}", code, rawSeries.size());

                // 정렬 전에 null ts 제거
                List<PriceOhlcv> series = rawSeries.stream()
                        .filter(Objects::nonNull)
                        .filter(p -> safeTs(p) != null)
                        .filter(p -> safeClose(p) != null)
                        .sorted(Comparator.comparing(PeerClusterDataServiceImpl::safeTs))
                        .collect(Collectors.toList());

                if (series.isEmpty()) {
                    warnings.add("SERIES_TS_EMPTY:" + code);
                    continue;
                }

                // tail(window) 적용
                List<PriceOhlcv> cut = tail(series, window);

                if (cut.size() < MIN_POINTS) {
                    warnings.add("SERIES_TOO_SHORT:" + code);
                    continue;
                }

                // close가 전부 0이면 의미 없음 (NULL→0 대체 케이스)
                if (isAllZeroClose(cut)) {
                    warnings.add("CLOSE_ALL_ZERO:" + code);
                    continue;
                }

                members.add(code);
                prices.add(priceItem(code, cut));
                liquidity.add(liquidityItem(code, cut));
            }

            if (members.isEmpty()) {
                warnings.add("NO_USABLE_SERIES");
            }

            Map<String, Object> pack = new LinkedHashMap<>();
            pack.put("warnings", warnings);
            pack.put("members", members);
            pack.put("metas", metas);
            pack.put("prices", prices);
            pack.put("liquidity", liquidity);
            pack.put("industry_index", industryIndex);

            log.info("[PeerClusterData] buildPack success members={}, metas={}, prices={}, liquidity={}, warnings={}",
                    members.size(), metas.size(), prices.size(), liquidity.size(), warnings.size());

            return pack;

        } catch (Exception e) {
            log.error("[PeerClusterData] unexpected failure", e);
            warnings.add("PEERCLUSTER_DATA_INTERNAL_ERROR");
            warnings.add(e.getClass().getSimpleName());
            return emptyPack(warnings);
        }
    }

    private static OffsetDateTime calcFrom(OffsetDateTime to, Freq freq, int window) {
        int span = Math.max(window * RANGE_MULTIPLIER, window + 30);
        return switch (freq) {
            case ONE_D -> to.minusDays(span);
            case ONE_W -> to.minusWeeks(span);
            default -> to.minusDays(span);
        };
    }

    private static List<PriceOhlcv> tail(List<PriceOhlcv> ascSeries, int window) {
        if (ascSeries == null) return List.of();
        if (window <= 0) return ascSeries;
        if (ascSeries.size() <= window) return ascSeries;
        return ascSeries.subList(ascSeries.size() - window, ascSeries.size());
    }

    private static List<Stock> capUniverseKeepingAnchor(List<Stock> stocks, String anchor) {
        if (stocks == null || stocks.size() <= MAX_UNIVERSE) {
            return stocks;
        }

        List<Stock> capped = new ArrayList<>(MAX_UNIVERSE);
        Stock anchorStock = null;

        for (Stock stock : stocks) {
            String code = stock == null ? null : safeTrim(stock.getStockCode());
            if (anchor != null && anchor.equals(code)) {
                anchorStock = stock;
                break;
            }
        }

        if (anchorStock != null) {
            capped.add(anchorStock);
        }

        for (Stock stock : stocks) {
            if (stock == null) {
                continue;
            }
            if (anchorStock != null && stock == anchorStock) {
                continue;
            }
            capped.add(stock);
            if (capped.size() >= MAX_UNIVERSE) {
                break;
            }
        }

        return capped;
    }

    private static boolean isAllZeroClose(List<PriceOhlcv> cut) {
        for (PriceOhlcv p : cut) {
            if (toDouble(safeClose(p)) != 0.0) return false;
        }
        return true;
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String safeCompanyName(Stock s) {
        try {
            String name = s.getCompanyName();
            return name == null ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeStockCode(PriceOhlcv p) {
        try {
            if (p == null || p.getStock() == null) return null;
            String code = p.getStock().getStockCode();
            return code == null ? null : code.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static OffsetDateTime safeTs(PriceOhlcv p) {
        try {
            if (p == null || p.getId() == null) return null;
            return p.getId().getTs();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal safeClose(PriceOhlcv p) {
        try {
            return p == null ? null : p.getClose();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal safeVolume(PriceOhlcv p) {
        try {
            return p == null ? null : p.getVolume();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> metaItem(String stockCode, String companyName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stock_code", stockCode);
        m.put("company_name", companyName);
        return m;
    }

    private static Map<String, Object> priceItem(String stockCode, List<PriceOhlcv> cut) {
        List<String> dates = new ArrayList<>(cut.size());
        List<Double> close = new ArrayList<>(cut.size());

        for (PriceOhlcv p : cut) {
            OffsetDateTime ts = safeTs(p);
            if (ts == null) continue;
            dates.add(ts.toInstant().toString());
            close.add(toDouble(safeClose(p)));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stock_code", stockCode);
        out.put("dates", dates);
        out.put("close", close);
        return out;
    }

    private static Map<String, Object> liquidityItem(String stockCode, List<PriceOhlcv> cut) {
        List<String> dates = new ArrayList<>(cut.size());
        List<Double> volume = new ArrayList<>(cut.size());
        List<Double> turnover = new ArrayList<>(cut.size());

        for (PriceOhlcv p : cut) {
            OffsetDateTime ts = safeTs(p);
            if (ts == null) continue;
            dates.add(ts.toInstant().toString());

            double v = toDouble(safeVolume(p));
            double c = toDouble(safeClose(p));

            volume.add(v);
            turnover.add(c * v);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stock_code", stockCode);
        out.put("dates", dates);
        out.put("turnover", turnover);
        out.put("volume", volume);
        return out;
    }

    private Map<String, Object> industryIndexItem(Long industryId, Freq freq, int window, List<String> warnings) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (industryId == null || freq == null || window <= 0) {
            warnings.add("INDUSTRY_INDEX_SERIES_MISSING");
            return out;
        }

        try {
            return industryIndexMapRepository.findFirstByIdIndustryId(industryId)
                    .map(map -> {
                        Long indexId = map.getIndustryIndex() == null ? null : map.getIndustryIndex().getIndexId();
                        String code = map.getIndustryIndex() == null ? null : map.getIndustryIndex().getCode();
                        String name = map.getIndustryIndex() == null ? null : map.getIndustryIndex().getName();
                        if (indexId == null) {
                            warnings.add("INDUSTRY_INDEX_SERIES_MISSING");
                            return out;
                        }

                        List<IndustryIndexOhlcv> rows = industryIndexOhlcvRepository.findRecent(
                                indexId,
                                freq,
                                PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "id.ts"))
                        );

                        List<IndustryIndexOhlcv> asc = rows == null ? List.of() : rows.stream()
                                .filter(Objects::nonNull)
                                .filter(row -> row.getId() != null && row.getId().getTs() != null)
                                .filter(row -> row.getClose() != null)
                                .sorted(Comparator.comparing(row -> row.getId().getTs()))
                                .collect(Collectors.toList());

                        if (asc.size() < MIN_POINTS) {
                            warnings.add("INDUSTRY_INDEX_SERIES_MISSING");
                            return out;
                        }

                        List<String> dates = new ArrayList<>(asc.size());
                        List<Double> close = new ArrayList<>(asc.size());
                        for (IndustryIndexOhlcv row : asc) {
                            dates.add(row.getId().getTs().toInstant().toString());
                            close.add(toDouble(row.getClose()));
                        }

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("code", code == null ? "INDUSTRY_INDEX" : code);
                        item.put("name", name);
                        item.put("dates", dates);
                        item.put("close", close);
                        return item;
                    })
                    .orElseGet(() -> {
                        warnings.add("INDUSTRY_INDEX_SERIES_MISSING");
                        return out;
                    });
        } catch (Exception e) {
            log.warn("[PeerClusterData] industry index lookup failed. industryId={}, cause={}", industryId, e.getMessage(), e);
            warnings.add("INDUSTRY_INDEX_SERIES_MISSING");
            return out;
        }
    }

    private static double toDouble(BigDecimal v) {
        if (v == null) return 0.0;
        try {
            return v.doubleValue();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static Map<String, Object> emptyPack(List<String> warnings) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("warnings", warnings);
        pack.put("members", List.of());
        pack.put("metas", List.of());
        pack.put("prices", List.of());
        pack.put("liquidity", List.of());
        pack.put("industry_index", Map.of());
        return pack;
    }

    private static Map<String, Object> packMembersMetaOnly(List<Stock> stocks, List<String> warnings) {
        List<String> members = new ArrayList<>();
        List<Map<String, Object>> metas = new ArrayList<>();

        if (stocks != null) {
            for (Stock s : stocks) {
                if (s == null) continue;
                String code = safeTrim(s.getStockCode());
                if (code == null || code.isBlank()) continue;
                members.add(code);
                metas.add(metaItem(code, safeCompanyName(s)));
            }
        }

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("warnings", warnings);
        pack.put("members", members.stream().distinct().collect(Collectors.toList()));
        pack.put("metas", metas);
        pack.put("prices", List.of());
        pack.put("liquidity", List.of());
        pack.put("industry_index", Map.of());
        return pack;
    }
}
