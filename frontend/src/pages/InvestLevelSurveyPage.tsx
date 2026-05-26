// src/pages/InvestLevelSurveyPage.tsx
// 투자레벨 설문: 3단계 용어 체크리스트.
// 통과 기준(=4개)과 단계 수·진척도는 의도적으로 노출하지 않는다.
// 사용자가 통과 기준을 알면 한두 개 더 체크해서 상향을 노리는 심리를 피하기 위함.
// 결과 화면에서 4단계 드롭다운으로 상·하향 수동 조정 허용.
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Check, ChevronDown, Info } from "lucide-react";
import { useTranslation } from "react-i18next";
import { updateMyProfile } from "../api/user";
import { getApiErrorMessage } from "../utils/errorMessage";
import { useDictionary } from "../components/DictContext";
import {
  INVEST_LEVELS,
  type InvestLevel,
} from "../utils/investLevel";
import { investLevelLabel } from "../utils/displayLabels";

const PASS_THRESHOLD = 4;

type Stage = {
  index: 1 | 2 | 3;
  passLevel: InvestLevel;
  failLevel: InvestLevel;
  terms: string[];
};

const STAGES: Stage[] = [
  {
    index: 1,
    passLevel: "중급자",
    failLevel: "초급자",
    terms: ["PER", "RSI", "외국인 순매수/순매도", "어닝 서프라이즈", "컨센서스", "국채금리", "달러인덱스(DXY)"],
  },
  {
    index: 2,
    passLevel: "고급자",
    failLevel: "중급자",
    terms: ["ROE", "PBR", "볼린저밴드", "숏커버링", "목표주가 괴리율", "섹터 로테이션", "장단기 금리차(스프레드)"],
  },
  {
    index: 3,
    passLevel: "전문가",
    failLevel: "고급자",
    terms: ["DCF", "EV/EBITDA", "멀티플 리레이팅", "공매도 잔고비율", "Put/Call Ratio", "듀레이션", "테이퍼링"],
  },
];

