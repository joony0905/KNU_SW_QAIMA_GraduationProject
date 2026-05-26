import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
  fetchWatchlist,
  deleteWatchlistItem,
} from "../api/watchlist";
import type { WatchlistItem } from "../types/watchlist";
import { getMyProfile, getMyRiskProfile, updateMyProfile, updateMyRiskProfile } from "../api/user";
import type { MyProfile } from "../api/user";
import { getApiErrorMessage } from "../utils/errorMessage";
import {
  Sun,
  Moon,
  Pencil,
  RotateCcw,
  X,
  ChevronDown,
  ChevronUp,
  User,
  TrendingUp,
  Star,
  Languages,
  LifeBuoy,
  BookOpen,
  FileText,
  Download,
  Eye,
} from "lucide-react";
import { useTheme } from "../hooks/useTheme";
import { useDictionary } from "../components/DictContext";
import {
  INVEST_LEVELS,
  toInvestLevel,
  type InvestLevel,
} from "../utils/investLevel";
import {
  RISK_PROFILE_OPTIONS,
  riskProfileOptionForGamma,
  syncFeature3RiskDefaults,
  type RiskProfileLabel,
} from "../utils/riskProfile";
import { fetchMyReports, fetchReportDetail } from "../api/reports";
import type { AnalysisReportDetail, AnalysisReportSummary, ReportFeatureType } from "../types/report";
import { mapWarningsToNotes, expandWarningLines } from "../utils/warningNotes";
import { useTranslation } from "react-i18next";
import { LANGUAGE_STORAGE_KEY, type SupportedLanguage } from "../i18n";
import ReportHeader from "../components/ReportHeader";
import { downloadElementAsPdf, waitForPdfCaptureReady } from "../utils/reportPdf";
import qaimaLogo from "../assets/qaima-final.png";
import { clientLog } from "../utils/clientLog";
import {
  investLevelLabel,
  riskProfileDescription,
  riskProfileLabel as formatRiskProfileLabel,
} from "../utils/displayLabels";
import { localizeBackendText } from "../utils/localizeBackendText";

const formatPhone = (v: string): string => {
  const d = (v ?? "").replace(/[^0-9]/g, "");
  if (d.length === 11) return `${d.slice(0, 3)} ${d.slice(3, 7)} ${d.slice(7)}`;
  if (d.length === 10) return `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}`;
  return v || "-";
};

const formatBirthdate = (v: string): string =>
  /^\d{6,7}$/.test(v ?? "")
    ? `${v.slice(0, 2)}.${v.slice(2, 4)}.${v.slice(4, 6)}`
    : v || "-";

const reportPortfolioSummary = (value?: string | null, language?: string | null): string | null =>
  value ? localizeBackendText(value, language) : null;


type CardProps = {
  icon: React.ElementType;
  title: string;
  desc?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
};

function SettingCard({ icon: Icon, title, desc, action, children }: CardProps) {
  return (
    <section className="w-full rounded-2xl border border-line bg-surface shadow-card">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 px-5 sm:px-6 pt-5 pb-4 border-b border-line">
        <div className="flex min-w-0 items-start gap-3">
          <div className="shrink-0 w-9 h-9 grid place-items-center rounded-xl bg-accent-soft text-accent">
            <Icon size={17} />
          </div>
          <div className="min-w-0">
            <h2 className="text-base font-bold text-ink tracking-tight break-words">
              {title}
            </h2>
            {desc && <p className="mt-0.5 text-xs text-ink-3 break-words">{desc}</p>}
          </div>
        </div>
        {action && <div className="max-w-full shrink-0 sm:pt-0">{action}</div>}
      </div>
      <div className="px-5 sm:px-6 py-5">{children}</div>
    </section>
  );
}


const formatKstDateTime = (value?: string | null) => {
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
    hour12: false,
  });
};

const REPORT_RETENTION_DAYS = 7;
const REPORT_RETENTION_MS = REPORT_RETENTION_DAYS * 24 * 60 * 60 * 1000;

