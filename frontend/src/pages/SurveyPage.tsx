// src/pages/SurveyPage.tsx
import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { updateMyRiskProfile } from "../api/user";
import { isLoggedIn } from "../utils/auth";
import { getApiErrorMessage } from "../utils/errorMessage";
import {
  SURVEY_RESULT_STORAGE_KEY,
  syncFeature3RiskDefaults,
} from "../utils/riskProfile";

// 원본 문서 기준 총점 최댓값. Q4(파생상품 경험)는 informational 항목으로 점수 산정에서 제외.
const MAX_SCORE = 72;

type SingleOption = { score: number };
type InfoOption = Record<string, never>;

type Question =
  | { id: string; number: number; type: "single"; options: SingleOption[] }
  | { id: string; number: number; type: "multi"; options: SingleOption[] }
  | { id: string; number: number; type: "info"; options: InfoOption[] };

// 점수 구조만 유지 (label은 i18n에서)
const QUESTIONS: Question[] = [
  { id: "age", number: 1, type: "single", options: [{ score: 6 }, { score: 8 }, { score: 6 }, { score: 4 }, { score: 2 }] },
  { id: "investmentPeriod", number: 2, type: "single", options: [{ score: 10 }, { score: 8 }, { score: 6 }, { score: 4 }, { score: 2 }] },
  { id: "investmentExperience", number: 3, type: "multi", options: [{ score: 10 }, { score: 8 }, { score: 6 }, { score: 4 }, { score: 2 }] },
  { id: "derivativeExperience", number: 4, type: "info", options: [{}, {}, {}, {}] },
  { id: "lossTolerance", number: 5, type: "single", options: [{ score: 10 }, { score: 8 }, { score: 6 }, { score: 2 }] },
  { id: "investmentAssetRatio", number: 6, type: "single", options: [{ score: 2 }, { score: 4 }, { score: 6 }, { score: 8 }, { score: 10 }] },
  { id: "monthlyIncome", number: 7, type: "single", options: [{ score: 6 }, { score: 5 }, { score: 4 }, { score: 3 }, { score: 2 }] },
  { id: "investmentPurpose", number: 8, type: "single", options: [{ score: 2 }, { score: 4 }, { score: 6 }, { score: 8 }] },
  { id: "financialKnowledge", number: 9, type: "single", options: [{ score: 0 }, { score: 4 }, { score: 8 }, { score: 12 }] },
];

type Answers = Record<string, number | number[]>;

type ProfileKey = "안정형" | "안정추구형" | "위험중립형" | "적극투자형" | "공격투자형";

type ProfileType = {
  key: ProfileKey;
  maxGamma: number;
  bgClass: string;
  textClass: string;
};

const PROFILE_TYPES: ProfileType[] = [
  { key: "안정형", maxGamma: 0.20, bgClass: "bg-emerald-50 border-emerald-200 dark:bg-emerald-950/40 dark:border-emerald-800", textClass: "text-emerald-700 dark:text-emerald-300" },
  { key: "안정추구형", maxGamma: 0.40, bgClass: "bg-sky-50 border-sky-200 dark:bg-sky-950/40 dark:border-sky-800", textClass: "text-sky-700 dark:text-sky-300" },
  { key: "위험중립형", maxGamma: 0.60, bgClass: "bg-amber-50 border-amber-200 dark:bg-amber-950/40 dark:border-amber-800", textClass: "text-amber-700 dark:text-amber-300" },
  { key: "적극투자형", maxGamma: 0.80, bgClass: "bg-orange-50 border-orange-200 dark:bg-orange-950/40 dark:border-orange-800", textClass: "text-orange-700 dark:text-orange-300" },
  { key: "공격투자형", maxGamma: 1.00, bgClass: "bg-rose-50 border-rose-200 dark:bg-rose-950/40 dark:border-rose-800", textClass: "text-rose-700 dark:text-rose-300" },
];

const getProfileType = (gamma: number): ProfileType => {
  for (const p of PROFILE_TYPES) {
    if (gamma <= p.maxGamma) return p;
  }
  return PROFILE_TYPES[PROFILE_TYPES.length - 1];
};

