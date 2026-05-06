import type { AnalysisExplainSection, AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import type { PeerItem } from "../types/feature2";
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";
import DictTerm from "./DictTerm";
import FinancialTimelineChart from "./FinancialTimelineChart";
import MarketSnapshotBars from "./MarketSnapshotBars";
import IndicatorSnapshotCards from "./IndicatorSnapshotCards";
import PriceFlowBars from "./PriceFlowBars";
import ShortSellingTrendChart from "./ShortSellingTrendChart";
import BaseRateStepChart from "./BaseRateStepChart";

export const LLM_VENDOR_OPTIONS = [
  "GPT-5.4",
  "GPT-5.2",
  "GPT-5 mini",
  "GPT-4.1",
  "GPT-4o",
  "Gemini 3.1 Pro",
  "Gemini 3 Pro",
  "Gemini 3 Flash",
  "Gemini 3.1 Flash Lite",
  "Gemini 2.5 Flash",
  "Gemini 2.5 Pro",
  "Claude Opus 4.6",
  "Claude Opus 4.5",
  "Claude Sonnet 4.6",
  "Claude Sonnet 4",
  "Claude Haiku 4.5",
  "Grok 4",
  "Grok 4.1 Fast",
  "Grok 4 Fast",
  "Grok 3",
  "Grok 3 Mini",
] as const;

interface AnalysisResultPanelProps {
  result: AnalysisPanelResult | null;
  loading: boolean;
  loadingStage?: string;
  err: string;
  showAnalyzeButton: boolean;
  onAnalyze: () => void;
  onDownload: () => void;
  onZoom: () => void;
  llmVendor: string;
  onLlmVendorChange: (vendor: string) => void;
  displayText: string;
  financialTimeline?: FinancialTimelineSection | null;
  priceFlowSummary?: PriceFlowSummary | null;
  layout?: "full" | "panel";
}

type ExplainSection = AnalysisExplainSection;

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR");
};

const formatSentimentScore = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value > 0 ? `+${value.toFixed(2)}` : value.toFixed(2);
};

const formatSignedNumber = (value?: number | null, digits = 2) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value > 0 ? `+${value.toFixed(digits)}` : value.toFixed(digits);
};

const formatRatio = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
};

const trendDirectionText = (direction?: string | null) => {
  switch (direction) {
    case "UP":
      return "상승";
    case "DOWN":
      return "하락";
    case "FLAT":
      return "보합";
    default:
      return "확인중";
  }
};

const sentimentLabelText = (label?: string | null, score?: number | null) => {
  switch (label) {
    case "positive":
      return "긍정";
    case "negative":
      return "부정";
  }
  if (score != null && Number.isFinite(score)) {
    if (score > 0.05) return "긍정";
    if (score < -0.05) return "부정";
  }
  return "중립";
};

const sentimentBadgeClass = (score?: number | null) => {
  if (score == null || !Number.isFinite(score) || Math.abs(score) < 0.05) {
    return "bg-zinc-100 text-zinc-600 border-zinc-200";
  }
  if (score > 0) {
    return "bg-rose-50 text-rose-700 border-rose-200";
  }
  return "bg-blue-50 text-blue-700 border-blue-200";
};

const formatNewsDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return date.toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" });
};

