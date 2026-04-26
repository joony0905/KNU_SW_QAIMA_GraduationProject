from __future__ import annotations

from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple
import math

import numpy as np

from app.models.feature2 import (
    PeerClusterRequest,
    PeerClusterResponse,
    RelativePoint,
    BandPoint,
    PeerItem,
)

from app.services.market_data import (
    MarketDataProvider,
    NotConfiguredMarketDataProvider,
    PriceSeries,
)


MIN_CORR = 0.2
# v1 조정 방식은 관측용 산업 공통 움직임 단순 차감이다.
# TODO: 요인 모델 검증 후 BETA_RESIDUAL_RESERVED 구현을 추가한다.
ADJUSTMENT_METHOD = "SIMPLE_SUBTRACTION"
INTERPRETATION_NOTE = (
    "이 클러스터는 최근 수익률 패턴과 시차 상관을 기반으로 한 관측 결과이며, "
    "향후 가격 방향을 예측하는 신호가 아닙니다."
)

# 전역 provider (앱 시작 시 주입해서 사용)
_market: MarketDataProvider = NotConfiguredMarketDataProvider()


def set_market_data_provider(provider: MarketDataProvider) -> None:
    global _market
    _market = provider


# =========================
# 유틸리티 함수
# =========================
def _np_dates(dates: List[datetime]) -> np.ndarray:
    # datetime을 np.datetime64[s]로 변환한다.
    out = []
    for dt in dates:
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        out.append(np.datetime64(dt.astimezone(timezone.utc).replace(tzinfo=None), "s"))
    return np.array(out)


def _log_returns(prices: np.ndarray) -> np.ndarray:
    prices = np.asarray(prices, dtype=float)
    if prices.size < 2:
        raise ValueError("TOO_SHORT")
    if np.any(prices <= 0):
        raise ValueError("NON_POSITIVE_PRICE")
    return np.diff(np.log(prices))


def _corr(a: np.ndarray, b: np.ndarray) -> float:
    if a.size < 10 or b.size < 10:
        return float("nan")
    if np.allclose(a, a[0]) or np.allclose(b, b[0]):
        return float("nan")
    return float(np.corrcoef(a, b)[0, 1])


def _quantile_ignore_nan(x: np.ndarray, q: float) -> float:
    x = x[~np.isnan(x)]
    if x.size == 0:
        return float("nan")
    return float(np.quantile(x, q))


def _mean_ignore_nan(x: np.ndarray) -> float:
    if np.any(~np.isnan(x)):
        return float(np.nanmean(x))
    return float("nan")


def _avg_last_window(vals: List[float]) -> Optional[float]:
    if not vals:
        return None
    arr = np.asarray(vals, dtype=float)
    arr = arr[~np.isnan(arr)]
    if arr.size == 0:
        return None
    return float(np.mean(arr))


def _series_volatility(series: PriceSeries) -> Optional[float]:
    if series is None or not series.close:
        return None
    try:
        returns = _log_returns(np.asarray(series.close, dtype=float))
    except Exception:
        return None

    returns = returns[~np.isnan(returns)]
    if returns.size < 20:
        return None

    vol = float(np.std(returns))
    if math.isnan(vol) or vol <= 0:
        return None
    return vol


def _within_ratio(value: Optional[float], anchor: Optional[float], low: float, high: float) -> bool:
    if value is None or anchor is None:
        return False
    if anchor <= 0:
        return False
    ratio = float(value) / float(anchor)
    return low <= ratio <= high


def _align_returns_by_common_dates(
    anchor: PriceSeries,
    other: PriceSeries,
    min_common: int = 30,
) -> Optional[Tuple[np.ndarray, np.ndarray]]:
    """
    로그 수익률 시계열을 공통 수익률 날짜 기준으로 정렬한다.
    수익률 날짜는 dates[1:]를 사용한다.
    """
    ad = _np_dates(anchor.dates)
    ap = np.asarray(anchor.close, dtype=float)
    od = _np_dates(other.dates)
    op = np.asarray(other.close, dtype=float)

    try:
        ar = _log_returns(ap)
        br = _log_returns(op)
    except Exception as ex:
        print(f"[DEBUG][_align_returns_by_common_dates] log return failed: {ex.__class__.__name__}: {ex}")
        return None

    a_ret_dates = ad[1:]
    o_ret_dates = od[1:]

    a_idx = {d: i for i, d in enumerate(a_ret_dates)}
    common_a = []
    common_b = []
    for j, d in enumerate(o_ret_dates):
        i = a_idx.get(d)
        if i is not None:
            common_a.append(i)
            common_b.append(j)

    if len(common_a) < min_common:
        print(
            f"[DEBUG][_align_returns_by_common_dates] insufficient common return dates: "
            f"{len(common_a)} < {min_common}"
        )
        return None

    return ar[np.array(common_a)], br[np.array(common_b)]


