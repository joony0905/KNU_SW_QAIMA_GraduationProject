import { useEffect, useState } from "react";
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

const PERIOD_CONFIG: Record<PeriodTab, { label: string; years: number }> = {
  A: { label: "연간", years: 5 },
  Q: { label: "분기", years: 3 },
  H: { label: "반기", years: 3 },
};

const fmtWon = (v: number | null | undefined) => {
  if (v == null) return "-";
  if (Math.abs(v) >= 1e12) return `${(v / 1e12).toFixed(2)}조`;
  if (Math.abs(v) >= 1e8) return `${(v / 1e8).toFixed(0)}억`;
  return v.toLocaleString("ko-KR");
};

const fmtPercent = (v: number | null | undefined) =>
  v == null ? "-" : `${v.toFixed(2)}%`;

const fmtTimes = (v: number | null | undefined) =>
  v == null ? "-" : `${v.toFixed(2)}배`;

const fmtNumber = (v: number | null | undefined) =>
  v == null ? "-" : v.toLocaleString("ko-KR");

type RowDef = {
  label: string;
  subtitle: string;
  format: (dto: FinancialDto) => string;
};

type SectionDef = { title: string; rows: RowDef[] };

const SECTIONS: SectionDef[] = [
  {
    title: "손익계산서",
    rows: [
      { label: "매출액", subtitle: "Revenue", format: (d) => fmtWon(d.revenue) },
      { label: "매출총이익", subtitle: "Gross Profit", format: (d) => fmtWon(d.grossProfit) },
      { label: "영업이익", subtitle: "Operating Income", format: (d) => fmtWon(d.operatingIncome) },
      { label: "순이익", subtitle: "Net Income", format: (d) => fmtWon(d.netIncome) },
    ],
  },
  {
    title: "재무상태표",
    rows: [
      { label: "자산총계", subtitle: "Total Assets", format: (d) => fmtWon(d.assets) },
      { label: "부채총계", subtitle: "Total Liabilities", format: (d) => fmtWon(d.liabilities) },
      { label: "자본총계", subtitle: "Total Equity", format: (d) => fmtWon(d.equity) },
      { label: "자본금", subtitle: "Capital Stock", format: (d) => fmtWon(d.capitalStock) },
      { label: "이익잉여금", subtitle: "Retained Earnings", format: (d) => fmtWon(d.retainedEarnings) },
      { label: "현금성자산", subtitle: "Cash & Equivalents", format: (d) => fmtWon(d.cashAndEquivalents) },
    ],
  },
  {
    title: "주당 지표",
    rows: [
      { label: "시가총액", subtitle: "Market Cap", format: (d) => fmtWon(d.marketCap) },
      { label: "EPS", subtitle: "주당순이익", format: (d) => fmtNumber(d.eps) },
      { label: "BPS", subtitle: "주당순자산", format: (d) => fmtNumber(d.bps) },
    ],
  },
  {
    title: "밸류에이션",
    rows: [
      { label: "PER", subtitle: "주가수익비율", format: (d) => fmtTimes(d.per) },
      { label: "PBR", subtitle: "주가순자산비율", format: (d) => fmtTimes(d.pbr) },
      { label: "PSR", subtitle: "주가매출비율", format: (d) => fmtTimes(d.psr) },
    ],
  },
  {
    title: "수익성",
    rows: [
      { label: "ROE", subtitle: "자기자본이익률", format: (d) => fmtPercent(d.roe) },
      { label: "ROA", subtitle: "총자산이익률", format: (d) => fmtPercent(d.roa) },
      { label: "영업이익률", subtitle: "Operating Margin", format: (d) => fmtPercent(d.operatingMargin) },
      { label: "순이익률", subtitle: "Net Margin", format: (d) => fmtPercent(d.netMargin) },
    ],
  },
  {
    title: "재무안정성",
    rows: [
      { label: "부채비율", subtitle: "Debt Ratio", format: (d) => fmtPercent(d.debtRatio) },
      { label: "유동비율", subtitle: "Current Ratio", format: (d) => fmtPercent(d.currentRatio) },
      { label: "당좌비율", subtitle: "Quick Ratio", format: (d) => fmtPercent(d.quickRatio) },
      { label: "이자보상배율", subtitle: "Interest Coverage", format: (d) => fmtTimes(d.interestCoverageRatio) },
    ],
  },
];

function columnLabel(dto: FinancialDto): string {
  const y = String(dto.year).slice(2);
  if (dto.periodType === "A") return `${y}년`;
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
      fetchFinancials(ticker, PERIOD_CONFIG.A.years, "A").catch(() => []),
      fetchFinancials(ticker, PERIOD_CONFIG.Q.years, "Q").catch(() => []),
      fetchFinancials(ticker, PERIOD_CONFIG.H.years, "H").catch(() => []),
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
        className="bg-white rounded-2xl w-[95vw] max-w-[900px] max-h-[85vh] flex flex-col shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-200">
          <div>
            <h2 className="text-lg font-semibold text-zinc-900">재무제표 상세</h2>
            <p className="text-sm text-zinc-500">{companyName}</p>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-700 text-xl leading-none p-1"
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
                  ? "bg-zinc-800 text-white"
                  : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
              }`}
            >
              {PERIOD_CONFIG[key].label}
            </button>
          ))}
        </div>

        {/* 본문 */}
        <div className="flex-1 overflow-auto px-6 py-4">
          {loading ? (
            <p className="text-sm text-gray-500 py-10 text-center animate-pulse">
              재무제표를 불러오는 중...
            </p>
          ) : columns.length === 0 ? (
            <p className="text-sm text-gray-500 py-10 text-center">
              해당 기간의 재무제표 데이터가 없습니다.
            </p>
          ) : (
            <div className="flex flex-col gap-6">
              {SECTIONS.map((section) => (
                <div key={section.title}>
                  <h3 className="text-base font-semibold text-zinc-800 mb-2">
                    <DictTerm term={section.title}>{section.title}</DictTerm>
                  </h3>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm border border-zinc-200 rounded-lg overflow-hidden">
                      <thead>
                        <tr className="bg-zinc-50">
                          <th className="text-left px-3 py-2 font-medium text-zinc-600 sticky left-0 bg-zinc-50 min-w-[140px]">
                            항목
                          </th>
                          {columns.map((col) => (
                            <th
                              key={`${col.year}-${col.periodType}-${col.periodNo}`}
                              className="text-right px-3 py-2 font-medium text-zinc-600 min-w-[90px] whitespace-nowrap"
                            >
                              {columnLabel(col)}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {section.rows.map((row) => (
                          <tr key={row.label} className="border-t border-zinc-100 hover:bg-zinc-50">
                            <td className="px-3 py-2 sticky left-0 bg-white">
                              <DictTerm term={row.label}>
                                <span className="font-medium text-zinc-800">{row.label}</span>
                              </DictTerm>
                              <span className="block text-xs text-zinc-400">{row.subtitle}</span>
                            </td>
                            {columns.map((col) => (
                              <td
                                key={`${col.year}-${col.periodType}-${col.periodNo}`}
                                className="px-3 py-2 text-right text-zinc-900 font-medium whitespace-nowrap"
                              >
                                {row.format(col)}
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
