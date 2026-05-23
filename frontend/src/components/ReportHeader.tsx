import type { ReportFeatureType } from "../types/report";

export interface ReportHeaderMeta {
  featureType: ReportFeatureType;
  subjectLabel: string;
  subjectDetail?: string | null;
  generatedAt?: string | null;
  analysisModel?: string | null;
  investLevel?: string | null;
  userName?: string | null;
  analysisWindow?: string | null;
  dataAsOf?: string | null;
  riskProfile?: string | null;
  priceBasis?: string | null;
  covarianceModel?: string | null;
}

const featureLabel = (featureType: ReportFeatureType) => {
  if (featureType === "FEATURE1") return "기능1 종목 분석";
  if (featureType === "FEATURE2") return "기능2 외부요인 분석";
  return "기능3 포트폴리오 분석";
};

const formatDateTime = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
};

export default function ReportHeader({ meta }: { meta?: ReportHeaderMeta | null }) {
  if (!meta) return null;

  const rows = [
    ["분석대상", meta.subjectDetail ? `${meta.subjectLabel} · ${meta.subjectDetail}` : meta.subjectLabel],
    ["분석기능", featureLabel(meta.featureType)],
    ["분석일시", `${formatDateTime(meta.generatedAt)} KST`],
    ["분석모델", meta.analysisModel || "-"],
    ["투자 레벨", meta.investLevel || "-"],
    ["사용자", meta.userName || "사용자"],
    ["분석기간", meta.analysisWindow || null],
    ["데이터 기준", meta.dataAsOf || null],
    ["위험성향", meta.riskProfile || null],
    ["가격 기준", meta.priceBasis || null],
    ["공분산 모형", meta.covarianceModel || null],
  ].filter(([, value]) => value !== null);

  return (
    <div className="rounded-xl border border-line bg-bg-sunk px-4 py-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-5 gap-y-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-start justify-between gap-3 text-xs sm:text-sm">
            <span className="shrink-0 font-medium text-ink-3">{label}</span>
            <span className="min-w-0 text-right font-semibold text-ink break-words">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