def _best_lag_corr(
    ar: np.ndarray,
    br: np.ndarray,
    max_lag: int,
    min_points: int = 30,
) -> Tuple[Optional[int], Optional[float]]:
    """
    Find lag in [-max_lag, +max_lag] maximizing corr(ar[t], br[t+lag]).

    Convention (ONE_D, days):
      lag > 0 => peer(br) must be shifted forward to align => peer lags anchor => FOLLOWER
      lag < 0 => peer leads anchor => LEADER
      lag = 0 => COINCIDENT
    """
    n = min(ar.size, br.size)
    if n < min_points:
        print(f"[DEBUG][_best_lag_corr] n too short: {n} < {min_points}")
        return None, None

    best_lag: Optional[int] = None
    best_corr: float = float("nan")

    for lag in range(-max_lag, max_lag + 1):
        if lag == 0:
            a = ar
            b = br
        elif lag > 0:
            if n - lag < min_points:
                continue
            a = ar[: n - lag]
            b = br[lag:n]
        else:
            k = -lag
            if n - k < min_points:
                continue
            a = ar[k:n]
            b = br[: n - k]

        c = _corr(a, b)
        if math.isnan(c):
            continue
        if best_lag is None or c > best_corr:
            best_lag = lag
            best_corr = float(c)

    if best_lag is None:
        print("[DEBUG][_best_lag_corr] no valid lag found")
        return None, None
    return best_lag, float(best_corr)


def _rebased_pct_on_common_dates(series: PriceSeries, common_dates: List[np.datetime64]) -> np.ndarray:
    """
    공통 날짜의 가격을 추출한 뒤 첫 유효값 기준으로 리베이스한다.
    결측은 NaN으로 두고, 첫 번째 non-NaN 값을 기준점으로 삼는다.
    """
    d = _np_dates(series.dates)
    p = np.asarray(series.close, dtype=float)
    idx = {dd: i for i, dd in enumerate(d)}

    vals = []
    for cd in common_dates:
        k = idx.get(cd)
        vals.append(float(p[k]) if k is not None else np.nan)
    vals = np.asarray(vals, dtype=float)

    valid = np.where(~np.isnan(vals))[0]
    if valid.size == 0:
        print("[DEBUG][_rebased_pct_on_common_dates] all values are NaN on common dates")
        return np.full_like(vals, np.nan)

    base = vals[valid[0]]
    if base <= 0:
        print(f"[DEBUG][_rebased_pct_on_common_dates] invalid base price: {base}")
        return np.full_like(vals, np.nan)

    return (vals / base) - 1.0


