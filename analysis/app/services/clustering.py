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

# 전역 provider (앱 시작 시 주입해서 사용)
_market: MarketDataProvider = NotConfiguredMarketDataProvider()


def set_market_data_provider(provider: MarketDataProvider) -> None:
    global _market
    _market = provider


# =========================
# Helpers
# =========================
def _np_dates(dates: List[datetime]) -> np.ndarray:
    # datetime -> np.datetime64[s]
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


def _align_returns_by_common_dates(
    anchor: PriceSeries,
    other: PriceSeries,
    min_common: int = 30,
) -> Optional[Tuple[np.ndarray, np.ndarray]]:
    """
    Align log return series by common return-dates.
    return-dates are dates[1:].
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
    Extract prices on common_dates and rebase to first available point.
    Missing -> NaN, then rebased based on first non-NaN.
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


# =========================
# Core: PeerCluster v1
# - industry 동일 (members 제공)
# - liquidity 필터(옵션)
# - corr + lead-lag(리더/팔로워) 계산
# - centroid(mean), band(p20/p80)
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

    # MVP: ONE_D only
    freq = req.freq
    if freq != "ONE_D":
        warnings.append("PEER_FREQ_NOT_SUPPORTED_FALLBACK_ONE_D")
        freq = "ONE_D"
        print("[DEBUG][compute_peer_cluster_v1] freq fallback applied -> ONE_D")

    # -------------------------
    # 1) Load pack (preferred) or legacy bulk
    # -------------------------
    members: List[str]
    metas: Dict[str, object]  # StockMeta-like (company_name만 사용)
    price_map: Dict[str, PriceSeries]
    liq_map: Dict[str, object]  # LiquiditySeries-like (turnover, volume list)

    if hasattr(_market, "get_peercluster_pack"):
        try:
            members, metas, price_map, liq_map, spring_warnings = _market.get_peercluster_pack(
                req.industry_id,
                req.anchor_stock_code,
                freq,
                req.window,
            )
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
            return PeerClusterResponse(
                industry_id=req.industry_id,
                anchor_stock_code=req.anchor_stock_code,
                freq=freq,
                window=req.window,
                peer_count=0,
                centroid=[],
                band=[],
                peers=[],
                as_of=datetime.now(timezone.utc),
                warnings=warnings + ["SPRING_PACK_FETCH_FAILED", ex.__class__.__name__],
            )
    else:
        # legacy bulk path (local/mock provider)
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
    # 2) Basic validation
    # -------------------------
    if not members:
        print("[DEBUG][validation] NO_INDUSTRY_MEMBERS")
        return PeerClusterResponse(
            industry_id=req.industry_id,
            anchor_stock_code=req.anchor_stock_code,
            freq=freq,
            window=req.window,
            peer_count=0,
            centroid=[],
            band=[],
            peers=[],
            as_of=datetime.now(timezone.utc),
            warnings=warnings + ["NO_INDUSTRY_MEMBERS"],
        )

    if req.anchor_stock_code not in members:
        warnings.append("ANCHOR_NOT_IN_INDUSTRY_MEMBERS")
        print("[DEBUG][validation] anchor not in members")

    anchor_ps = price_map.get(req.anchor_stock_code)
    if anchor_ps is None:
        print("[DEBUG][validation] ANCHOR_PRICE_SERIES_MISSING")
        return PeerClusterResponse(
            industry_id=req.industry_id,
            anchor_stock_code=req.anchor_stock_code,
            freq=freq,
            window=req.window,
            peer_count=0,
            centroid=[],
            band=[],
            peers=[],
            as_of=datetime.now(timezone.utc),
            warnings=warnings + ["ANCHOR_PRICE_SERIES_MISSING"],
        )

    print(
        "[DEBUG][validation] anchor series ok: "
        f"dates={len(anchor_ps.dates)}, close={len(anchor_ps.close)}"
    )

    # -------------------------
    # 3) Liquidity prefilter
    # -------------------------
    avg_turnover: Dict[str, Optional[float]] = {}
    avg_volume: Dict[str, Optional[float]] = {}

    for code in members:
        liq = liq_map.get(code)
        if liq is None:
            avg_turnover[code] = None
            avg_volume[code] = None
            continue

        try:
            avg_turnover[code] = _avg_last_window(list(getattr(liq, "turnover")))
        except Exception:
            avg_turnover[code] = None

        try:
            avg_volume[code] = _avg_last_window(list(getattr(liq, "volume")))
        except Exception:
            avg_volume[code] = None

    candidates = [c for c in members if c != req.anchor_stock_code]
    print(f"[DEBUG][liquidity] initial candidates = {len(candidates)}")

    # optional top-K by turnover
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

    # min turnover filter
    if req.liquidity_min_turnover is not None:
        before = len(candidates)
        candidates = [
            c for c in candidates
            if (avg_turnover.get(c) is not None and float(avg_turnover[c]) >= float(req.liquidity_min_turnover))
        ]
        print(f"[DEBUG][liquidity] min_turnover applied: {before} -> {len(candidates)}")
        if len(candidates) < before:
            warnings.append("LIQUIDITY_MIN_TURNOVER_APPLIED")

    # min volume filter
    if req.liquidity_min_volume is not None:
        before = len(candidates)
        candidates = [
            c for c in candidates
            if (avg_volume.get(c) is not None and float(avg_volume[c]) >= float(req.liquidity_min_volume))
        ]
        print(f"[DEBUG][liquidity] min_volume applied: {before} -> {len(candidates)}")
        if len(candidates) < before:
            warnings.append("LIQUIDITY_MIN_VOLUME_APPLIED")

    if not candidates:
        print("[DEBUG][liquidity] NO_CANDIDATES_AFTER_LIQUIDITY_FILTER")
        return PeerClusterResponse(
            industry_id=req.industry_id,
            anchor_stock_code=req.anchor_stock_code,
            freq=freq,
            window=req.window,
            peer_count=0,
            centroid=[],
            band=[],
            peers=[],
            as_of=datetime.now(timezone.utc),
            warnings=warnings + ["NO_CANDIDATES_AFTER_LIQUIDITY_FILTER"],
        )

    print(f"[DEBUG][liquidity] final candidates = {len(candidates)}")

    # -------------------------
    # 4) Score by corr + lead-lag
    # -------------------------
    peer_items: List[PeerItem] = []

    max_lag = int(getattr(req, "max_lag", 5) or 5)
    if max_lag < 1:
        max_lag = 1

    print(f"[DEBUG][score] max_lag = {max_lag}")

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

        best_lag, ll_corr = _best_lag_corr(ar, br, max_lag=max_lag, min_points=30)

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

        # Ranking score:
        # - 기본은 corr0, 보조로 lead_lag_corr 반영(리더/팔로워 편향은 없음)
        score: Optional[float] = None
        if corr0 is not None and ll_corr is not None:
            score = 0.7 * corr0 + 0.3 * ll_corr
        elif corr0 is not None:
            score = corr0
        elif ll_corr is not None:
            score = ll_corr

        print(
            f"[DEBUG][score][KEEP] {code}: "
            f"corr0={corr0}, best_lag={best_lag}, ll_corr={ll_corr}, "
            f"relation={relation}, score={score}"
        )

        peer_items.append(
            PeerItem(
                stock_code=code,
                company_name=company_name,
                avg_turnover=avg_turnover.get(code),
                avg_volume=avg_volume.get(code),
                corr=corr0,
                best_lag=best_lag,
                lead_lag_corr=ll_corr,
                relation=relation,  # type: ignore[arg-type]
                score=score,
            )
        )

    print(f"[DEBUG][score] peer_items count = {len(peer_items)}")

    if len(peer_items) < req.peer_count:
        warnings.append("INSUFFICIENT_PEERS_AFTER_SERIES_FILTER")
        print(
            f"[DEBUG][score] insufficient peers after filter: "
            f"{len(peer_items)} < {req.peer_count}"
        )

    if not peer_items:
        print("[DEBUG][score] NO_PEERS_AFTER_ALIGNMENT")
        return PeerClusterResponse(
            industry_id=req.industry_id,
            anchor_stock_code=req.anchor_stock_code,
            freq=freq,
            window=req.window,
            peer_count=0,
            centroid=[],
            band=[],
            peers=[],
            as_of=datetime.now(timezone.utc),
            warnings=warnings + ["NO_PEERS_AFTER_ALIGNMENT"],
        )

    peer_items.sort(
        key=lambda x: float(x.score) if x.score is not None else -999.0,
        reverse=True,
    )
    selected = peer_items[: req.peer_count]

    print("[DEBUG][score] selected peers:")
    for idx, p in enumerate(selected, start=1):
        print(
            f"  {idx}. {p.stock_code} "
            f"score={p.score}, corr={p.corr}, best_lag={p.best_lag}, relation={p.relation}"
        )

    # -------------------------
    # 5) centroid/band (rebased pct, common dates)
    # -------------------------
    common_dates = _np_dates(anchor_ps.dates)

    print(f"[DEBUG][chart] anchor_dates count = {len(common_dates)}")
    print(f"[DEBUG][chart] common init count = {len(common_dates)}")

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
        centroid=centroid,
        band=band,
        peers=selected,
        as_of=datetime.now(timezone.utc),
        warnings=warnings + ["MARKET_CAP_EXCLUDED_V1"],
    )