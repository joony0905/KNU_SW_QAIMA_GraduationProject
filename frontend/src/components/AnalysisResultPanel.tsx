import { useState } from "react";
import type { AnalysisPanelResult } from "../types/analysisPanel";
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";

interface AnalysisResultPanelProps {
  result: AnalysisPanelResult | null;
  loading: boolean;
  err: string;
  showAnalyzeButton: boolean;
  onAnalyze: () => void;
  onDownload: () => void;
  onZoom: () => void;
  displayText: string;
  layout?: "full" | "panel";
}

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined) return "-";
  return value.toLocaleString();
};

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
};

export default function AnalysisResultPanel({
  result,
  loading,
  err,
  showAnalyzeButton,
  onAnalyze,
  onDownload,
  onZoom,
  displayText,
  layout = "full",
}: AnalysisResultPanelProps) {
  const isPanel = layout === "panel";
  const [llmVendor, setLlmVendor] = useState<"gemini" | "grok" | "gpt-4o">("gemini");

  const parsed = result?.explain?.parsed ?? null;

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
              onChange={(e) => setLlmVendor(e.target.value as "gemini" | "grok" | "gpt-4o")}
              className="px-3 py-1.5 text-sm border border-zinc-300 rounded-lg bg-white text-zinc-800 focus:outline-none focus:ring-2 focus:ring-sky-500 cursor-pointer"
            >
              <option value="gemini">Gemini 2.5 Flash</option>
              <option value="grok">Grok</option>
              <option value="gpt-4o">GPT-4o</option>
            </select>
          </div>

          {/* 분석 실행 버튼 */}
          <button
            onClick={onAnalyze}
            className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
          >
            분석 결과 보기
          </button>
        </div>
      )}

      {/* 로딩 문구 */}
      {loading && (
        <p className="text-sm sm:text-base text-gray-600">분석 중입니다...</p>
      )}

      {/* 에러 문구 */}
      {err && <p className="text-sm sm:text-base text-red-600">{err}</p>}

      {/* 분석 결과 카드 */}
      {result && (
        <div className="w-[90%] max-w-4xl bg-white rounded-2xl shadow-sm border border-zinc-200 p-4 sm:p-6 flex flex-col gap-4">
          {/* 설명 섹션 */}
          <div>
            <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
              설명
            </h3>

            {parsed ? (
              <div className="mt-2 text-sm sm:text-base text-zinc-700 flex flex-col gap-2">
                <div>
                  <p className="font-medium text-zinc-900">요약</p>
                  <ul className="list-disc list-inside">
                    {(parsed.summary ?? []).slice(0, 3).map((item, idx) => (
                      <li key={`summary-${idx}`}>{item}</li>
                    ))}
                  </ul>
                </div>
                <div>
                  <p className="font-medium text-zinc-900">리스크</p>
                  <ul className="list-disc list-inside">
                    {(parsed.risks ?? []).slice(0, 2).map((item, idx) => (
                      <li key={`risk-${idx}`}>{item}</li>
                    ))}
                  </ul>
                </div>
                <div>
                  <p className="font-medium text-zinc-900">결론</p>
                  <p>{parsed.conclusion ?? "-"}</p>
                </div>
              </div>
            ) : result.explain?.text?.trim() ? (
              <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                {displayText}
              </p>
            ) : (
              <p className="text-sm sm:text-base text-zinc-500 mt-2">
                설명 생성이 비활성화되었거나 실패했습니다.
              </p>
            )}
          </div>

          {/* OHLCV 요약 */}
          {result.metrics?.ohlcvSummary && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                OHLCV 요약
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm sm:text-base text-zinc-700 mt-2">
                <div>
                  캔들 수:{" "}
                  {result.metrics.ohlcvSummary.count.toLocaleString()}
                </div>
                <div>
                  기간: {formatDate(result.metrics.ohlcvSummary.from)} ~{" "}
                  {formatDate(result.metrics.ohlcvSummary.to)}
                </div>
                <div>
                  마지막 종가:{" "}
                  {formatNumber(result.metrics.ohlcvSummary.lastClose)}
                </div>
              </div>
            </div>
          )}

          {/* Indicator Summary */}
          {result.metrics?.indicatorSummary !== undefined && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                Indicator Summary
              </h3>
              <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                {result.metrics.indicatorSummary?.trim()
                  ? result.metrics.indicatorSummary
                  : "지표 요약 데이터를 생성하지 못했습니다."}
              </p>
            </div>
          )}

          {/* 재무 요약 (5개년) */}
          {result.metrics?.financialSummary && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                재무 요약 (5개년)
              </h3>
              {result.metrics.financialSummary.years.length > 0 ? (
                <div className="overflow-x-auto mt-2">
                  <table className="min-w-full text-xs sm:text-sm text-zinc-700 border border-zinc-200">
                    <thead className="bg-zinc-100 text-zinc-900">
                      <tr>
                        <th className="px-3 py-2 text-left border-b">구분</th>
                        {result.metrics.financialSummary.years.map((year) => (
                          <th
                            key={year}
                            className="px-3 py-2 text-right border-b"
                          >
                            {year}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td className="px-3 py-2 border-b">매출</td>
                        {result.metrics.financialSummary.years.map((year) => (
                          <td
                            key={`revenue-${year}`}
                            className="px-3 py-2 text-right border-b"
                          >
                            {formatNumber(
                              result.metrics!.financialSummary!.revenue[
                                String(year)
                              ],
                            )}
                          </td>
                        ))}
                      </tr>
                      <tr>
                        <td className="px-3 py-2 border-b">영업이익</td>
                        {result.metrics.financialSummary.years.map((year) => (
                          <td
                            key={`op-${year}`}
                            className="px-3 py-2 text-right border-b"
                          >
                            {formatNumber(
                              result.metrics!.financialSummary!
                                .operatingIncome[String(year)],
                            )}
                          </td>
                        ))}
                      </tr>
                      <tr>
                        <td className="px-3 py-2 border-b">순이익</td>
                        {result.metrics.financialSummary.years.map((year) => (
                          <td
                            key={`net-${year}`}
                            className="px-3 py-2 text-right border-b"
                          >
                            {formatNumber(
                              result.metrics!.financialSummary!.netIncome[
                                String(year)
                              ],
                            )}
                          </td>
                        ))}
                      </tr>
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-sm sm:text-base text-zinc-500 mt-2">
                  재무 요약 데이터를 확보하지 못했습니다.
                </p>
              )}
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