export default function InvestLevelSurveyPage() {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation("investLevelPage");
  const { setInvestLevel: setCtxInvestLevel } = useDictionary();

  const [step, setStep] = useState<1 | 2 | 3 | "result">(1);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [finalLevel, setFinalLevel] = useState<InvestLevel>("초급자");
  const [levelDropdownOpen, setLevelDropdownOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const currentStage = step !== "result" ? STAGES[step - 1] : null;

  const handleToggle = (term: string) => {
    setChecked((prev) => {
      const next = new Set(prev);
      if (next.has(term)) next.delete(term);
      else next.add(term);
      return next;
    });
  };

  const handleNext = () => {
    if (!currentStage) return;
    const passed = checked.size >= PASS_THRESHOLD;
    if (passed && currentStage.index < 3) {
      setChecked(new Set());
      setStep((currentStage.index + 1) as 1 | 2 | 3);
      return;
    }
    const decided: InvestLevel = passed ? currentStage.passLevel : currentStage.failLevel;
    setFinalLevel(decided);
    setStep("result");
  };

  const handleRestart = () => {
    setChecked(new Set());
    setStep(1);
    setFinalLevel("초급자");
    setSaveError(null);
  };

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    setSaveError(null);
    try {
      await updateMyProfile({ experience: finalLevel });
      setCtxInvestLevel(finalLevel);
      navigate("/setting");
    } catch (e) {
      setSaveError(getApiErrorMessage(e, t("errors.saveFailed")));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg overflow-x-hidden md:ml-[84px]">
      <div className="w-full max-w-3xl mx-auto px-3 sm:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
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

        {/* 안내 */}
        {step !== "result" && (
          <section className="w-full rounded-xl sm:rounded-2xl border border-accent/30 bg-accent-soft px-4 sm:px-5 py-4 flex items-start gap-3">
            <Info size={18} className="text-accent flex-shrink-0 mt-0.5" />
            <div className="min-w-0 flex flex-col gap-1.5">
              <p className="text-sm font-semibold text-ink">{t("notice.title")}</p>
              <p className="text-xs sm:text-sm text-ink-2 leading-relaxed">{t("notice.body")}</p>
            </div>
          </section>
        )}

        {/* 단계 카드 */}
        {currentStage && (
          <section
            key={currentStage.index}
            className="w-full bg-surface rounded-xl sm:rounded-2xl border-2 border-line p-4 sm:p-6 flex flex-col gap-4 sm:gap-5"
          >
            <div className="flex flex-col gap-1.5">
              <h2 className="text-base sm:text-lg font-bold text-ink">
                {t("stageQuestion")}
              </h2>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {currentStage.terms.map((term) => {
                const selected = checked.has(term);
                return (
                  <button
                    key={term}
                    type="button"
                    onClick={() => handleToggle(term)}
                    className={`min-w-0 text-left px-3 sm:px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-2.5 sm:gap-3 ${
                      selected ? "border-accent bg-accent/10" : "border-line bg-surface hover:border-line-strong"
                    }`}
                  >
                    <span className={`w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 ${selected ? "border-accent bg-accent" : "border-line-strong bg-surface"}`}>
                      {selected && <Check size={14} className="text-white" />}
                    </span>
                    <span className={`min-w-0 break-words text-sm sm:text-base ${selected ? "text-ink font-medium" : "text-ink-2"}`}>
                      {term}
                    </span>
                  </button>
                );
              })}
            </div>

            <div className="flex justify-end pt-1">
              <button
                type="button"
                onClick={handleNext}
                className="px-7 py-2.5 rounded-xl bg-accent text-white text-sm sm:text-base font-semibold hover:bg-accent-ink transition-all"
              >
                {t("next")}
              </button>
            </div>
          </section>
        )}

        {/* 결과 화면 */}
        {step === "result" && (
          <div className="flex flex-col gap-5">
            <section className="w-full rounded-2xl border-2 border-accent/30 bg-accent-soft p-6 sm:p-8 flex flex-col gap-4 items-center text-center">
              <p className="text-sm text-ink-3">{t("result.profileIntro")}</p>
              <h2 className="max-w-full break-words text-3xl sm:text-4xl font-bold text-accent">{investLevelLabel(finalLevel, i18n.language)}</h2>
              <p className="text-ink-2 text-sm sm:text-base leading-relaxed max-w-md">
                {t(`levels.${finalLevel}` as `levels.초급자`)}
              </p>
            </section>

            {/* 수동 조정 드롭다운 */}
            <section className="w-full bg-surface rounded-2xl border border-line p-5 sm:p-6 flex flex-col gap-3">
              <div>
                <h3 className="text-sm font-bold text-ink tracking-tight">{t("result.adjust.title")}</h3>
                <p className="mt-1 text-xs text-ink-3">{t("result.adjust.desc")}</p>
              </div>

              <div className="relative w-full sm:w-64">
                <button
                  type="button"
                  onClick={() => setLevelDropdownOpen((o) => !o)}
                  className={`inline-flex items-center justify-between gap-2 w-full pl-3.5 pr-3 py-2.5 rounded-lg bg-bg-sunk border text-sm text-ink transition-colors ${levelDropdownOpen ? "border-accent" : "border-line"}`}
                >
                  <span className="truncate">{investLevelLabel(finalLevel, i18n.language)}</span>
                  <ChevronDown size={16} className={`text-ink-3 flex-shrink-0 pointer-events-none transition-transform ${levelDropdownOpen ? "rotate-180" : ""}`} />
                </button>
                {levelDropdownOpen && (
                  <div className="absolute z-30 top-full left-0 mt-1 w-full bg-surface border border-line rounded-lg shadow-pop overflow-hidden">
                    {INVEST_LEVELS.map((lv) => (
                      <button
                        key={lv}
                        type="button"
                        onClick={() => { setFinalLevel(lv); setLevelDropdownOpen(false); }}
                        className={`w-full text-left px-3.5 py-2.5 text-sm transition-colors ${lv === finalLevel ? "bg-accent-soft text-accent font-semibold" : "text-ink hover:bg-bg-sunk"}`}
                      >
                        {investLevelLabel(lv, i18n.language)}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </section>

            {saveError && <p className="text-sm text-danger">{saveError}</p>}

            <div className="flex flex-col sm:flex-row justify-between gap-3">
              <button
                type="button"
                onClick={handleRestart}
                disabled={saving}
                className="px-5 py-2.5 rounded-lg border border-line bg-surface text-sm font-medium text-ink-2 hover:bg-bg-sunk transition-colors disabled:opacity-50"
              >
                {t("result.reset")}
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="px-7 py-2.5 rounded-lg bg-accent text-white text-sm font-semibold hover:bg-accent-ink transition-all disabled:opacity-50"
              >
                {saving ? t("result.saving") : t("result.save")}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
