import type { AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";
import DictTerm from "./DictTerm";
import FinancialTimelineChart from "./FinancialTimelineChart";
import MarketSnapshotBars from "./MarketSnapshotBars";
import IndicatorSnapshotCards from "./IndicatorSnapshotCards";
import PriceFlowBars from "./PriceFlowBars";

const LLM_VENDOR_OPTIONS = [
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

type ExplainSection = NonNullable<NonNullable<AnalysisPanelResult["explain"]>["sections"]>["priceFlow"];

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR");
};

const renderExplainSection = (section?: ExplainSection | null) => {
  if (!section || (!section.summary && (!section.bullets || section.bullets.length === 0))) {
    return null;
  }

  return (
    <div className="mt-3 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4">
      <h4 className="text-sm font-semibold text-zinc-900">
        {section.title ?? "설명"}
      </h4>
      {section.summary ? (
        <p className="mt-2 text-sm sm:text-base text-zinc-700">{section.summary}</p>
      ) : null}
      {section.bullets && section.bullets.length > 0 ? (
        <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-zinc-700 flex flex-col gap-1">
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

  // --- wrapper class ---
  const wrapperClass = isPanel
    ? result
      ? "w-full bg-neutral-50 rounded-2xl border-2 border-stone-300 flex flex-col items-center py-6 sm:py-8 gap-4"
      : "w-full bg-neutral-50 rounded-2xl border-2 border-stone-300 flex flex-col items-center justify-center py-[15rem] sm:py-[16rem] md:py-[17rem]"
    : "w-full bg-zinc-100 rounded-2xl py-8 sm:py-10 flex flex-col items-center gap-4 mt-2";

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
            <label className="text-sm text-zinc-600 font-medium whitespace-nowrap">
              분석 모델
            </label>
            <select
              value={llmVendor}
              onChange={(e) => onLlmVendorChange(e.target.value)}
              className="px-3 py-1.5 text-sm border border-zinc-300 rounded-lg bg-white text-zinc-800 focus:outline-none focus:ring-2 focus:ring-sky-500 cursor-pointer"
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
            className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
          >
            분석 결과 보기
          </button>
        </div>
      )}

      {/* 로딩 문구 */}
      {loading && (
        <div className="flex flex-col items-center gap-3 text-gray-600">
          <div className="h-10 w-10 rounded-full border-4 border-sky-200 border-t-sky-700 animate-spin" />
          <p className="text-sm sm:text-base font-medium">분석 중입니다...</p>
          <p className="text-xs sm:text-sm text-zinc-500">
            {loadingStage ?? "분석 데이터를 준비하고 있습니다."}
          </p>
        </div>
      )}

      {/* 에러 문구 */}
      {err && <p className="text-sm sm:text-base text-red-600">{err}</p>}

      {/* 분석 결과 카드 */}
      {result && (
        <div className="w-[90%] max-w-4xl bg-white rounded-2xl shadow-sm border border-zinc-200 p-4 sm:p-6 flex flex-col gap-4">
          {/* 가격 흐름 요약 */}
          {priceFlowSummary && (
            <div>
              <PriceFlowBars summary={priceFlowSummary} />
              {renderExplainSection(explainSections?.priceFlow)}
            </div>
          )}

          {/* Market Snapshot */}
          {result.metrics?.marketSnapshot && (
            <div>
              <MarketSnapshotBars snapshot={result.metrics.marketSnapshot} />
              {renderExplainSection(explainSections?.marketSnapshot)}
            </div>
          )}

          {/* Indicator Summary */}
          {result.metrics?.indicators && (
            <div>
              <IndicatorSnapshotCards
                indicators={result.metrics.indicators}
              />
              {renderExplainSection(explainSections?.indicators)}
            </div>
          )}

          {financialTimeline && financialTimeline.points.length > 0 && (
            <div>
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
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                종합 요약
              </h3>

              {overallExplain ? (
                <div className="mt-2 text-sm sm:text-base text-zinc-700 flex flex-col gap-3">
                  {overallExplain.summary ? (
                    <div>
                      <p className="font-medium text-zinc-900">요약</p>
                      <p>{overallExplain.summary}</p>
                    </div>
                  ) : null}
                  {overallExplain.bullets && overallExplain.bullets.length > 0 ? (
                    <div>
                      <p className="font-medium text-zinc-900">핵심 포인트</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.bullets.map((item, idx) => (
                          <li key={`overall-bullet-${idx}`}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.risks && overallExplain.risks.length > 0 ? (
                    <div>
                      <p className="font-medium text-zinc-900">리스크</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.risks.map((item, idx) => (
                          <li key={`overall-risk-${idx}`}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.conclusion ? (
                    <div>
                      <p className="font-medium text-zinc-900">결론</p>
                      <p>{overallExplain.conclusion}</p>
                    </div>
                  ) : null}
                </div>
              ) : result.explain?.text?.trim() ? (
                <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                  {displayText}
                </p>
              ) : null}
            </div>
          )}

          {warningNotes.length > 0 && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                참고
              </h3>
              <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-zinc-700 flex flex-col gap-1">
                {warningNotes.map((item, idx) => (
                  <li key={`warning-note-${idx}`}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {/* 기준금리 */}
          {result.metrics?.baseRate && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                <DictTerm term="기준금리">기준금리</DictTerm>
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm sm:text-base text-zinc-700 mt-2">
                <div>기준일: {result.metrics.baseRate.date}</div>
                <div>금리: {result.metrics.baseRate.value}{result.metrics.baseRate.unit}</div>
              </div>
            </div>
          )}

          {/* 공매도 현황 */}
          {result.metrics?.shortSelling && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                <DictTerm term="공매도">공매도</DictTerm> 현황
              </h3>
              <div className="overflow-x-auto mt-2">
                <table className="min-w-full text-xs sm:text-sm text-zinc-700 border border-zinc-200">
                  <thead className="bg-zinc-100 text-zinc-900">
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
            </div>
          )}

          {/* 경고 */}
          {(result.meta?.warnings ?? []).length > 0 && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                경고
              </h3>
              <ul className="text-sm sm:text-base text-amber-700 mt-2 list-disc list-inside">
                {result.meta?.warnings?.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