type ScoreResult = {
  rawSum: number;
  rawMax: number;
  normalizedScore: number;
  rawGamma: number;
  gamma: number;
  profile: ProfileType;
  capApplied: boolean;
  capReason?: string;
};

const SINGLE_SCORED_IDS = [
  "age", "investmentPeriod", "lossTolerance", "investmentAssetRatio",
  "monthlyIncome", "investmentPurpose", "financialKnowledge",
];

const computeScore = (answers: Answers, t: TFunction<"surveyPage">): ScoreResult => {
  let sum = 0;

  for (const id of SINGLE_SCORED_IDS) {
    const q = QUESTIONS.find((qq) => qq.id === id);
    const idx = answers[id];
    if ((q?.type === "single") && typeof idx === "number") {
      sum += (q.options[idx] as SingleOption)?.score ?? 0;
    }
  }

  const q3 = QUESTIONS.find((qq) => qq.id === "investmentExperience");
  const q3Indices = answers["investmentExperience"];
  if (q3?.type === "multi" && Array.isArray(q3Indices) && q3Indices.length > 0) {
    const scores = q3Indices.map((i) => (q3.options[i] as SingleOption)?.score ?? 0);
    sum += Math.max(...scores);
  }

  const rawGamma = Math.min(1, sum / MAX_SCORE);
  let gamma = rawGamma;
  let capApplied = false;
  let capReason: string | undefined;

  const q5 = QUESTIONS.find((qq) => qq.id === "lossTolerance");
  const q5Idx = answers["lossTolerance"];
  if (q5?.type === "single" && typeof q5Idx === "number") {
    const q5Score = (q5.options[q5Idx] as SingleOption)?.score ?? 0;
    if (q5Score === 2) {
      if (gamma > 0.20) { gamma = 0.20; capApplied = true; capReason = t("cap.stable"); }
    } else if (q5Score === 6) {
      if (gamma > 0.60) { gamma = 0.60; capApplied = true; capReason = t("cap.neutral"); }
    }
  }

  return {
    rawSum: sum,
    rawMax: MAX_SCORE,
    normalizedScore: Math.round(rawGamma * 100 * 100) / 100,
    rawGamma: Math.round(rawGamma * 100) / 100,
    gamma: Math.round(gamma * 100) / 100,
    profile: getProfileType(gamma),
    capApplied,
    capReason,
  };
};

const isAnswered = (answers: Answers, qId: string): boolean => {
  const v = answers[qId];
  if (Array.isArray(v)) return v.length > 0;
  return typeof v === "number";
};