const formatNewsTimeAgo = (value?: string | null): string => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return formatNewsDate(value);
  const diff = Date.now() - date.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${Math.max(0, mins)}분 전`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;
  return date.toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" });
};

const formatRelationBadge = (relation: PeerItem["relation"]) => {
  switch (relation) {
    case "LEADER":
      return "선행";
    case "FOLLOWER":
      return "후행";
    case "COINCIDENT":
      return "동행";
    default:
      return "중립";
  }
};

const correlationColorClass = (corr?: number | null): string => {
  if (corr == null || !Number.isFinite(corr)) return "text-ink-3";
  const abs = Math.abs(corr);
  if (abs >= 0.7) return "text-red-600";
  if (abs >= 0.5) return "text-orange-500";
  if (abs >= 0.3) return "text-amber-500";
  return "text-ink-3";
};

const displayPeerCorr = (peer: PeerItem): number | null => {
  if (peer.adjustedCorrValid && peer.adjustedCorr != null && Number.isFinite(peer.adjustedCorr)) {
    return peer.adjustedCorr;
  }
  return peer.corr == null || !Number.isFinite(peer.corr) ? null : peer.corr;
};

const displayPeerCorrLabel = (peer: PeerItem): string => {
  if (peer.adjustedCorrValid && !peer.rawCorrValid) return "산업조정 기준";
  if (peer.adjustedCorrValid) return "산업조정 상관";
  return "상관계수";
};

const formatPeerScore = (peer: PeerItem): string => {
  const value = peer.peerScore ?? peer.score;
  return value == null || !Number.isFinite(value) ? "-" : value.toFixed(2);
};

const formatDisplayStatus = (peer: PeerItem): string => {
  switch (peer.displayStatus) {
    case "SELECTED":
      return "클러스터 포함";
    case "ELIGIBLE_NOT_SELECTED":
      return "유사하지만 제외됨";
    case "LOW_CORR":
      return "상관 낮음";
    case "ADJUSTED_ONLY":
      return "산업조정 기준 유사";
    case "RAW_ONLY":
      return "원시 상관 기준";
    case "FALLBACK_RAW":
      return "원시 상관 대체";
    default:
      return "참고 후보";
  }
};

const peerCardClass = (peer: PeerItem): string => {
  switch (peer.displayStatus) {
    case "SELECTED":
      return "border-sky-300 bg-sky-50/70";
    case "LOW_CORR":
      return "border-line bg-bg-sunk opacity-75";
    case "DISPLAY_ONLY":
    case "RAW_ONLY":
    case "ADJUSTED_ONLY":
    case "FALLBACK_RAW":
      return "border-line bg-surface";
    default:
      return "border-line bg-surface";
  }
};

const relationColorClass = (relation: PeerItem["relation"]): string => {
  switch (relation) {
    case "LEADER":
      return "text-green-600";
    case "FOLLOWER":
      return "text-purple-600";
    case "COINCIDENT":
      return "text-ink";
    default:
      return "text-ink-3";
  }
};

const renderExplainSection = (section?: ExplainSection | null) => {
  if (!section || (!section.summary && (!section.bullets || section.bullets.length === 0))) {
    return null;
  }

  return (
    <div className="mt-3 rounded-2xl border border-line bg-bg-sunk px-4 py-4">
      <h4 className="text-sm font-semibold text-ink">
        {section.title ?? "설명"}
      </h4>
      {section.summary ? (
        <p className="mt-2 text-sm sm:text-base text-ink-2">{section.summary}</p>
      ) : null}
      {section.bullets && section.bullets.length > 0 ? (
        <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-ink-2 flex flex-col gap-1">
          {section.bullets.map((item, idx) => (
            <li key={`${section.title ?? "section"}-${idx}`}>{item}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
};

const WARNING_MESSAGE_MAP: Array<[RegExp, string]> = [
  [/^MARKET_SNAPSHOT_PARTIAL$/, "일부 투자지표는 데이터가 부족해 비어 있을 수 있어요.\n현재 보고서는 위쪽에 출력된 투자 지표들 기반으로 분석됐으니 참고해주세요."],
  [/^INDICATOR_CALC_FAILED$/, "일부 보조지표는 분석 구간 또는 데이터 상태에 따라 계산되지 않을 수 있어요."],
  [/^TTM_FALLBACK_TO_ANNUAL$/, "최근 4개 분기 데이터가 부족해 일부 지표는 연간 기준으로 보정될 수 있어요."],
  [/^MARKET_SNAPSHOT_TTM_FALLBACK_TO_ANNUAL$/, "각 분기의 데이터가 부족해 일부 투자지표는 연간 실적 기준으로 계산될 수 있어요."],
  [/^PRICE_STALE_USED$/, "실시간 가격 대신 최근 캐시 가격이 사용될 수 있어요."],
  [/^PRICE_FETCH_FAILED$/, "실시간 가격을 가져오지 못해 일부 가격 기반 지표가 정확하지 않을 수 있어요."],
  [/^PEER_CLUSTER_LOW_POSITIVE_CORR_CANDIDATES$/, "양의 동행성이 충분한 유사 종목 후보가 적어 표시 종목 수가 줄어들 수 있어요."],
  [/^INDUSTRY_ADJUSTED_RETURN_FALLBACK_RAW$/, "산업지수 보정 수익률을 계산할 수 없어 원 수익률 기준으로 유사 종목을 비교했어요."],
  [/^PEER_CORR_STABILITY_INSUFFICIENT_DATA$/, "구간별 상관 안정성을 판단하기에는 일부 종목의 데이터가 부족할 수 있어요."],
  [/^PEER_FILTER_RELAXED$/, "요청한 유사 종목 수를 평가하기 위해 극단값 필터를 완화했어요."],
  [/^PEER_COUNT_REDUCED_BY_CANDIDATE_SIZE$/, "동일 산업 내 비교 가능한 후보 수가 요청한 종목 수보다 적어 실제 표시 수가 줄었어요."],
  [/^NEWS_FILTER_(APPLIED|EXPANDED_FETCH)$/, "종목 관련성이 높은 뉴스를 선별하기 위해 뉴스 검색 범위를 조정했어요."],
  [/^NEWS_FILTER_INSUFFICIENT_RESULT$/, "조건에 맞는 관련 뉴스 수가 충분하지 않아 일부 뉴스 지표가 제한될 수 있어요."],
  [/^NEWS_BODY_LOW_CONFIDENCE:/, "일부 뉴스는 본문 추출 신뢰도가 낮아 감성 점수 해석에 주의가 필요해요."],
  [/^NEWS_BODY_FETCH_FAILED:/, "일부 뉴스는 본문을 가져오지 못해 감성 분석 대상에서 제외됐어요."],
  [/^NEWS_SENTIMENT_LOCAL_/, "뉴스 감성 모델 처리 중 일부 결과가 제한됐어요."],
  [/^NEWS_SENTIMENT_FAILED:/, "일부 뉴스는 감성 점수를 산출하지 못했어요."],
  [/^NEWS_LIST_FETCH_FAILED$/, "뉴스 목록을 가져오지 못해 뉴스 기반 분석이 제한될 수 있어요."],
  [/^NEWS_INVALID_ITEM_SKIPPED$/, "형식이 맞지 않는 뉴스 항목 일부를 제외했어요."],
  [/^NEWS_PUBDATE_PARSE_FAILED$/, "일부 뉴스 발행시각을 해석하지 못해 현재 시각 기준으로 보정될 수 있어요."],
  [/^LLM_EXPLAIN_TIMEOUT$/, "설명 생성이 지연되어 일부 해설이 생략될 수 있어요."],
  [/^LLM_EXPLAIN_RATE_LIMITED$/, "설명 생성 요청이 많아 해설 생성이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_MAX_OUTPUT_TOKENS$/, "설명 생성 분량 제한으로 일부 해설이 축약될 수 있어요."],
  [/^LLM_EXPLAIN_PARSE_FAILED$/, "설명 생성 결과를 구조화하는 과정에서 일부 내용이 누락될 수 있어요."],
  [/^LLM_EXPLAIN_HTTP_500/, "설명 생성 서버가 일시적으로 불안정해 일부 해설이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_HTTP_/, "설명 생성 중 일시적인 오류가 발생해 일부 해설이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_INCOMPLETE:/, "설명 생성이 중간에 종료되어 일부 해설이 축약될 수 있어요."],
  [/^LLM_EXPLAIN_REFUSAL$/, "설명 생성 정책에 따라 일부 응답이 제한될 수 있어요."],
];

const mapWarningsToNotes = (warnings?: string[] | null): string[] => {
  if (!warnings || warnings.length === 0) return [];

  const messages = warnings.flatMap((warning) => {
    for (const [pattern, message] of WARNING_MESSAGE_MAP) {
      if (pattern.test(warning)) {
        return [message];
      }
    }
    return [];
  });

  return Array.from(new Set(messages));
};

const expandWarningLines = (notes: string[]): string[] =>
  notes.flatMap((note) =>
    note
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean),
  );

export default function AnalysisResultPanel({
  result,
  loading,
  loadingStage,
  err,
  showAnalyzeButton,
  onAnalyze,
  onDownload,
  onZoom,
  llmVendor,
  onLlmVendorChange,
  displayText,
  financialTimeline = null,
  priceFlowSummary = null,
  layout = "full",
}: AnalysisResultPanelProps) {
  const isPanel = layout === "panel";
  const explainSections = result?.explain?.sections ?? null;
  const overallExplain = result?.explain?.overall ?? null;
  const warningNotes = expandWarningLines(mapWarningsToNotes(result?.warnings));

  // --- 래퍼 클래스 ---
  const wrapperClass = isPanel
    ? result
      ? "w-full bg-bg-sunk rounded-2xl border-2 border-line flex flex-col items-center py-6 sm:py-8 gap-4"
      : "w-full bg-bg-sunk rounded-2xl border-2 border-line flex flex-col items-center justify-center py-[15rem] sm:py-[16rem] md:py-[17rem]"
    : "w-full bg-bg-sunk rounded-2xl py-8 sm:py-10 flex flex-col items-center gap-4 mt-2";

  return (
    <div className={wrapperClass}>
      {/* 상단 헤더 바: 다운로드/확대 버튼 — result 있을 때만 */}
      {result && (
        <div className="w-[90%] max-w-4xl flex items-center justify-end">
          <div className="flex items-center gap-3">
            <button onClick={onDownload} className="w-7 h-7 sm:w-8 sm:h-8">
              <img
                src={downloadIcon}
                alt="다운로드"
                className="w-full h-full object-contain"
              />
            </button>
            <button onClick={onZoom} className="w-6 h-6 sm:w-7 sm:h-7">
              <img
                src={zoomIcon}
                alt="확대"
                className="w-full h-full object-contain"
              />
            </button>
          </div>
        </div>
      )}

      {/* LLM 모델 선택 + 분석 실행 버튼 */}
      {showAnalyzeButton && (
        <div className="flex flex-col items-center gap-3">
          {/* LLM 모델 선택 드롭다운 */}
          <div className="flex items-center gap-2">
            <label className="text-sm text-ink-3 font-medium whitespace-nowrap">
              분석 모델
            </label>
            <select
              value={llmVendor}
              onChange={(e) => onLlmVendorChange(e.target.value)}
              className="px-3 py-1.5 text-sm border border-line rounded-lg bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent cursor-pointer"
            >
              {LLM_VENDOR_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>

          {/* 분석 실행 버튼 */}
          <button
            onClick={onAnalyze}
            disabled={loading}
            className="px-6 sm:px-8 py-2.5 bg-accent rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
          >
            분석 결과 보기
          </button>
        </div>
      )}

      {/* 로딩 문구 */}
      {loading && (
        <div className="flex flex-col items-center gap-3 text-ink-3">
          <div className="h-10 w-10 rounded-full border-4 border-accent-soft border-t-accent animate-spin" />
          <p className="text-sm sm:text-base font-medium">분석 중입니다...</p>
          <p className="text-xs sm:text-sm text-ink-3">
            {loadingStage ?? "분석 데이터를 준비하고 있습니다."}
          </p>
        </div>
      )}

      {/* 에러 문구 */}
      {err && <p className="text-sm sm:text-base text-danger">{err}</p>}

      {/* 분석 결과 카드 */}
      {result && (
        <div className="w-[90%] max-w-4xl bg-surface rounded-2xl shadow-sm border border-line p-4 sm:p-6 flex flex-col gap-4">
          {result.metrics?.stock && (
            <div className="rounded-lg border border-line bg-bg-sunk px-4 py-3">
              <p className="text-xs sm:text-sm font-medium text-ink-3">분석 종목</p>
              <p className="mt-1 text-lg sm:text-xl font-bold text-ink">
                {result.metrics.stock.companyName || result.metrics.stock.stockCode}
              </p>
            </div>
          )}

          {result.metrics?.peerCluster && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">유사 종목 반응 구조</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  {result.metrics.peerCluster.peers.length}개 선정
                </span>
              </div>
              <p className="mt-1 text-xs sm:text-sm text-ink-3">
                산업조정 상관은 산업 공통 움직임을 단순 차감한 관측용 지표이며, 정교한 요인 모델이나 가격 방향 신호가 아닙니다.
              </p>
              {(() => {
                const selectedCodes = new Set(result.metrics?.peerCluster?.peers.map((peer) => peer.stockCode) ?? []);
                const candidates = result.metrics?.peerCluster?.candidates?.length
                  ? result.metrics.peerCluster.candidates
                  : result.metrics?.peerCluster?.peers ?? [];
                const selected = candidates.filter((peer) => peer.displayStatus === "SELECTED" || selectedCodes.has(peer.stockCode));
                const extra = candidates.filter((peer) => !(peer.displayStatus === "SELECTED" || selectedCodes.has(peer.stockCode)));
                const renderRows = (peers: PeerItem[]) => (
                  <div className="grid grid-cols-1 gap-2">
                    {peers.map((peer) => (
                      <div key={peer.stockCode} className={`rounded-lg border px-4 py-3 ${peerCardClass(peer)}`}>
                        <div className="flex flex-wrap items-center gap-3">
                          <div className="min-w-0 w-[120px] sm:w-[170px] flex-shrink-0">
                            <p className="text-sm sm:text-base font-semibold text-ink truncate">{peer.companyName ?? "-"}</p>
                            <p className="text-[11px] sm:text-xs text-ink-4">{peer.stockCode}</p>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[80px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">{displayPeerCorrLabel(peer)}</span>
                            <span className={`text-sm sm:text-base font-bold ${correlationColorClass(displayPeerCorr(peer))}`}>
                              {displayPeerCorr(peer) == null ? "-" : displayPeerCorr(peer)?.toFixed(2)}
                            </span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[70px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">관계</span>
                            <span className={`text-sm sm:text-base font-semibold ${relationColorClass(peer.relation)}`}>
                              {formatRelationBadge(peer.relation)}
                            </span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[70px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">점수</span>
                            <span className="text-sm sm:text-base font-bold text-ink">{formatPeerScore(peer)}</span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[92px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">상태</span>
                            <span className="text-xs sm:text-sm font-medium text-ink-2">{formatDisplayStatus(peer)}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                );

                return (
                  <div className="mt-3 max-h-[420px] overflow-y-auto rounded-lg border border-line bg-bg-sunk/60 p-3 pr-2">
                    <div className="flex flex-col gap-4">
                      <div>
                        <h4 className="text-sm font-semibold text-ink">핵심 유사 종목</h4>
                        <div className="mt-2">{selected.length > 0 ? renderRows(selected) : <p className="text-sm text-ink-3">선정된 유사 종목이 없습니다.</p>}</div>
                      </div>
                      {extra.length > 0 && (
                        <div>
                          <h4 className="text-sm font-semibold text-ink">추가 후보</h4>
                          <div className="mt-2">{renderRows(extra)}</div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })()}
              {renderExplainSection(explainSections?.peerCluster)}
            </div>
          )}

          {result.metrics?.newsSentimentSummary && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">뉴스 감성</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  감성 점수 산출 뉴스 {result.metrics.newsSentimentSummary.scoredNewsCount}건
                </span>
              </div>
              <div className="mt-2 grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">일 평균 점수</p>
                  <p className={`text-lg font-bold ${result.metrics.newsSentimentSummary.dailyAvgScore != null && result.metrics.newsSentimentSummary.dailyAvgScore < -0.05 ? "text-blue-700" : result.metrics.newsSentimentSummary.dailyAvgScore != null && result.metrics.newsSentimentSummary.dailyAvgScore > 0.05 ? "text-rose-700" : "text-ink"}`}>
                    {formatSentimentScore(result.metrics.newsSentimentSummary.dailyAvgScore)}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">점수 산출 뉴스</p>
                  <p className="text-lg font-bold text-ink">{result.metrics.newsSentimentSummary.scoredNewsCount}</p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">긍정/중립/부정</p>
                  <p className="text-sm font-semibold text-ink">
                    {result.metrics.newsSentimentSummary.positiveCount} / {result.metrics.newsSentimentSummary.neutralCount} / {result.metrics.newsSentimentSummary.negativeCount}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">기준일</p>
                  <p className="text-sm font-semibold text-ink">{result.metrics.newsSentimentSummary.summaryDate ?? "-"}</p>
                </div>
              </div>

              {(result.metrics?.newsList?.length ?? 0) > 0 && (
                <div className="mt-4">
                  <h4 className="text-sm font-semibold text-ink">감성 점수가 산출된 뉴스</h4>
                  <div className="mt-2 max-h-[360px] divide-y divide-line overflow-y-auto rounded-lg border border-line bg-surface pr-1">
                    {result.metrics?.newsList?.map((item, idx) => (
                      <a
                        key={item.newsId}
                        href={item.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-4 px-3 py-3 hover:bg-bg-sunk transition-colors ${
                          idx === 0 ? "rounded-t-lg" : ""
                        } ${idx === (result.metrics?.newsList?.length ?? 0) - 1 ? "rounded-b-lg" : ""}`}
                      >
                        <div className="min-w-0 flex-1 flex flex-col gap-2">
                          <div className="flex flex-col">
                            <h5 className="truncate text-sm sm:text-base font-semibold text-ink">
                              {item.title}
                            </h5>
                            <p className="line-clamp-2 text-xs sm:text-sm leading-snug text-ink-2">
                              {item.summary}
                            </p>
                          </div>
                          <p className="text-[11px] sm:text-xs font-medium text-ink-3">
                            {formatNewsTimeAgo(item.publishedAt)} · {item.publisher}
                          </p>
                        </div>
                        <div className={`flex min-w-[64px] flex-col items-center justify-center rounded-md border px-2 py-1 text-xs font-semibold ${sentimentBadgeClass(item.sentimentScore)}`}>
                          <span>{sentimentLabelText(item.sentimentLabel, item.sentimentScore)}</span>
                          <span>{formatSentimentScore(item.sentimentScore)}</span>
                        </div>
                      </a>
                    ))}
                  </div>
                </div>
              )}
              {renderExplainSection(explainSections?.newsSentiment)}
            </div>
          )}

          {(result.metrics?.baseRateTrendSummary || result.metrics?.shortSellingTrendSummary) && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">기간 추이 요약</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  사용자 설정 기간 기준
                </span>
              </div>
              <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
                {result.metrics?.baseRateTrendSummary && (
                  <div className="rounded-lg border border-line bg-bg-sunk px-4 py-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-ink">기준금리</p>
                      <span className="text-xs font-medium text-ink-3">
                        최근 {result.metrics.baseRateTrendSummary.window}일
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-ink-2">
                      {result.metrics.baseRateTrendSummary.startValue ?? "-"}
                      {result.metrics.baseRateTrendSummary.unit ?? ""} → {result.metrics.baseRateTrendSummary.endValue ?? "-"}
                      {result.metrics.baseRateTrendSummary.unit ?? ""}
                    </p>
                    <p className="mt-1 text-xs text-ink-3">
                      변동 {formatSignedNumber(result.metrics.baseRateTrendSummary.change)} · {trendDirectionText(result.metrics.baseRateTrendSummary.direction)}
                    </p>
                  </div>
                )}
                {result.metrics?.shortSellingTrendSummary && (
                  <div className="rounded-lg border border-line bg-bg-sunk px-4 py-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-ink">공매도</p>
                      <span className="text-xs font-medium text-ink-3">
                        최근 {result.metrics.shortSellingTrendSummary.window}일
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-ink-2">
                      거래대금 비율 {formatRatio(result.metrics.shortSellingTrendSummary.startShortAmountRatio)}
                      {" → "}
                      {formatRatio(result.metrics.shortSellingTrendSummary.endShortAmountRatio)}
                    </p>
                    <p className="mt-1 text-xs text-ink-3">
                      변동 {formatSignedNumber(result.metrics.shortSellingTrendSummary.shortAmountRatioChange)}% · 평균 {formatRatio(result.metrics.shortSellingTrendSummary.avgShortAmountRatio)} · {trendDirectionText(result.metrics.shortSellingTrendSummary.direction)}
                    </p>
                  </div>
                )}
              </div>
              {renderExplainSection(explainSections?.trendSummary)}
            </div>
          )}

          {/* 기준금리 */}
          {result.metrics?.baseRateSeries && result.metrics.baseRateSeries.length > 0 ? (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">
                  <DictTerm term="기준금리">기준금리</DictTerm> 추이
                </h3>
              </div>
              <BaseRateStepChart points={result.metrics.baseRateSeries} />
              {renderExplainSection(explainSections?.baseRate)}
            </div>
          ) : result.metrics?.baseRate ? (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">
                  <DictTerm term="기준금리">기준금리</DictTerm>
                </h3>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm sm:text-base text-ink-2 mt-2">
                <div>기준일: {result.metrics.baseRate.date}</div>
                <div>금리: {result.metrics.baseRate.value}{result.metrics.baseRate.unit}</div>
              </div>
              {renderExplainSection(explainSections?.baseRate)}
            </div>
          ) : null}

          {/* 공매도 현황 */}
          {result.metrics?.shortSellingSeries && result.metrics.shortSellingSeries.length > 0 ? (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">
                  <DictTerm term="공매도">공매도</DictTerm> 추이
                </h3>
              </div>
              <ShortSellingTrendChart points={result.metrics.shortSellingSeries} />
              {renderExplainSection(explainSections?.shortSelling)}
            </div>
          ) : result.metrics?.shortSelling ? (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">
                  <DictTerm term="공매도">공매도</DictTerm> 현황
                </h3>
              </div>
              <div className="overflow-x-auto mt-2">
                <table className="min-w-full text-xs sm:text-sm text-ink-2 border border-line">
                  <thead className="bg-bg-sunk text-ink">
                    <tr>
                      <th className="px-3 py-2 text-left border-b">항목</th>
                      <th className="px-3 py-2 text-right border-b">값</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className="px-3 py-2 border-b">기준일</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.reportDate}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">공매도 거래량</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.shortVolumeTotal)}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b"><DictTerm term="거래량">거래량</DictTerm> (총)</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.totalVolume)}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">공매도 거래량 비율</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.shortVolumeRatio.toFixed(2)}%</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">공매도 거래대금</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.shortAmountTotal)}원</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b"><DictTerm term="거래대금">거래대금</DictTerm> (총)</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.totalAmount)}원</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">공매도 거래대금 비율</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.shortAmountRatio.toFixed(2)}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              {renderExplainSection(explainSections?.shortSelling)}
            </div>
          ) : null}

          {/* 가격 흐름 요약 */}
          {priceFlowSummary && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <PriceFlowBars summary={priceFlowSummary} />
              {renderExplainSection(explainSections?.priceFlow)}
            </div>
          )}

          {/* 시장 스냅샷 */}
          {result.metrics?.marketSnapshot && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <MarketSnapshotBars snapshot={result.metrics.marketSnapshot} />
              {renderExplainSection(explainSections?.marketSnapshot)}
            </div>
          )}

          {/* 보조지표 요약 */}
          {result.metrics?.indicators && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <IndicatorSnapshotCards
                indicators={result.metrics.indicators}
              />
              {renderExplainSection(explainSections?.indicators)}
            </div>
          )}

          {financialTimeline && financialTimeline.points.length > 0 && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <FinancialTimelineChart
                period={financialTimeline.period}
                points={financialTimeline.points}
              />
              {renderExplainSection(explainSections?.financialTimeline)}
            </div>
          )}

          {(overallExplain?.summary
            || (overallExplain?.bullets && overallExplain.bullets.length > 0)
            || (overallExplain?.risks && overallExplain.risks.length > 0)
            || overallExplain?.conclusion
            || result.explain?.text?.trim()) && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-ink">
                종합 요약
              </h3>

              {overallExplain ? (
                <div className="mt-2 text-sm sm:text-base text-ink-2 flex flex-col gap-3">
                  {overallExplain.summary ? (
                    <div>
                      <p className="font-medium text-ink">요약</p>
                      <p>{overallExplain.summary}</p>
                    </div>
                  ) : null}
                  {overallExplain.bullets && overallExplain.bullets.length > 0 ? (
                    <div>
                      <p className="font-medium text-ink">핵심 포인트</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.bullets.map((item, idx) => (
                          <li key={`overall-bullet-${idx}`}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.risks && overallExplain.risks.length > 0 ? (
                    <div>
                      <p className="font-medium text-ink">리스크</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.risks.map((item, idx) => (
                          <li key={`overall-risk-${idx}`}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.conclusion ? (
                    <div>
                      <p className="font-medium text-ink">결론</p>
                      <p>{overallExplain.conclusion}</p>
                    </div>
                  ) : null}
                </div>
              ) : result.explain?.text?.trim() ? (
                <p className="text-sm sm:text-base text-ink-2 whitespace-pre-wrap mt-2">
                  {displayText}
                </p>
              ) : null}
            </div>
          )}

          {warningNotes.length > 0 && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-ink">
                참고
              </h3>
              <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-ink-2 flex flex-col gap-1">
                {warningNotes.map((item, idx) => (
                  <li key={`warning-note-${idx}`}>{item}</li>
                ))}
              </ul>
            </div>
          )}

        </div>
      )}
    </div>
  );
}
