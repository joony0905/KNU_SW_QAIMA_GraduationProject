import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import type { ReportFeatureType } from "../types/report";
import { investLevelLabel, riskProfileLabel } from "../utils/displayLabels";

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

const featureLabel = (featureType: ReportFeatureType, t: TFunction<"reportHeader">) => {
  if (featureType === "FEATURE1") return t("featureLabel.FEATURE1");
  if (featureType === "FEATURE2") return t("featureLabel.FEATURE2");
  return t("featureLabel.FEATURE3");
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
  const { t, i18n } = useTranslation("reportHeader");
  if (!meta) return null;

  const rows = [
    [t("rows.subject"), meta.subjectDetail ? `${meta.subjectLabel} · ${meta.subjectDetail}` : meta.subjectLabel],
    [t("rows.feature"), featureLabel(meta.featureType, t)],
    [t("rows.generatedAt"), `${formatDateTime(meta.generatedAt)} KST`],
    [t("rows.investLevel"), investLevelLabel(meta.investLevel, i18n.language)],
    [t("rows.user"), meta.userName || t("userFallback")],
    [t("rows.window"), meta.analysisWindow || null],
    [t("rows.dataAsOf"), meta.dataAsOf || null],
    [t("rows.riskProfile"), meta.riskProfile ? riskProfileLabel(meta.riskProfile, i18n.language) : null],
    [t("rows.priceBasis"), meta.priceBasis || null],
    [t("rows.covarianceModel"), meta.covarianceModel || null],
  ].filter(([, value]) => value !== null);

  return (
    <div className="min-w-0 overflow-hidden rounded-xl border border-line bg-bg-sunk px-3 py-3 sm:px-4">
      <div className="grid min-w-0 grid-cols-1 gap-x-5 gap-y-2 sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex min-w-0 flex-col items-start gap-1 text-xs sm:flex-row sm:justify-between sm:gap-3 sm:text-sm">
            <span className="shrink-0 font-medium text-ink-3">{label}</span>
            <span className="min-w-0 max-w-full break-words text-left font-semibold text-ink sm:text-right">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