export default function SurveyPage() {
  const navigate = useNavigate();
  const { t } = useTranslation("surveyPage");
  const [answers, setAnswers] = useState<Answers>({});
  const [result, setResult] = useState<ScoreResult | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);
  const resultRef = useRef<HTMLDivElement | null>(null);
  const formTopRef = useRef<HTMLDivElement | null>(null);

  const answeredCount = useMemo(
    () => QUESTIONS.filter((q) => isAnswered(answers, q.id)).length,
    [answers],
  );
  const allAnswered = answeredCount === QUESTIONS.length;
  const progressPercent = Math.round((answeredCount / QUESTIONS.length) * 100);

  const handleSelectSingle = (qId: string, optIdx: number) => {
    setAnswers((prev) => ({ ...prev, [qId]: optIdx }));
  };

  const handleToggleMulti = (qId: string, optIdx: number) => {
    setAnswers((prev) => {
      const cur = (prev[qId] as number[] | undefined) ?? [];
      const next = cur.includes(optIdx) ? cur.filter((i) => i !== optIdx) : [...cur, optIdx];
      return { ...prev, [qId]: next };
    });
  };

  const handleSubmit = () => {
    setSubmitAttempted(true);
    if (!allAnswered) {
      const firstUnanswered = QUESTIONS.find((q) => !isAnswered(answers, q.id));
      if (firstUnanswered) {
        const el = document.getElementById(`survey-q-${firstUnanswered.id}`);
        el?.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      return;
    }
    const r = computeScore(answers, t);
    setResult(r);
    setTimeout(() => {
      resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 50);
  };

  const handleApply = async () => {
    if (!result) return;
    setApplying(true);
    setApplyError(null);
    try {
      if (isLoggedIn()) {
        await updateMyRiskProfile({ defaultRiskGamma: result.gamma });
      }
      syncFeature3RiskDefaults(result.gamma);
    } catch (e) {
      setApplyError(getApiErrorMessage(e, t("errors.saveFailed")));
      setApplying(false);
      return;
    }
    sessionStorage.setItem(SURVEY_RESULT_STORAGE_KEY, String(result.gamma));
    navigate("/feature/3");
  };

  const handleReset = () => {
    setAnswers({});
    setResult(null);
    setSubmitAttempted(false);
    formTopRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="min-h-screen bg-bg overflow-x-hidden md:ml-[84px]">
      <div className="w-full max-w-3xl mx-auto px-3 sm:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="w-full bg-surface border-b border-line px-3 sm:px-4 py-3 flex items-center justify-between gap-3">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-ink">
            {t("title")}
          </h1>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-1 text-sm text-ink-3 hover:text-ink transition-colors"
          >
            <ChevronLeft size={16} />
            {t("back")}
          </button>
        </header>

        <div ref={formTopRef} />

        {/* 안내 + 진행 현황 */}
        <section className="w-full bg-surface rounded-xl sm:rounded-2xl border border-line px-4 sm:px-5 py-4 flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <p className="text-sm text-ink-2">
              {t("progress.intro", { count: QUESTIONS.length })}
            </p>
            <span className="text-xs text-ink-3">
              {t("progress.answered", { answered: answeredCount, total: QUESTIONS.length, percent: progressPercent })}
            </span>
          </div>
          <div className="w-full h-1.5 bg-bg-sunk rounded-full overflow-hidden">
            <div
              className="h-full bg-accent transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </section>

        {/* 문항 카드 */}
        {QUESTIONS.map((q) => {
          const answered = isAnswered(answers, q.id);
          const showWarning = submitAttempted && !answered;
          const hasSubtitle = t(`questions.${q.id}.subtitle`, { defaultValue: "" }) !== "";
          return (
            <section
              key={q.id}
              id={`survey-q-${q.id}`}
              className={`w-full bg-surface rounded-xl sm:rounded-2xl border-2 transition-colors p-4 sm:p-6 flex flex-col gap-4 scroll-mt-6 ${
                showWarning ? "border-danger" : "border-line"
              }`}
            >
              <div className="flex flex-col gap-1.5">
                <span className="text-sm text-accent font-semibold">Q{q.number}</span>
                <h2 className="text-base sm:text-lg font-bold text-ink">
                  {t(`questions.${q.id}.title`)}
                </h2>
                {hasSubtitle && (
                  <p className="text-xs sm:text-sm text-ink-3">
                    {t(`questions.${q.id}.subtitle`)}
                  </p>
                )}
                {showWarning && (
                  <p className="text-xs text-danger mt-1">{t("warning.unanswered")}</p>
                )}
              </div>

              <div className="flex flex-col gap-2">
                {q.options.map((_, idx) => {
                  const label = t(`questions.${q.id}.options.${idx}`);
                  if (q.type === "multi") {
                    const selected = (answers[q.id] as number[] | undefined)?.includes(idx) ?? false;
                    return (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => handleToggleMulti(q.id, idx)}
                        className={`min-w-0 text-left px-3 sm:px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-2.5 sm:gap-3 ${
                          selected ? "border-accent bg-accent/10" : "border-line bg-surface hover:border-line-strong"
                        }`}
                      >
                        <span className={`w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 ${selected ? "border-accent bg-accent" : "border-line-strong bg-surface"}`}>
                          {selected && (
                            <svg className="w-3 h-3 text-white" viewBox="0 0 12 12" fill="none">
                              <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                          )}
                        </span>
                        <span className={`min-w-0 break-words text-sm sm:text-base ${selected ? "text-ink font-medium" : "text-ink-2"}`}>{label}</span>
                      </button>
                    );
                  }

                  const selected = answers[q.id] === idx;
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleSelectSingle(q.id, idx)}
                      className={`min-w-0 text-left px-3 sm:px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-2.5 sm:gap-3 ${
                        selected ? "border-accent bg-accent/10" : "border-line bg-surface hover:border-line-strong"
                      }`}
                    >
                      <span className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${selected ? "border-accent" : "border-line-strong"}`}>
                        {selected && <span className="w-2.5 h-2.5 rounded-full bg-accent" />}
                      </span>
                      <span className={`min-w-0 break-words text-sm sm:text-base ${selected ? "text-ink font-medium" : "text-ink-2"}`}>{label}</span>
                    </button>
                  );
                })}
              </div>
            </section>
          );
        })}

        {/* 제출 버튼 */}
        {!result && (
          <div className="w-full flex flex-col items-center gap-2 pt-2">
            <button
              type="button"
              onClick={handleSubmit}
              className="w-full sm:w-auto px-10 py-3 bg-accent text-white font-semibold text-base rounded-xl shadow-md hover:bg-accent-ink transition-all"
            >
              {t("submit")}
            </button>
            {submitAttempted && !allAnswered && (
              <p className="text-sm text-danger">{t("warning.hasUnanswered")}</p>
            )}
          </div>
        )}

        {/* 결과 섹션 */}
        {result && (
          <div ref={resultRef} className="flex flex-col gap-5 pt-2">
            <section className={`w-full rounded-2xl border-2 ${result.profile.bgClass} p-6 sm:p-8 flex flex-col gap-4 items-center text-center`}>
              <p className="text-sm text-ink-3">{t("result.profileIntro")}</p>
              <h2 className={`text-3xl sm:text-4xl font-bold ${result.profile.textClass}`}>
                {t(`profiles.${result.profile.key}.name` as `profiles.안정형.name`)}
              </h2>
              <p className="text-ink-2 text-sm sm:text-base leading-relaxed max-w-md">
                {t(`profiles.${result.profile.key}.description` as `profiles.안정형.description`)}
              </p>

              <div className="flex flex-col items-center gap-1 mt-3">
                <span className="text-xs text-ink-3">{t("result.gammaLabel")}</span>
                <span className={`text-5xl font-bold ${result.profile.textClass}`}>
                  {result.gamma.toFixed(2)}
                </span>
              </div>

              <div className="flex items-center gap-6 text-xs text-ink-3 mt-3">
                <div className="flex flex-col items-center">
                  <span>{t("result.rawScore")}</span>
                  <span className="font-semibold text-ink-2 mt-0.5">{result.rawSum} / {result.rawMax}</span>
                </div>
                <div className="flex flex-col items-center">
                  <span>{t("result.normalizedScore")}</span>
                  <span className="font-semibold text-ink-2 mt-0.5">{result.normalizedScore.toFixed(1)} / 100</span>
                </div>
              </div>

              {result.capApplied && (
                <div className="mt-4 px-4 py-3 rounded-lg bg-warn/10 border border-warn/40 text-xs sm:text-sm text-warn">
                  ⓘ {result.capReason}
                  <span className="block mt-1 text-warn/80">
                    {t("cap.beforeCap", { value: result.rawGamma.toFixed(2) })}
                  </span>
                </div>
              )}
            </section>

            <div className="flex flex-col sm:flex-row justify-between gap-3">
              <button
                type="button"
                onClick={handleReset}
                disabled={applying}
                className="px-5 py-2.5 rounded-lg border border-line bg-surface text-sm font-medium text-ink-2 hover:bg-bg-soft transition-colors disabled:opacity-50"
              >
                {t("result.reset")}
              </button>
              <button
                type="button"
                onClick={handleApply}
                disabled={applying}
                className="px-7 py-2.5 rounded-lg bg-accent text-white text-sm font-semibold hover:bg-accent-ink transition-all disabled:opacity-50"
              >
                {applying ? t("result.applying") : t("result.apply")}
              </button>
            </div>
            {applyError && <p className="text-sm text-danger">{applyError}</p>}
          </div>
        )}
      </div>
    </div>
  );
}