def _clamp01(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    if math.isnan(value):
        return None
    return max(0.0, min(1.0, float(value)))


def _positive_or_none(value: Optional[float]) -> Optional[float]:
    if value is None or math.isnan(float(value)):
        return None
    return max(0.0, float(value))


def _similarity_score(value: Optional[float], anchor: Optional[float]) -> Optional[float]:
    if value is None or anchor is None or value <= 0 or anchor <= 0:
        return None
    # 대칭 비율 유사도: 같으면 1, 2배 또는 0.5배면 약 0.59.
    return _clamp01(math.exp(-abs(math.log(float(value) / float(anchor)))))


def _finite_values(values: List[Optional[float]]) -> np.ndarray:
    arr = np.asarray([v for v in values if v is not None and not math.isnan(float(v))], dtype=float)
    return arr[np.isfinite(arr)]


def _lower_quantile(values: List[Optional[float]], q: float) -> Optional[float]:
    arr = _finite_values(values)
    if arr.size == 0:
        return None
    return float(np.quantile(arr, q))


def _quantile_bounds(values: List[Optional[float]], low_q: float, high_q: float) -> Tuple[Optional[float], Optional[float]]:
    arr = _finite_values(values)
    if arr.size == 0:
        return None, None
    return float(np.quantile(arr, low_q)), float(np.quantile(arr, high_q))


def _returns_by_date(series: PriceSeries) -> Optional[Dict[np.datetime64, float]]:
    try:
        dates = _np_dates(series.dates)
        returns = _log_returns(np.asarray(series.close, dtype=float))
    except Exception:
        return None
    return {dates[i + 1]: float(returns[i]) for i in range(returns.size)}


def _align_adjusted_returns_by_common_dates(
    anchor: PriceSeries,
    other: PriceSeries,
    industry_index: Optional[PriceSeries],
    min_common: int = 30,
) -> Tuple[Optional[Tuple[np.ndarray, np.ndarray, int, float]], Optional[str]]:
    if industry_index is None:
        return None, "INDUSTRY_INDEX_MISSING"

    anchor_returns = _returns_by_date(anchor)
    other_returns = _returns_by_date(other)
    index_returns = _returns_by_date(industry_index)
    if anchor_returns is None or other_returns is None or index_returns is None:
        return None, "RETURN_SERIES_INVALID"

    common_dates = sorted(set(anchor_returns) & set(other_returns) & set(index_returns))
    if len(common_dates) < min_common:
        return None, "INSUFFICIENT_COMMON_DATES"

    ar = np.asarray([anchor_returns[d] - index_returns[d] for d in common_dates], dtype=float)
    br = np.asarray([other_returns[d] - index_returns[d] for d in common_dates], dtype=float)
    expected = max(1, len(set(anchor_returns) & set(other_returns)))
    coverage = _clamp01(len(common_dates) / expected) or 0.0
    return (ar, br, len(common_dates), coverage), None


def _corr_stability(a: np.ndarray, b: np.ndarray, segments: int = 3, min_segment_points: int = 10) -> Tuple[Optional[float], List[float]]:
    n = min(a.size, b.size)
    if n < segments * min_segment_points:
        return None, []

    seg_corrs: List[float] = []
    for idx in np.array_split(np.arange(n), segments):
        if idx.size < min_segment_points:
            continue
        c = _corr(a[idx], b[idx])
        if not math.isnan(c):
            seg_corrs.append(float(c))

    if len(seg_corrs) < segments:
        return None, seg_corrs

    arr = np.asarray(seg_corrs, dtype=float)
    direction_consistency = float(np.mean(arr > 0))
    std_corr = float(np.std(arr))
    return _clamp01(direction_consistency * (1.0 - std_corr)), seg_corrs


def _lag_confidence(
    lead_lag_corr: Optional[float],
    base_corr: Optional[float],
    sample_size: int,
    corr_stability: Optional[float],
) -> Optional[float]:
    if lead_lag_corr is None or base_corr is None:
        return None
    sample_size_penalty = min(1.0, float(sample_size) / 60.0)
    stability_penalty = corr_stability if corr_stability is not None else 1.0
    return _clamp01(abs(float(lead_lag_corr) - float(base_corr)) * sample_size_penalty * stability_penalty)


def calculate_peer_score(
    corr: Optional[float],
    adjusted_corr: Optional[float],
    lead_lag_corr: Optional[float],
    lag_confidence: Optional[float],
    liquidity_similarity_score: Optional[float],
    corr_stability: Optional[float],
    volatility_similarity_score: Optional[float],
) -> float:
    corr_value = adjusted_corr if adjusted_corr is not None else corr
    corr_score = _clamp01(_positive_or_none(corr_value)) or 0.0
    lag_score = _clamp01(abs(float(lead_lag_corr)) * float(lag_confidence)) if lead_lag_corr is not None and lag_confidence is not None else 0.0
    liquidity_score = _clamp01(liquidity_similarity_score) or 0.0
    stability_score = _clamp01(corr_stability) or 0.0
    volatility_score = _clamp01(volatility_similarity_score) or 0.0

    return _clamp01(
        0.40 * corr_score
        + 0.20 * lag_score
        + 0.15 * liquidity_score
        + 0.15 * stability_score
        + 0.10 * volatility_score
    ) or 0.0


def _candidate_is_displayable(item: PeerItem) -> bool:
    corr_score = _positive_or_none(item.adjusted_corr if item.adjusted_corr is not None else item.corr)
    if corr_score is not None and corr_score > 0:
        return True
    if item.adjusted_corr_valid and item.adjusted_corr is not None and item.adjusted_corr > 0:
        return True
    if item.raw_corr_valid and item.corr is not None and item.corr > 0:
        return True
    return False


def _display_status_for_non_selected(item: PeerItem, eligible: bool) -> str:
    if eligible:
        return "ELIGIBLE_NOT_SELECTED"
    if item.adjustment_basis == "FALLBACK_RAW":
        return "FALLBACK_RAW"
    if item.adjusted_corr_valid and not item.raw_corr_valid:
        return "ADJUSTED_ONLY"
    if item.raw_corr_valid and not item.adjusted_corr_valid:
        return "RAW_ONLY"
    corr_score = _positive_or_none(item.adjusted_corr if item.adjusted_corr is not None else item.corr)
    if corr_score is not None and corr_score < MIN_CORR:
        return "LOW_CORR"
    return "DISPLAY_ONLY"


def _empty_response(
    req: PeerClusterRequest,
    freq: str,
    warnings: List[str],
    raw_candidate_count: int = 0,
    evaluated_candidate_count: int = 0,
    eligible_candidate_count: int = 0,
    effective_peer_count: int = 0,
) -> PeerClusterResponse:
    return PeerClusterResponse(
        industry_id=req.industry_id,
        anchor_stock_code=req.anchor_stock_code,
        freq=freq,  # type: ignore[arg-type]
        window=req.window,
        peer_count=0,
        requested_peer_count=req.peer_count,
        effective_peer_count=effective_peer_count,
        raw_candidate_count=raw_candidate_count,
        evaluated_candidate_count=evaluated_candidate_count,
        eligible_candidate_count=eligible_candidate_count,
        selected_peer_count=0,
        displayed_candidate_count=0,
        display_limit=req.display_limit,
        adjustment_method=ADJUSTMENT_METHOD,  # type: ignore[arg-type]
        industry_index_code=None,
        industry_index_name=None,
        adjusted_return_sample_size=None,
        adjusted_return_coverage_ratio=None,
        adjustment_valid=False,
        adjustment_fallback_reason=None,
        anchor_series=[],
        industry_index_series=[],
        centroid=[],
        band=[],
        peer_centroid=[],
        peer_band=[],
        peers=[],
        candidates=[],
        as_of=datetime.now(timezone.utc),
        interpretation_note=INTERPRETATION_NOTE,
        warnings=warnings,
    )


# =========================
# PeerCluster v1 핵심 로직
# - 동일 industry 기준(members 제공)
# - liquidity 필터(옵션)
# - corr + lead-lag(리더/팔로워) 계산
# - centroid(평균), band(p20/p80)
#
# 계약:
# - market_cap/cap_score 없음
# - best_lag/lead_lag_corr/relation 포함
# =========================
def compute_peer_cluster_v1(req: PeerClusterRequest) -> PeerClusterResponse:
    warnings: List[str] = []

    print("=" * 80)
    print("[DEBUG][compute_peer_cluster_v1] START")
    print(
        "[DEBUG][compute_peer_cluster_v1] req = "
        f"industry_id={req.industry_id}, "
        f"anchor_stock_code={req.anchor_stock_code}, "
        f"freq={req.freq}, "
        f"window={req.window}, "
        f"peer_count={req.peer_count}, "
        f"max_lag={getattr(req, 'max_lag', None)}, "
        f"liquidity_top_k_turnover={getattr(req, 'liquidity_top_k_turnover', None)}, "
        f"liquidity_min_turnover={getattr(req, 'liquidity_min_turnover', None)}, "
        f"liquidity_min_volume={getattr(req, 'liquidity_min_volume', None)}"
    )

    # MVP에서는 ONE_D만 지원한다.
    freq = req.freq
    if freq != "ONE_D":
        warnings.append("PEER_FREQ_NOT_SUPPORTED_FALLBACK_ONE_D")
        freq = "ONE_D"
        print("[DEBUG][compute_peer_cluster_v1] freq fallback applied -> ONE_D")

    # -------------------------
    # 1) pack 우선 로드. 없으면 기존 bulk 경로를 사용한다.
    # -------------------------
    members: List[str]
    metas: Dict[str, object]  # StockMeta와 유사한 객체. company_name만 사용한다.
    price_map: Dict[str, PriceSeries]
    liq_map: Dict[str, object]  # LiquiditySeries와 유사한 객체. turnover/volume 목록을 사용한다.
    industry_index_ps: Optional[PriceSeries] = None
    industry_index_name: Optional[str] = None

    if hasattr(_market, "get_peercluster_pack"):
        try:
            pack = _market.get_peercluster_pack(
                req.industry_id,
                req.anchor_stock_code,
                freq,
                req.window,
            )
            if len(pack) >= 7:
                members, metas, price_map, liq_map, spring_warnings, industry_index_ps, industry_index_name = pack
            elif len(pack) >= 6:
                members, metas, price_map, liq_map, spring_warnings, industry_index_ps = pack
            else:
                members, metas, price_map, liq_map, spring_warnings = pack
            if spring_warnings:
                warnings.extend(list(spring_warnings))

            print("[DEBUG][pack] loaded from spring pack")
            print(f"[DEBUG][pack] members count = {len(members)}")
            print(f"[DEBUG][pack] metas count   = {len(metas)}")
            print(f"[DEBUG][pack] price_map cnt = {len(price_map)}")
            print(f"[DEBUG][pack] liq_map cnt   = {len(liq_map)}")
            print(f"[DEBUG][pack] spring warnings = {spring_warnings}")
            print(f"[DEBUG][pack] anchor in members   = {req.anchor_stock_code in members}")
            print(f"[DEBUG][pack] anchor in price_map = {req.anchor_stock_code in price_map}")

            if req.anchor_stock_code in price_map:
                aps = price_map[req.anchor_stock_code]
                print(
                    "[DEBUG][pack] anchor series size = "
                    f"dates={len(aps.dates)}, close={len(aps.close)}"
                )

        except Exception as ex:
            print(f"[DEBUG][pack] spring pack fetch failed: {ex.__class__.__name__}: {ex}")
            return _empty_response(req, freq, warnings + ["SPRING_PACK_FETCH_FAILED", ex.__class__.__name__])
    else:
        # 기존 bulk 경로. 로컬/mock provider에서 사용한다.
        members = _market.get_industry_members(req.industry_id)
        metas = _market.get_stock_meta_bulk(members)
        price_map = _market.get_price_series_bulk(members, freq=freq, window=req.window)
        liq_map = _market.get_liquidity_series_bulk(members, freq=freq, window=req.window)

        print("[DEBUG][pack] loaded from legacy provider")
        print(f"[DEBUG][pack] members count = {len(members)}")
        print(f"[DEBUG][pack] metas count   = {len(metas)}")
        print(f"[DEBUG][pack] price_map cnt = {len(price_map)}")
        print(f"[DEBUG][pack] liq_map cnt   = {len(liq_map)}")
        print(f"[DEBUG][pack] anchor in members   = {req.anchor_stock_code in members}")
        print(f"[DEBUG][pack] anchor in price_map = {req.anchor_stock_code in price_map}")

    # -------------------------
    # 2) 기본 검증
    # -------------------------
    if not members:
        print("[DEBUG][validation] NO_INDUSTRY_MEMBERS")
        return _empty_response(req, freq, warnings + ["NO_INDUSTRY_MEMBERS"])

    if req.anchor_stock_code not in members:
        warnings.append("ANCHOR_NOT_IN_INDUSTRY_MEMBERS")
        print("[DEBUG][validation] anchor not in members")

    anchor_ps = price_map.get(req.anchor_stock_code)
    if anchor_ps is None:
        print("[DEBUG][validation] ANCHOR_PRICE_SERIES_MISSING")
        raw_candidate_count = len([c for c in members if c != req.anchor_stock_code])
        return _empty_response(req, freq, warnings + ["ANCHOR_PRICE_SERIES_MISSING"], raw_candidate_count=raw_candidate_count)

    print(
        "[DEBUG][validation] anchor series ok: "
        f"dates={len(anchor_ps.dates)}, close={len(anchor_ps.close)}"
    )

    # -------------------------
    # 3) 유동성 + 변동성 품질 점수화/사전 필터
    # -------------------------
    avg_turnover: Dict[str, Optional[float]] = {}
    avg_volume: Dict[str, Optional[float]] = {}
    volatility_map: Dict[str, Optional[float]] = {}

    for code in members:
        liq = liq_map.get(code)
        if liq is None:
            avg_turnover[code] = None
            avg_volume[code] = None
        else:
            try:
                avg_turnover[code] = _avg_last_window(list(getattr(liq, "turnover")))
            except Exception:
                avg_turnover[code] = None

            try:
                avg_volume[code] = _avg_last_window(list(getattr(liq, "volume")))
            except Exception:
                avg_volume[code] = None

        volatility_map[code] = _series_volatility(price_map.get(code))

    candidates = [c for c in members if c != req.anchor_stock_code]
    raw_candidate_count = len(candidates)
    print(f"[DEBUG][liquidity] initial candidates = {raw_candidate_count}")

    if raw_candidate_count < req.peer_count:
        warnings.append("PEER_COUNT_REDUCED_BY_CANDIDATE_SIZE")
        warnings.append("PEER_CANDIDATES_INSUFFICIENT")

    # 거래대금 상위 K개 필터. 선택적으로 적용한다.
    if req.liquidity_top_k_turnover is not None:
        ranked = sorted(
            [c for c in candidates if avg_turnover.get(c) is not None],
            key=lambda x: float(avg_turnover[x]),  # type: ignore[arg-type]
            reverse=True,
        )
        keep = set(ranked[: req.liquidity_top_k_turnover])
        before = len(candidates)
        candidates = [c for c in candidates if c in keep]
        print(f"[DEBUG][liquidity] top_k_turnover applied: {before} -> {len(candidates)}")
        if len(candidates) < before:
            warnings.append("LIQUIDITY_TOPK_TURNOVER_APPLIED")
        if len(candidates) < req.peer_count:
            warnings.append("PEER_CANDIDATES_LOW_AFTER_LIQUIDITY_FILTER")

    # 최소 거래대금 필터
    if req.liquidity_min_turnover is not None:
        before = len(candidates)
        candidates = [
            c for c in candidates
            if (avg_turnover.get(c) is not None and float(avg_turnover[c]) >= float(req.liquidity_min_turnover))
        ]
        print(f"[DEBUG][liquidity] min_turnover applied: {before} -> {len(candidates)}")
        if len(candidates) < before:
            warnings.append("LIQUIDITY_MIN_TURNOVER_APPLIED")
        if len(candidates) < req.peer_count:
            warnings.append("PEER_CANDIDATES_LOW_AFTER_LIQUIDITY_FILTER")

    # 최소 거래량 필터
    if req.liquidity_min_volume is not None:
        before = len(candidates)
        candidates = [
            c for c in candidates
            if (avg_volume.get(c) is not None and float(avg_volume[c]) >= float(req.liquidity_min_volume))
        ]
        print(f"[DEBUG][liquidity] min_volume applied: {before} -> {len(candidates)}")
        if len(candidates) < before:
            warnings.append("LIQUIDITY_MIN_VOLUME_APPLIED")
        if len(candidates) < req.peer_count:
            warnings.append("PEER_CANDIDATES_LOW_AFTER_LIQUIDITY_FILTER")

    anchor_turnover = avg_turnover.get(req.anchor_stock_code)
    anchor_volatility = volatility_map.get(req.anchor_stock_code)

    print(
        "[DEBUG][prefilter] anchor metrics: "
        f"avg_turnover={anchor_turnover}, volatility={anchor_volatility}"
    )

    liquidity_similarity_score: Dict[str, Optional[float]] = {}
    volatility_similarity_score: Dict[str, Optional[float]] = {}
    for code in candidates:
        liquidity_similarity_score[code] = _similarity_score(avg_turnover.get(code), anchor_turnover)
        volatility_similarity_score[code] = _similarity_score(volatility_map.get(code), anchor_volatility)

    turnover_floor = _lower_quantile([avg_turnover.get(c) for c in candidates], 0.15)
    vol_floor, vol_ceiling = _quantile_bounds([volatility_map.get(c) for c in candidates], 0.05, 0.95)

    quality_filtered: List[str] = []
    for code in candidates:
        if price_map.get(code) is None:
            continue
        turnover = avg_turnover.get(code)
        volatility = volatility_map.get(code)
        if turnover_floor is not None and turnover is not None and turnover < turnover_floor:
            continue
        if volatility is not None and vol_floor is not None and volatility <= vol_floor:
            continue
        if volatility is not None and vol_ceiling is not None and volatility >= vol_ceiling:
            continue
        quality_filtered.append(code)

    print(
        "[DEBUG][quality-filter] "
        f"candidates={len(candidates)}, turnover_floor={turnover_floor}, "
        f"vol_floor={vol_floor}, vol_ceiling={vol_ceiling}, result={len(quality_filtered)}"
    )

    if len(quality_filtered) < req.peer_count and raw_candidate_count >= req.peer_count:
        warnings.append("PEER_FILTER_RELAXED")
        warnings.append("PEER_CANDIDATES_LOW_AFTER_QUALITY_FILTER")
        if vol_floor is not None or vol_ceiling is not None:
            warnings.append("PEER_CANDIDATES_LOW_AFTER_VOLATILITY_FILTER")
        relaxed = [c for c in candidates if price_map.get(c) is not None]
        if len(relaxed) >= len(quality_filtered):
            quality_filtered = relaxed
    elif len(quality_filtered) < req.peer_count:
        warnings.append("PEER_CANDIDATES_LOW_AFTER_QUALITY_FILTER")
        if vol_floor is not None or vol_ceiling is not None:
            warnings.append("PEER_CANDIDATES_LOW_AFTER_VOLATILITY_FILTER")

    candidates = quality_filtered
    evaluated_candidate_count = len(candidates)

    if not candidates:
        print("[DEBUG][liquidity] NO_CANDIDATES_AFTER_LIQUIDITY_FILTER")
        return _empty_response(
            req,
            freq,
            warnings + ["NO_CANDIDATES_AFTER_LIQUIDITY_FILTER"],
            raw_candidate_count=raw_candidate_count,
            evaluated_candidate_count=evaluated_candidate_count,
        )

    print(f"[DEBUG][liquidity] final candidates = {len(candidates)}")

    # -------------------------
    # 4) 상관계수 + 선행/후행 기준 점수화
    # -------------------------
    display_candidate_items: List[PeerItem] = []
    peer_items: List[PeerItem] = []

    max_lag = int(getattr(req, "max_lag", 5) or 5)
    if max_lag < 1:
        max_lag = 1

    print(f"[DEBUG][score] max_lag = {max_lag}")
    adjusted_fallback_warned = False
    first_adjustment_fallback_reason: Optional[str] = None
    stability_insufficient_warned = False

    for code in candidates:
        ps = price_map.get(code)
        if ps is None:
            print(f"[DEBUG][score][DROP] {code}: price series missing")
            continue

        print(
            f"[DEBUG][score][TRY] {code}: "
            f"dates={len(ps.dates)}, close={len(ps.close)}"
        )

        aligned = _align_returns_by_common_dates(anchor_ps, ps, min_common=30)
        if aligned is None:
            print(f"[DEBUG][score][DROP] {code}: alignment failed")
            continue

        ar, br = aligned
        print(
            f"[DEBUG][score][ALIGN] {code}: "
            f"anchor_returns={ar.size}, peer_returns={br.size}"
        )

        corr0_raw = _corr(ar, br)
        corr0 = None if math.isnan(corr0_raw) else float(corr0_raw)
        raw_corr_valid = corr0 is not None
        adjusted_corr: Optional[float] = None
        adjusted_corr_valid = False
        adjusted_return_sample_size: Optional[int] = None
        adjusted_return_coverage_ratio: Optional[float] = None
        adjustment_basis = "RAW_ONLY"
        adjusted_aligned, adjustment_failure_reason = _align_adjusted_returns_by_common_dates(
            anchor_ps,
            ps,
            industry_index_ps,
            min_common=30,
        )
        if adjusted_aligned is None:
            if adjustment_failure_reason and first_adjustment_fallback_reason is None:
                first_adjustment_fallback_reason = adjustment_failure_reason
            if not adjusted_fallback_warned:
                warnings.append("INDUSTRY_ADJUSTED_RETURN_FALLBACK_RAW")
                adjusted_fallback_warned = True
            score_ar, score_br = ar, br
            if industry_index_ps is not None:
                adjustment_basis = "FALLBACK_RAW"
        else:
            adj_ar, adj_br, adjusted_return_sample_size, adjusted_return_coverage_ratio = adjusted_aligned
            adjusted_raw = _corr(adj_ar, adj_br)
            adjusted_corr = None if math.isnan(adjusted_raw) else float(adjusted_raw)
            if adjusted_corr is None:
                adjustment_failure_reason = "ADJUSTED_CORR_INVALID"
                if first_adjustment_fallback_reason is None:
                    first_adjustment_fallback_reason = adjustment_failure_reason
                if not adjusted_fallback_warned:
                    warnings.append("INDUSTRY_ADJUSTED_RETURN_FALLBACK_RAW")
                    adjusted_fallback_warned = True
                score_ar, score_br = ar, br
                adjustment_basis = "FALLBACK_RAW"
            else:
                score_ar, score_br = adj_ar, adj_br
                adjusted_corr_valid = True
                adjustment_basis = "SIMPLE_SUBTRACTION"

        base_corr = adjusted_corr if adjusted_corr is not None else corr0
        corr_score_value = _positive_or_none(base_corr)
        eligible_for_selection = corr_score_value is not None and corr_score_value >= MIN_CORR

        best_lag, ll_corr = _best_lag_corr(score_ar, score_br, max_lag=max_lag, min_points=30)
        corr_stability, segment_corrs = _corr_stability(score_ar, score_br)
        if corr_stability is None and not stability_insufficient_warned:
            warnings.append("PEER_CORR_STABILITY_INSUFFICIENT_DATA")
            stability_insufficient_warned = True
        lag_conf = _lag_confidence(ll_corr, base_corr, int(min(score_ar.size, score_br.size)), corr_stability)

        if best_lag is None or ll_corr is None:
            relation = "UNKNOWN"
        elif best_lag == 0:
            relation = "COINCIDENT"
        elif best_lag > 0:
            relation = "FOLLOWER"
        else:
            relation = "LEADER"

        meta = metas.get(code)
        company_name = None
        try:
            company_name = getattr(meta, "company_name", None) if meta is not None else None
        except Exception:
            company_name = None

        # 순위 점수:
        # - v1은 양의 동행성을 가진 peer만 selected에 포함한다.
        peer_score = calculate_peer_score(
            corr=corr0,
            adjusted_corr=adjusted_corr,
            lead_lag_corr=ll_corr,
            lag_confidence=lag_conf,
            liquidity_similarity_score=liquidity_similarity_score.get(code),
            corr_stability=corr_stability,
            volatility_similarity_score=volatility_similarity_score.get(code),
        )

        print(
            f"[DEBUG][score][KEEP] {code}: "
            f"corr0={corr0}, adjusted_corr={adjusted_corr}, best_lag={best_lag}, ll_corr={ll_corr}, "
            f"lag_confidence={lag_conf}, corr_stability={corr_stability}, segments={segment_corrs}, "
            f"relation={relation}, peer_score={peer_score}"
        )

        peer_items.append(
            PeerItem(
                stock_code=code,
                company_name=company_name,
                avg_turnover=avg_turnover.get(code),
                avg_volume=avg_volume.get(code),
                corr=corr0,
                adjusted_corr=adjusted_corr,
                corr_stability=corr_stability,
                raw_corr_valid=raw_corr_valid,
                adjusted_corr_valid=adjusted_corr_valid,
                adjusted_return_sample_size=adjusted_return_sample_size,
                adjusted_return_coverage_ratio=adjusted_return_coverage_ratio,
                adjustment_basis=adjustment_basis,  # type: ignore[arg-type]
                best_lag=best_lag,
                lead_lag_corr=ll_corr,
                lag_confidence=lag_conf,
                relation=relation,  # type: ignore[arg-type]
                liquidity_similarity_score=liquidity_similarity_score.get(code),
                volatility_similarity_score=volatility_similarity_score.get(code),
                score=peer_score,
                peer_score=peer_score,
            )
        )
        item = peer_items[-1]
        item.display_status = _display_status_for_non_selected(item, eligible_for_selection)  # type: ignore[assignment]
        if _candidate_is_displayable(item):
            display_candidate_items.append(item)
        if not eligible_for_selection:
            # v1 selected peer는 양의 동행성만 사용한다. 향후 contrastPeer에서는 음의 상관을 활용할 수 있다.
            peer_items.pop()
            print(f"[DEBUG][score][DISPLAY] {code}: not selected-eligible. corr={corr0}, adjusted_corr={adjusted_corr}")

    print(f"[DEBUG][score] peer_items count = {len(peer_items)}")
    eligible_candidate_count = len(peer_items)
    effective_peer_count = min(req.peer_count, eligible_candidate_count)

    if eligible_candidate_count < req.peer_count:
        warnings.append("INSUFFICIENT_PEERS_AFTER_SERIES_FILTER")
        warnings.append("PEER_CLUSTER_LOW_POSITIVE_CORR_CANDIDATES")
        print(
            f"[DEBUG][score] insufficient peers after filter: "
            f"{eligible_candidate_count} < {req.peer_count}"
        )

    if not peer_items and not display_candidate_items:
        print("[DEBUG][score] NO_PEERS_AFTER_ALIGNMENT")
        return _empty_response(
            req,
            freq,
            warnings + ["NO_PEERS_AFTER_ALIGNMENT"],
            raw_candidate_count=raw_candidate_count,
            evaluated_candidate_count=evaluated_candidate_count,
            eligible_candidate_count=eligible_candidate_count,
            effective_peer_count=effective_peer_count,
        )
    if not peer_items:
        warnings.append("NO_SELECTED_PEERS_AFTER_MIN_CORR")

    peer_items.sort(
        key=lambda x: (
            -(float(x.peer_score) if x.peer_score is not None else -999.0),
            -(float(x.adjusted_corr) if x.adjusted_corr is not None else -999.0),
            -(float(x.corr) if x.corr is not None else -999.0),
            x.stock_code,
        ),
    )
    selected = peer_items[:effective_peer_count]
    selected_codes = {p.stock_code for p in selected}
    eligible_codes = {p.stock_code for p in peer_items}
    for item in display_candidate_items:
        if item.stock_code in selected_codes:
            item.display_status = "SELECTED"  # type: ignore[assignment]
        elif item.stock_code in eligible_codes:
            item.display_status = "ELIGIBLE_NOT_SELECTED"  # type: ignore[assignment]
    display_candidate_items.sort(
        key=lambda x: (
            0 if x.stock_code in selected_codes else 1,
            -(float(x.peer_score) if x.peer_score is not None else -999.0),
            -(float(x.adjusted_corr) if x.adjusted_corr is not None else -999.0),
            -(float(x.corr) if x.corr is not None else -999.0),
            x.stock_code,
        ),
    )
    displayed_candidates = display_candidate_items[: req.display_limit]
    if len(displayed_candidates) < min(req.display_limit, evaluated_candidate_count):
        warnings.append("PEER_DISPLAY_CANDIDATES_LOW")
    selected_adjusted_samples = [
        int(p.adjusted_return_sample_size)
        for p in selected
        if p.adjusted_corr_valid and p.adjusted_return_sample_size is not None
    ]
    selected_adjusted_coverages = [
        float(p.adjusted_return_coverage_ratio)
        for p in selected
        if p.adjusted_corr_valid and p.adjusted_return_coverage_ratio is not None
    ]
    adjustment_valid = len(selected_adjusted_samples) > 0
    response_adjusted_sample_size = min(selected_adjusted_samples) if selected_adjusted_samples else None
    response_adjusted_coverage_ratio = min(selected_adjusted_coverages) if selected_adjusted_coverages else None
    if not adjustment_valid and first_adjustment_fallback_reason is None and industry_index_ps is None:
        first_adjustment_fallback_reason = "INDUSTRY_INDEX_MISSING"

    print("[DEBUG][score] selected peers:")
    for idx, p in enumerate(selected, start=1):
        print(
            f"  {idx}. {p.stock_code} "
            f"peer_score={p.peer_score}, corr={p.corr}, adjusted_corr={p.adjusted_corr}, "
            f"best_lag={p.best_lag}, relation={p.relation}"
        )

    # -------------------------
    # 5) centroid/band 계산. 공통 날짜 기준 리베이스 수익률을 사용한다.
    # -------------------------
    common_dates = _np_dates(anchor_ps.dates)

    print(f"[DEBUG][chart] anchor_dates count = {len(common_dates)}")
    print(f"[DEBUG][chart] common init count = {len(common_dates)}")

    if industry_index_ps is not None:
        index_dates = _np_dates(industry_index_ps.dates)
        before = len(common_dates)
        common_dates = np.intersect1d(common_dates, index_dates)
        print(
            f"[DEBUG][chart] intersect with industry index: "
            f"index_dates={len(index_dates)}, common {before} -> {len(common_dates)}"
        )

    for p in selected:
        ps = price_map.get(p.stock_code)
        if ps is None:
            print(f"[DEBUG][chart][WARN] missing selected peer series: {p.stock_code}")
            continue

        peer_dates = _np_dates(ps.dates)
        before = len(common_dates)
        common_dates = np.intersect1d(common_dates, peer_dates)
        after = len(common_dates)

        print(
            f"[DEBUG][chart] intersect with {p.stock_code}: "
            f"peer_dates={len(peer_dates)}, common {before} -> {after}"
        )

    common_dates = list(common_dates)
    print(f"[DEBUG][chart] common_dates count = {len(common_dates)}")

    if common_dates:
        print(
            f"[DEBUG][chart] common_dates first={common_dates[0]}, "
            f"last={common_dates[-1]}"
        )

    if len(common_dates) < 30:
        warnings.append("INSUFFICIENT_COMMON_DATES_FOR_CHART")
        print(
            f"[DEBUG][chart] insufficient common dates for chart: "
            f"{len(common_dates)} < 30"
        )

    anchor_series: List[RelativePoint] = []
    industry_index_series: List[RelativePoint] = []
    anchor_col = _rebased_pct_on_common_dates(anchor_ps, common_dates)
    index_col = _rebased_pct_on_common_dates(industry_index_ps, common_dates) if industry_index_ps is not None else None
    for i, cd in enumerate(common_dates):
        if i >= anchor_col.size or math.isnan(float(anchor_col[i])):
            continue
        dt = datetime.fromtimestamp(cd.astype("datetime64[s]").astype(int), tz=timezone.utc)
        anchor_series.append(RelativePoint(t=dt, value=float(anchor_col[i])))
        if index_col is not None and i < index_col.size and not math.isnan(float(index_col[i])):
            industry_index_series.append(RelativePoint(t=dt, value=float(index_col[i])))

    rel_cols = []
    for p in selected:
        ps = price_map.get(p.stock_code)
        if ps is None:
            print(f"[DEBUG][chart][WARN] selected peer missing when rebase: {p.stock_code}")
            rel_cols.append(np.full((len(common_dates),), np.nan))
            continue

        col = _rebased_pct_on_common_dates(ps, common_dates)
        nan_count = int(np.isnan(col).sum())
        print(
            f"[DEBUG][chart] rebased {p.stock_code}: "
            f"len={len(col)}, nan_count={nan_count}"
        )
        rel_cols.append(col)

    rel_mat = np.vstack(rel_cols).T if rel_cols else np.zeros((0, 0), dtype=float)
    print(f"[DEBUG][chart] rel_mat shape = {rel_mat.shape}")

    centroid: List[RelativePoint] = []
    band: List[BandPoint] = []

    for i, cd in enumerate(common_dates):
        row = rel_mat[i, :] if rel_mat.size else np.array([], dtype=float)
        c_mean = _mean_ignore_nan(row)
        p20 = _quantile_ignore_nan(row, 0.2)
        p80 = _quantile_ignore_nan(row, 0.8)

        if not (math.isnan(c_mean) or math.isnan(p20) or math.isnan(p80)):
            dt = datetime.fromtimestamp(cd.astype("datetime64[s]").astype(int), tz=timezone.utc)
            centroid.append(RelativePoint(t=dt, value=float(c_mean)))
            band.append(BandPoint(t=dt, p20=float(p20), p80=float(p80)))

    print(f"[DEBUG][chart] centroid count = {len(centroid)}")
    print(f"[DEBUG][chart] band count     = {len(band)}")
    print(f"[DEBUG][compute_peer_cluster_v1] warnings = {warnings + ['MARKET_CAP_EXCLUDED_V1']}")
    print("[DEBUG][compute_peer_cluster_v1] END")
    print("=" * 80)

    return PeerClusterResponse(
        method="INDUSTRY_CORR_V1",
        industry_id=req.industry_id,
        anchor_stock_code=req.anchor_stock_code,
        freq=freq,
        window=req.window,
        peer_count=len(selected),
        requested_peer_count=req.peer_count,
        effective_peer_count=effective_peer_count,
        raw_candidate_count=raw_candidate_count,
        evaluated_candidate_count=evaluated_candidate_count,
        eligible_candidate_count=eligible_candidate_count,
        selected_peer_count=len(selected),
        displayed_candidate_count=len(displayed_candidates),
        display_limit=req.display_limit,
        adjustment_method=ADJUSTMENT_METHOD,  # type: ignore[arg-type]
        industry_index_code=industry_index_ps.stock_code if industry_index_ps is not None else None,
        industry_index_name=industry_index_name,
        adjusted_return_sample_size=response_adjusted_sample_size,
        adjusted_return_coverage_ratio=response_adjusted_coverage_ratio,
        adjustment_valid=adjustment_valid,
        adjustment_fallback_reason=None if adjustment_valid else first_adjustment_fallback_reason,
        anchor_series=anchor_series,
        industry_index_series=industry_index_series,
        # TODO: centroid/band는 peer_centroid/peer_band의 기존 별칭이다.
        centroid=centroid,
        band=band,
        peer_centroid=centroid,
        peer_band=band,
        peers=selected,
        candidates=displayed_candidates,
        as_of=datetime.now(timezone.utc),
        interpretation_note=INTERPRETATION_NOTE,
        warnings=warnings + ["MARKET_CAP_EXCLUDED_V1"],
    )
