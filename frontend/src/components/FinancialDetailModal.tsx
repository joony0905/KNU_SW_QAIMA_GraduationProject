import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import type { FinancialDto } from "../types/financial";
import { fetchFinancials } from "../api/financial";
import DictTerm from "./DictTerm";

type Props = {
  isOpen: boolean;
  onClose: () => void;
  ticker: string;
  companyName: string;
};

type PeriodTab = "A" | "Q" | "H";

const PERIOD_YEARS: Record<PeriodTab, number> = {
  A: 5,
  Q: 3,
  H: 3,
};

const isEnglish = (language?: string | null) => (language ?? "").toLowerCase().startsWith("en");

const fmtWon = (v: number | null | undefined, language?: string | null) => {
  if (v == null) return "-";
  if (isEnglish(language)) {
    if (Math.abs(v) >= 1e12) return `${(v / 1e12).toFixed(2)}T KRW`;
    if (Math.abs(v) >= 1e9) return `${(v / 1e9).toFixed(2)}B KRW`;
    if (Math.abs(v) >= 1e6) return `${(v / 1e6).toFixed(2)}M KRW`;
    return `${v.toLocaleString("en-US")} KRW`;
  }
  if (Math.abs(v) >= 1e12) return `${(v / 1e12).toFixed(2)}조`;
  if (Math.abs(v) >= 1e8) return `${(v / 1e8).toFixed(0)}억`;
  return v.toLocaleString("ko-KR");
};

const fmtPercent = (v: number | null | undefined) =>
  v == null ? "-" : `${v.toFixed(2)}%`;

const fmtTimes = (v: number | null | undefined, language?: string | null) =>
  v == null ? "-" : `${v.toFixed(2)}${isEnglish(language) ? "x" : "배"}`;

const fmtNumber = (v: number | null | undefined, language?: string | null) =>
  v == null ? "-" : v.toLocaleString(isEnglish(language) ? "en-US" : "ko-KR");

type RowDef = {
  label: string;
  labelEn: string;
  subtitle: string;
  subtitleEn?: string;
  format: (dto: FinancialDto, language?: string | null) => string;
};

type SectionDef = { title: string; titleEn: string; rows: RowDef[] };

// SECTIONS는 정적 표시용 — DictTerm 키로 Korean label이 사용되므로 유지
const SECTIONS: SectionDef[] = [
  {
    title: "손익계산서",
    titleEn: "Income Statement",
    rows: [
      { label: "매출액", labelEn: "Revenue", subtitle: "Revenue", format: (d, language) => fmtWon(d.revenue, language) },
      { label: "매출총이익", labelEn: "Gross Profit", subtitle: "Gross Profit", format: (d, language) => fmtWon(d.grossProfit, language) },
      { label: "영업이익", labelEn: "Operating Income", subtitle: "Operating Income", format: (d, language) => fmtWon(d.operatingIncome, language) },
      { label: "순이익", labelEn: "Net Income", subtitle: "Net Income", format: (d, language) => fmtWon(d.netIncome, language) },
    ],
  },
  {
    title: "재무상태표",
    titleEn: "Balance Sheet",
    rows: [
      { label: "자산총계", labelEn: "Total Assets", subtitle: "Total Assets", format: (d, language) => fmtWon(d.assets, language) },
      { label: "부채총계", labelEn: "Total Liabilities", subtitle: "Total Liabilities", format: (d, language) => fmtWon(d.liabilities, language) },
      { label: "자본총계", labelEn: "Total Equity", subtitle: "Total Equity", format: (d, language) => fmtWon(d.equity, language) },
      { label: "자본금", labelEn: "Capital Stock", subtitle: "Capital Stock", format: (d, language) => fmtWon(d.capitalStock, language) },
      { label: "이익잉여금", labelEn: "Retained Earnings", subtitle: "Retained Earnings", format: (d, language) => fmtWon(d.retainedEarnings, language) },
      { label: "현금성자산", labelEn: "Cash & Equivalents", subtitle: "Cash & Equivalents", format: (d, language) => fmtWon(d.cashAndEquivalents, language) },
    ],
  },
  {
    title: "주당 지표",
    titleEn: "Per-Share Metrics",
    rows: [
      { label: "시가총액", labelEn: "Market Cap", subtitle: "Market Cap", format: (d, language) => fmtWon(d.marketCap, language) },
      { label: "EPS", labelEn: "EPS", subtitle: "주당순이익", subtitleEn: "Earnings Per Share", format: (d, language) => fmtNumber(d.eps, language) },
      { label: "BPS", labelEn: "BPS", subtitle: "주당순자산", subtitleEn: "Book Value Per Share", format: (d, language) => fmtNumber(d.bps, language) },
    ],
  },
  {
    title: "밸류에이션",
    titleEn: "Valuation",
    rows: [
      { label: "PER", labelEn: "PER", subtitle: "주가수익비율", subtitleEn: "Price Earnings Ratio", format: (d, language) => fmtTimes(d.per, language) },
      { label: "PBR", labelEn: "PBR", subtitle: "주가순자산비율", subtitleEn: "Price Book Ratio", format: (d, language) => fmtTimes(d.pbr, language) },
      { label: "PSR", labelEn: "PSR", subtitle: "주가매출비율", subtitleEn: "Price Sales Ratio", format: (d, language) => fmtTimes(d.psr, language) },
    ],
  },
  {
    title: "수익성",
    titleEn: "Profitability",
    rows: [
      { label: "ROE", labelEn: "ROE", subtitle: "자기자본이익률", subtitleEn: "Return on Equity", format: (d) => fmtPercent(d.roe) },
      { label: "ROA", labelEn: "ROA", subtitle: "총자산이익률", subtitleEn: "Return on Assets", format: (d) => fmtPercent(d.roa) },
      { label: "영업이익률", labelEn: "Operating Margin", subtitle: "Operating Margin", format: (d) => fmtPercent(d.operatingMargin) },
      { label: "순이익률", labelEn: "Net Margin", subtitle: "Net Margin", format: (d) => fmtPercent(d.netMargin) },
    ],
  },
  {
    title: "재무안정성",
    titleEn: "Financial Stability",
    rows: [
      { label: "부채비율", labelEn: "Debt Ratio", subtitle: "Debt Ratio", format: (d) => fmtPercent(d.debtRatio) },
      { label: "유동비율", labelEn: "Current Ratio", subtitle: "Current Ratio", format: (d) => fmtPercent(d.currentRatio) },
      { label: "당좌비율", labelEn: "Quick Ratio", subtitle: "Quick Ratio", format: (d) => fmtPercent(d.quickRatio) },
      { label: "이자보상배율", labelEn: "Interest Coverage", subtitle: "Interest Coverage", format: (d, language) => fmtTimes(d.interestCoverageRatio, language) },
    ],
  },
];