const isReportWithinRetention = (report: AnalysisReportSummary | AnalysisReportDetail): boolean => {
  const generatedAt = new Date(report.generatedAt).getTime();
  if (Number.isNaN(generatedAt)) return false;
  return Date.now() - generatedAt <= REPORT_RETENTION_MS;
};

function SavedReportDocument({ report }: { report: AnalysisReportDetail }) {
  const { t, i18n } = useTranslation("settingPage");
  const snapshot = report.resultSnapshot as {
    explain?: {
      text?: string | null;
      overall?: {
        summary?: string | null;
        bullets?: string[] | null;
        risks?: string[] | null;
        conclusion?: string | null;
      } | null;
      sections?: Record<string, { title?: string | null; summary?: string | null; bullets?: string[] | null } | null> | null;
    } | null;
    warnings?: unknown;
    summary?: string;
    highlights?: string[];
    risks?: string[];
  };
  const explain = snapshot?.explain;
  const sections = Object.entries(explain?.sections ?? {}).filter(([, value]) => value);
  const rawWarnings = Array.isArray(report.warnings)
    ? report.warnings
    : Array.isArray(snapshot?.warnings)
      ? snapshot.warnings
      : [];
  const warnings = expandWarningLines(mapWarningsToNotes(rawWarnings, i18n.language));

  return (
    <div className="w-[900px] max-w-full bg-surface text-ink p-6 flex flex-col gap-4">
      <ReportHeader
        meta={{
          featureType: report.featureType,
          subjectLabel: report.subjectType === "PORTFOLIO"
            ? t("cards.reports.portfolioLabel")
            : `${report.companyName || report.stockCode || t("cards.reports.stockFallback")} (${report.stockCode || "-"})`,
          subjectDetail: reportPortfolioSummary(report.portfolioSummary, i18n.language),
          generatedAt: report.generatedAt,
          analysisModel: report.analysisModel,
          investLevel: report.investLevel,
          userName: report.userName,
          analysisWindow: report.analysisWindow,
          dataAsOf: report.dataAsOf,
          riskProfile: report.riskProfile,
          priceBasis: report.priceBasis,
        }}
      />
      <div className="rounded-xl border border-line bg-bg-sunk p-4">
        <h3 className="text-base font-bold text-ink">{localizeBackendText(report.title, i18n.language)}</h3>
        <p className="mt-1 text-sm text-ink-3">
          {t("reportDetail.snapshotNote")}
        </p>
      </div>
      {explain?.overall?.summary || explain?.text || snapshot?.summary ? (
        <section className="rounded-xl border border-line bg-bg-sunk p-4">
          <h3 className="text-base font-bold text-ink">{t("reportDetail.overallSummary")}</h3>
          <p className="mt-2 text-sm leading-relaxed text-ink-2">
            {localizeBackendText(explain?.overall?.summary || explain?.text || snapshot?.summary, i18n.language)}
          </p>
          {explain?.overall?.bullets?.length ? (
            <ul className="mt-3 list-disc list-inside text-sm text-ink-2">
              {explain.overall.bullets.map((item, idx) => <li key={`bullet-${idx}`}>{localizeBackendText(item, i18n.language)}</li>)}
            </ul>
          ) : null}
          {explain?.overall?.risks?.length ? (
            <div className="mt-3">
              <p className="text-sm font-semibold text-ink">{t("reportDetail.risks")}</p>
              <ul className="mt-1 list-disc list-inside text-sm text-ink-2">
                {explain.overall.risks.map((item, idx) => <li key={`risk-${idx}`}>{localizeBackendText(item, i18n.language)}</li>)}
              </ul>
            </div>
          ) : null}
          {explain?.overall?.conclusion ? (
            <p className="mt-3 text-sm leading-relaxed text-ink-2">{localizeBackendText(explain.overall.conclusion, i18n.language)}</p>
          ) : null}
        </section>
      ) : null}
      {sections.length > 0 ? (
        <section className="rounded-xl border border-line bg-bg-sunk p-4">
          <h3 className="text-base font-bold text-ink">{t("reportDetail.sections")}</h3>
          <div className="mt-3 flex flex-col gap-3">
            {sections.map(([key, section]) => (
              <div key={key} className="rounded-lg border border-line bg-surface p-3">
                <p className="text-sm font-bold text-ink">{localizeBackendText(section?.title, i18n.language) || key}</p>
                {section?.summary ? <p className="mt-1 text-sm text-ink-2">{localizeBackendText(section.summary, i18n.language)}</p> : null}
                {section?.bullets?.length ? (
                  <ul className="mt-2 list-disc list-inside text-sm text-ink-2">
                    {section.bullets.map((item, idx) => <li key={`${key}-${idx}`}>{localizeBackendText(item, i18n.language)}</li>)}
                  </ul>
                ) : null}
              </div>
            ))}
          </div>
        </section>
      ) : null}
      {warnings.length > 0 ? (
        <section className="rounded-xl border border-line bg-bg-sunk p-4">
          <h3 className="text-base font-bold text-ink">{t("reportDetail.notes")}</h3>
          <ul className="mt-2 list-disc list-inside text-sm text-ink-2">
            {warnings.map((item, idx) => (
              <li key={`warning-${idx}`}>{item}</li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function SettingSelect<T extends string>({
  value,
  options,
  onChange,
  labelForOption,
}: {
  value: T;
  options: readonly T[];
  onChange: (next: T) => void;
  labelForOption?: (value: T) => string;
}) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number; width: number } | null>(null);
  const ref = useRef<HTMLDivElement | null>(null);
  const popRef = useRef<HTMLDivElement | null>(null);

  // 트리거 좌표 계산 (transform 스태킹 컨텍스트에 가리지 않게 portal + fixed)
  const updateCoords = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setCoords({ top: rect.bottom + 4, left: rect.left, width: rect.width });
  }, []);

  useEffect(() => {
    if (!open) return;
    updateCoords();
    window.addEventListener("scroll", updateCoords, true);
    window.addEventListener("resize", updateCoords);
    return () => {
      window.removeEventListener("scroll", updateCoords, true);
      window.removeEventListener("resize", updateCoords);
    };
  }, [open, updateCoords]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        !ref.current?.contains(target) &&
        !popRef.current?.contains(target)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  return (
    <div ref={ref} className="relative w-full sm:w-56">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={`inline-flex items-center justify-between gap-2 w-full pl-3.5 pr-3 py-2.5 rounded-lg bg-bg-sunk border text-sm text-ink transition-colors ${
          open ? "border-accent" : "border-line"
        }`}
      >
        <span className="truncate">{labelForOption ? labelForOption(value) : value}</span>
        {open ? (
          <ChevronUp size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />
        ) : (
          <ChevronDown size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />
        )}
      </button>
      {open && coords &&
        createPortal(
          <div
            ref={popRef}
            style={{
              position: "fixed",
              top: coords.top,
              left: coords.left,
              width: coords.width,
            }}
            className="z-[200] bg-surface border border-line rounded-lg shadow-pop overflow-hidden"
          >
            {options.map((opt) => (
              <button
                key={opt}
                type="button"
                onClick={() => {
                  onChange(opt);
                  setOpen(false);
                }}
                className={`w-full text-left px-3.5 py-2.5 text-sm transition-colors ${
                  opt === value
                    ? "bg-accent-soft text-accent font-semibold"
                    : "text-ink hover:bg-bg-sunk"
                }`}
              >
                {labelForOption ? labelForOption(opt) : opt}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </div>
  );
}

export default function SettingPage() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const { t, i18n } = useTranslation(["common", "settingPage"]);

  const formatGender = (gender?: string | null, birthdate?: string | null): string => {
    if (gender === "male") return t("settingPage:basicInfo.genderMale");
    if (gender === "female") return t("settingPage:basicInfo.genderFemale");
    const digit = birthdate?.replace(/[^0-9]/g, "").charAt(6);
    if (digit === "1" || digit === "3") return t("settingPage:basicInfo.genderMale");
    if (digit === "2" || digit === "4") return t("settingPage:basicInfo.genderFemale");
    return "-";
  };

  const featureLabel = (featureType: string) => {
    if (featureType === "FEATURE1") return t("settingPage:featureLabel.FEATURE1");
    if (featureType === "FEATURE2") return t("settingPage:featureLabel.FEATURE2");
    if (featureType === "FEATURE3") return t("settingPage:featureLabel.FEATURE3");
    return featureType;
  };
  const {
    glossaryHover,
    setGlossaryHover,
    investLevel,
    setInvestLevel,
  } = useDictionary();

  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [savingPref, setSavingPref] = useState(false);
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [wlLoading, setWlLoading] = useState(true);
  const [wlError, setWlError] = useState<string | null>(null);
  const [isEditingWatchlist, setIsEditingWatchlist] = useState(false);
  const languageOptions = ["한국어", "English"] as const;
  type LanguageLabel = (typeof languageOptions)[number];
  const labelToLng = (label: LanguageLabel): SupportedLanguage =>
    label === "English" ? "en" : "ko";
  const lngToLabel = (lng: string): LanguageLabel =>
    lng.startsWith("en") ? "English" : "한국어";
  const language = lngToLabel(i18n.language);
  const setLanguage = (next: LanguageLabel) => {
    const lng = labelToLng(next);
    i18n.changeLanguage(lng);
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, lng);
    } catch {
      // localStorage 사용 불가 환경에서는 메모리에만 반영
    }
  };
  const [riskProfileLabel, setRiskProfileLabel] = useState<RiskProfileLabel | "미설정">("미설정");
  const [riskProfileError, setRiskProfileError] = useState<string | null>(null);
  const [reportFilter, setReportFilter] = useState<ReportFeatureType | "ALL">("ALL");
  const [reports, setReports] = useState<AnalysisReportSummary[]>([]);
  const [reportsLoading, setReportsLoading] = useState(false);
  const [reportsError, setReportsError] = useState<string | null>(null);
  const [selectedReport, setSelectedReport] = useState<AnalysisReportDetail | null>(null);
  const [pdfReport, setPdfReport] = useState<AnalysisReportDetail | null>(null);
  const [reportDetailLoading, setReportDetailLoading] = useState(false);
  const [pdfExporting, setPdfExporting] = useState(false);
  const reportPdfRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let alive = true;
    setWlLoading(true);
    setWlError(null);
    fetchWatchlist()
      .then((items) => {
        if (alive) setWatchlist(items);
      })
      .catch(() => {
        if (alive) setWlError(t("settingPage:errors.watchlistLoad"));
      })
      .finally(() => {
        if (alive) setWlLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    Promise.all([getMyProfile(), getMyRiskProfile()])
      .then(([p, riskProfile]) => {
        if (!alive) return;
        setProfile(p);
        setInvestLevel(toInvestLevel(p.experience));
        setGlossaryHover(p.glossaryHover);
        const option = riskProfileOptionForGamma(riskProfile.defaultRiskGamma);
        setRiskProfileLabel(option?.label ?? "미설정");
      })
      .catch((e) => {
        if (alive)
          setProfileError(getApiErrorMessage(e, t("settingPage:errors.profileLoad")));
      });
    return () => {
      alive = false;
    };
  }, [setGlossaryHover, setInvestLevel]);

  useEffect(() => {
    let alive = true;
    setReportsLoading(true);
    setReportsError(null);
    fetchMyReports(reportFilter)
      .then((rows) => {
        if (alive) setReports(rows.filter(isReportWithinRetention));
      })
      .catch((e) => {
        if (alive) setReportsError(getApiErrorMessage(e, t("settingPage:errors.reportsLoad")));
      })
      .finally(() => {
        if (alive) setReportsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [reportFilter]);

  // 투자레벨 = 백엔드 experience(자유 문자열). 선택 즉시 저장.
  const handleInvestLevelChange = async (next: InvestLevel) => {
    const prev = investLevel;
    setInvestLevel(next);
    try {
      await updateMyProfile({ experience: next });
    } catch (e) {
      setInvestLevel(prev);
      alert(getApiErrorMessage(e, t("settingPage:errors.investLevelSave")));
    }
  };

  const handleRiskProfileChange = async (next: RiskProfileLabel | "미설정") => {
    if (next === "미설정") return;
    const option = RISK_PROFILE_OPTIONS.find((item) => item.label === next);
    if (!option) return;
    const prev = riskProfileLabel;
    setRiskProfileLabel(next);
    setRiskProfileError(null);
    try {
      await updateMyRiskProfile({ defaultRiskGamma: option.gamma });
      syncFeature3RiskDefaults(option.gamma);
    } catch (e) {
      setRiskProfileLabel(prev);
      setRiskProfileError(getApiErrorMessage(e, t("settingPage:errors.riskProfileSave")));
    }
  };

  // 용어 hover 설명 표시 여부 = 백엔드 glossaryHover. 토글 즉시 저장 + 앱 전역 반영.
  const handleToggleGlossaryHover = async () => {
    if (savingPref) return;
    const next = !glossaryHover;
    setGlossaryHover(next);
    setSavingPref(true);
    try {
      await updateMyProfile({ glossaryHover: next });
    } catch (e) {
      setGlossaryHover(!next);
      alert(getApiErrorMessage(e, t("settingPage:errors.glossarySave")));
    } finally {
      setSavingPref(false);
    }
  };

  const handleDeleteWatchlistItem = async (item: WatchlistItem) => {
    const prev = watchlist;
    // 낙관적 제거 후 실패 시 롤백
    setWatchlist((list) =>
      list.filter((w) => w.watchlistItemId !== item.watchlistItemId),
    );
    try {
      await deleteWatchlistItem(item.watchlistItemId);
    } catch {
      setWatchlist(prev);
      alert(t("settingPage:errors.watchlistDelete"));
    }
  };

  const labelOf = (item: WatchlistItem) =>
    item.stockName ?? (item.stockId != null ? `#${item.stockId}` : t("settingPage:cards.reports.stockFallback"));
  const displayInvestLevel = (value: InvestLevel) => investLevelLabel(value, i18n.language);
  const displayRiskProfile = (value: RiskProfileLabel | "미설정") =>
    value === "미설정" ? t("settingPage:riskProfileUnset") : formatRiskProfileLabel(value, i18n.language);
  const selectedRiskProfileDescription =
    riskProfileDescription(riskProfileLabel !== "미설정" ? riskProfileLabel : null, i18n.language)
    ?? RISK_PROFILE_OPTIONS.find((option) => option.label === riskProfileLabel)?.description;

  const openReportDetail = async (reportId: number) => {
    setReportDetailLoading(true);
    try {
      const detail = await fetchReportDetail(reportId);
      if (!isReportWithinRetention(detail)) {
        setReports((prev) => prev.filter((item) => item.reportId !== reportId));
        alert(t("settingPage:errors.reportExpired"));
        return;
      }
      setSelectedReport(detail);
    } catch (e) {
      alert(getApiErrorMessage(e, t("settingPage:errors.reportDetailLoad")));
    } finally {
      setReportDetailLoading(false);
    }
  };

  const handleReportDownload = async (report: AnalysisReportSummary | AnalysisReportDetail) => {
    try {
      const detail = "resultSnapshot" in report ? report : await fetchReportDetail(report.reportId);
      if (!isReportWithinRetention(detail)) {
        setReports((prev) => prev.filter((item) => item.reportId !== detail.reportId));
        alert(t("settingPage:errors.reportExpired"));
        return;
      }
      setPdfReport(detail);
      setPdfExporting(true);
      await waitForPdfCaptureReady();
      if (!reportPdfRef.current) return;
      await downloadElementAsPdf(reportPdfRef.current, `qaima_report_${detail.reportId}.pdf`, qaimaLogo);
    } catch (e) {
      clientLog.error("Saved report PDF generation failed", e);
      alert(getApiErrorMessage(e, t("settingPage:errors.pdfFailed")));
    } finally {
      setPdfExporting(false);
    }
  };

  const basicInfo: [string, string][] = [
    [t("settingPage:basicInfo.name"), profile?.name || "-"],
    [t("settingPage:basicInfo.email"), profile?.email || "-"],
    [t("settingPage:basicInfo.phone"), profile ? formatPhone(profile.phone) : "-"],
    [t("settingPage:basicInfo.birthdate"), profile ? formatBirthdate(profile.birthdate) : "-"],
    [t("settingPage:basicInfo.gender"), profile ? formatGender(profile.gender, profile.birthdate) : "-"],
  ];

  const ghostBtn =
    "inline-flex max-w-full items-center justify-center gap-1.5 px-3.5 py-2 rounded-lg border border-line bg-surface text-sm font-medium text-ink-2 hover:bg-bg-sunk transition-colors whitespace-normal text-center";

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      <div className="qaima-stagger max-w-3xl mx-auto px-4 sm:px-6 py-5 sm:py-7 flex flex-col gap-5">
        {/* 헤더 */}
        <header className="flex items-end justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Account · Settings
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              {t("settingPage:header")}
            </h1>
          </div>
          <button
            onClick={toggle}
            aria-label={theme === "dark" ? t("settingPage:themeLight") : t("settingPage:themeDark")}
            className="w-9 h-9 grid place-items-center rounded-xl bg-surface border border-line text-ink-2 shadow-card hover:bg-bg-sunk transition-colors"
          >
            {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </header>

        {/* 기본정보 */}
        <SettingCard
          icon={User}
          title={t("settingPage:cards.basicInfo.title")}
          desc={t("settingPage:cards.basicInfo.desc")}
          action={
            <button
              onClick={() => navigate("/setting/edit")}
              className={ghostBtn}
            >
              <Pencil size={14} />
              {t("settingPage:cards.basicInfo.editBtn")}
            </button>
          }
        >
          {profileError && (
            <p className="text-sm text-danger mb-3">{profileError}</p>
          )}
          <dl className="divide-y divide-line">
            {basicInfo.map(([label, value]) => (
              <div
                key={label}
                className="flex items-start justify-between gap-4 py-2.5 first:pt-0 last:pb-0"
              >
                <dt className="shrink-0 text-sm text-ink-3">{label}</dt>
                <dd className="min-w-0 text-sm font-medium text-ink text-right break-words">
                  {value}
                </dd>
              </div>
            ))}
          </dl>
        </SettingCard>

        {/* 투자레벨 */}
        <SettingCard
          icon={TrendingUp}
          title={t("settingPage:cards.investLevel.title")}
          desc={t("settingPage:cards.investLevel.desc")}
          action={
            <button
              onClick={() => navigate("/invest-level-survey")}
              className={ghostBtn}
            >
              <RotateCcw size={14} />
              {t("settingPage:cards.investLevel.retakeBtn")}
            </button>
          }
        >
          <SettingSelect
            value={investLevel}
            options={INVEST_LEVELS}
            onChange={handleInvestLevelChange}
            labelForOption={displayInvestLevel}
          />
        </SettingCard>

        {/* 투자성향 */}
        <SettingCard
          icon={TrendingUp}
          title={t("settingPage:cards.riskProfile.title")}
          desc={t("settingPage:cards.riskProfile.desc")}
          action={
            <button onClick={() => navigate("/survey")} className={ghostBtn}>
              <RotateCcw size={14} />
              {t("settingPage:cards.riskProfile.retakeBtn")}
            </button>
          }
        >
          <div className="flex flex-col gap-2">
            <SettingSelect
              value={riskProfileLabel}
              options={["미설정", ...RISK_PROFILE_OPTIONS.map((option) => option.label)] as const}
              onChange={handleRiskProfileChange}
              labelForOption={displayRiskProfile}
            />
            {riskProfileLabel !== "미설정" && (
              <p className="text-xs text-ink-3">
                {selectedRiskProfileDescription}
              </p>
            )}
            {riskProfileError && (
              <p className="text-sm text-danger">{riskProfileError}</p>
            )}
          </div>
        </SettingCard>

        {/* 용어 설명 hover */}
        <SettingCard
          icon={BookOpen}
          title={t("settingPage:cards.glossaryHover.title")}
          desc={t("settingPage:cards.glossaryHover.desc")}
        >
          <button
            type="button"
            onClick={handleToggleGlossaryHover}
            disabled={savingPref}
            aria-pressed={glossaryHover}
            className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors disabled:opacity-50 ${
              glossaryHover ? "bg-accent" : "bg-line-strong"
            }`}
          >
            <span
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                glossaryHover ? "translate-x-6" : "translate-x-1"
              }`}
            />
          </button>
          <span className="ml-3 text-sm text-ink-2 align-middle">
            {glossaryHover ? t("settingPage:cards.glossaryHover.on") : t("settingPage:cards.glossaryHover.off")}
          </span>
        </SettingCard>

        {/* 관심종목 */}
        <SettingCard
          icon={Star}
          title={t("settingPage:cards.watchlist.title")}
          desc={wlLoading ? t("settingPage:cards.watchlist.loadingDesc") : t("settingPage:cards.watchlist.countDesc", { count: watchlist.length })}
          action={
            watchlist.length > 0 ? (
              <button
                onClick={() => setIsEditingWatchlist((prev) => !prev)}
                className={ghostBtn}
              >
                {isEditingWatchlist ? t("settingPage:cards.watchlist.doneBtn") : t("settingPage:cards.watchlist.deleteSelectBtn")}
              </button>
            ) : undefined
          }
        >
          {wlLoading ? (
            <p className="text-sm text-ink-3 text-center py-6">
              {t("settingPage:cards.watchlist.loading")}
            </p>
          ) : wlError ? (
            <p className="text-sm text-danger text-center py-6">{wlError}</p>
          ) : watchlist.length === 0 ? (
            <p className="text-sm text-ink-3 text-center py-6">
              {t("settingPage:cards.watchlist.empty")}
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {watchlist.map((item) => (
                <span
                  key={item.watchlistItemId}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium ${
                    isEditingWatchlist
                      ? "bg-danger/10 text-danger"
                      : "bg-bg-sunk text-ink"
                  }`}
                >
                  {labelOf(item)}
                  {isEditingWatchlist && (
                    <button
                      onClick={() => handleDeleteWatchlistItem(item)}
                      className="hover:opacity-70"
                      aria-label={t("settingPage:cards.watchlist.deleteAriaLabel", { name: labelOf(item) })}
                    >
                      <X size={13} />
                    </button>
                  )}
                </span>
              ))}
            </div>
          )}
        </SettingCard>

        {/* 언어 */}
        <SettingCard
          icon={Languages}
          title={t("common:language.label")}
          desc={t("common:language.description")}
        >
          <SettingSelect
            value={language}
            options={languageOptions}
            onChange={setLanguage}
          />
        </SettingCard>

        {/* 내 리포트 */}
        <SettingCard
          icon={FileText}
          title={t("settingPage:cards.reports.title")}
          desc={reportsLoading ? t("settingPage:cards.reports.loadingDesc") : t("settingPage:cards.reports.countDesc", { count: reports.length })}
          action={
            <div className="flex max-w-full flex-wrap items-center gap-1 rounded-lg bg-bg-sunk border border-line p-1">
              {(["ALL", "FEATURE1", "FEATURE2", "FEATURE3"] as const).map((item) => (
                <button
                  key={item}
                  type="button"
                  onClick={() => setReportFilter(item)}
                  className={`min-w-0 px-2.5 py-1 rounded-md text-xs font-semibold ${
                    reportFilter === item ? "bg-surface text-ink shadow-card" : "text-ink-3"
                  }`}
                >
                  {item === "ALL" ? t("settingPage:cards.reports.filterAll") : featureLabel(item)}
                </button>
              ))}
            </div>
          }
        >
          {reportsLoading ? (
            <p className="text-sm text-ink-3 text-center py-6">{t("settingPage:cards.reports.loading")}</p>
          ) : reportsError ? (
            <p className="text-sm text-danger text-center py-6">{reportsError}</p>
          ) : reports.length === 0 ? (
            <div className="py-6 text-center">
              <p className="text-sm text-ink-3">{t("settingPage:cards.reports.empty")}</p>
              <p className="mt-1 text-xs text-ink-4">{t("settingPage:cards.reports.retentionNotice")}</p>
            </div>
          ) : (
            <div>
              <p className="mb-2 text-xs text-ink-4">{t("settingPage:cards.reports.retentionNotice")}</p>
              <div className="divide-y divide-line">
                {reports.map((report) => (
                  <div key={report.reportId} className="py-3 first:pt-0 last:pb-0 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-bold text-accent">
                          {featureLabel(report.featureType)}
                        </span>
                        <p className="text-sm font-bold text-ink truncate">
                          {report.subjectType === "PORTFOLIO"
                            ? reportPortfolioSummary(report.portfolioSummary, i18n.language) || t("settingPage:cards.reports.portfolioLabel")
                            : `${report.companyName || report.stockCode || t("settingPage:cards.reports.stockFallback")} (${report.stockCode || "-"})`}
                        </p>
                      </div>
                      <p className="mt-1 text-xs text-ink-3">
                        {formatKstDateTime(report.generatedAt)} · {investLevelLabel(report.investLevel, i18n.language)}
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        onClick={() => void openReportDetail(report.reportId)}
                        disabled={reportDetailLoading}
                        className={ghostBtn}
                      >
                        <Eye size={14} />
                        {t("settingPage:cards.reports.viewBtn")}
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleReportDownload(report)}
                        disabled={pdfExporting}
                        className={ghostBtn}
                      >
                        <Download size={14} />
                        PDF
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </SettingCard>

        {/* 버그제보 */}
        <SettingCard
          icon={LifeBuoy}
          title={t("settingPage:cards.bugReport.title")}
          desc={t("settingPage:cards.bugReport.desc")}
        >
          <a
            href="mailto:admin.qaima@gmail.com"
            className="text-sm font-medium text-accent hover:underline"
          >
            admin.qaima@gmail.com
          </a>
        </SettingCard>
      </div>
      {selectedReport && createPortal(
        <div
          className="fixed inset-0 z-[100] bg-ink/50 flex items-center justify-center p-4"
          onClick={() => setSelectedReport(null)}
        >
          <div
            className="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-2xl bg-surface shadow-pop"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-line">
              <h2 className="text-base font-bold text-ink">{t("settingPage:reportDetail.title")}</h2>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleReportDownload(selectedReport)}
                  disabled={pdfExporting}
                  className={ghostBtn}
                >
                  <Download size={14} />
                  {t("settingPage:reportDetail.redownload")}
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedReport(null)}
                  className="w-8 h-8 grid place-items-center rounded-full text-ink-3 hover:bg-bg-sunk"
                >
                  <X size={16} />
                </button>
              </div>
            </div>
            <div className="p-5">
              <SavedReportDocument report={selectedReport} />
            </div>
          </div>
        </div>,
        document.body,
      )}
      <div className="fixed -left-[10000px] top-0 pointer-events-none opacity-0">
        <div ref={reportPdfRef}>
          {pdfReport && <SavedReportDocument report={pdfReport} />}
        </div>
      </div>
    </div>
  );
}