function columnLabel(dto: FinancialDto, t: TFunction<"financialModal">): string {
  const y = String(dto.year).slice(2);
  if (dto.periodType === "A") return t("columnLabel.annual", { y });
  if (dto.periodType === "Q") return `${y}.Q${dto.periodNo ?? dto.quarter ?? ""}`;
  if (dto.periodType === "H") return `${y}.H${dto.periodNo ?? dto.half ?? ""}`;
  return `${dto.year}`;
}

export default function FinancialDetailModal({
  isOpen,
  onClose,
  ticker,
  companyName,
}: Props) {
  const { t, i18n } = useTranslation("financialModal");
  const english = isEnglish(i18n.language);
  const [tab, setTab] = useState<PeriodTab>("A");
  const [data, setData] = useState<Record<PeriodTab, FinancialDto[]>>({
    A: [],
    Q: [],
    H: [],
  });
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!isOpen || !ticker || loaded) return;
    setLoading(true);

    Promise.all([
      fetchFinancials(ticker, PERIOD_YEARS.A, "A").catch(() => []),
      fetchFinancials(ticker, PERIOD_YEARS.Q, "Q").catch(() => []),
      fetchFinancials(ticker, PERIOD_YEARS.H, "H").catch(() => []),
    ])
      .then(([annual, quarterly, half]) => {
        setData({
          A: annual.slice(0, 5),
          Q: quarterly.slice(0, 10),
          H: half.slice(0, 6),
        });
        setLoaded(true);
      })
      .finally(() => setLoading(false));
  }, [isOpen, ticker, loaded]);

  // 모달 닫힐 때 로드 상태 리셋
  useEffect(() => {
    if (!isOpen) setLoaded(false);
  }, [isOpen]);

  if (!isOpen) return null;

  const columns = data[tab];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-2xl w-[95vw] max-w-[900px] max-h-[85vh] flex flex-col shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-line">
          <div>
            <h2 className="text-lg font-semibold text-ink">{t("header")}</h2>
            <p className="text-sm text-ink-3">{companyName}</p>
          </div>
          <button
            onClick={onClose}
            className="text-ink-4 hover:text-ink-2 text-xl leading-none p-1"
          >
            ✕
          </button>
        </div>

        {/* 탭 */}
        <div className="flex gap-1 px-6 pt-3">
          {(["A", "Q", "H"] as PeriodTab[]).map((key) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                tab === key
                  ? "bg-accent text-white"
                  : "bg-surface-2 text-ink-2 hover:bg-line"
              }`}
            >
              {t(`periods.${key}` as "periods.A" | "periods.Q" | "periods.H")}
            </button>
          ))}
        </div>

        {/* 본문 */}
        <div className="flex-1 overflow-auto px-6 py-4">
          {loading ? (
            <p className="text-sm text-ink-3 py-10 text-center animate-pulse">
              {t("loading")}
            </p>
          ) : columns.length === 0 ? (
            <p className="text-sm text-ink-3 py-10 text-center">
              {t("empty")}
            </p>
          ) : (
            <div className="flex flex-col gap-6">
              {SECTIONS.map((section) => (
                <div key={section.title}>
                  <h3 className="text-base font-semibold text-ink-2 mb-2">
                    <DictTerm term={section.title}>{english ? section.titleEn : section.title}</DictTerm>
                  </h3>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm border border-line rounded-lg overflow-hidden">
                      <thead>
                        <tr className="bg-bg-sunk">
                          <th className="text-left px-3 py-2 font-medium text-ink-3 sticky left-0 bg-bg-sunk min-w-[140px]">
                            {t("tableHeader")}
                          </th>
                          {columns.map((col) => (
                            <th
                              key={`${col.year}-${col.periodType}-${col.periodNo}`}
                              className="text-right px-3 py-2 font-medium text-ink-3 min-w-[90px] whitespace-nowrap"
                            >
                              {columnLabel(col, t)}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {section.rows.map((row) => (
                          <tr key={row.label} className="border-t border-line hover:bg-bg-soft">
                            <td className="px-3 py-2 sticky left-0 bg-surface">
                              <DictTerm term={row.label}>
                                <span className="font-medium text-ink-2">{english ? row.labelEn : row.label}</span>
                              </DictTerm>
                              <span className="block text-xs text-ink-4">{english ? (row.subtitleEn ?? row.subtitle) : row.subtitle}</span>
                            </td>
                            {columns.map((col) => (
                              <td
                                key={`${col.year}-${col.periodType}-${col.periodNo}`}
                                className="px-3 py-2 text-right text-ink font-medium whitespace-nowrap"
                              >
                                {row.format(col, i18n.language)}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
