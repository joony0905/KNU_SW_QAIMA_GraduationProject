// src/pages/InvestLevelSurveyPage.tsx
// 투자레벨 설문: 3단계 용어 체크리스트.
// 통과 기준(=4개)과 단계 수·진척도는 의도적으로 노출하지 않는다.
// 사용자가 통과 기준을 알면 한두 개 더 체크해서 상향을 노리는 심리를 피하기 위함.
// 결과 화면에서 4단계 드롭다운으로 상·하향 수동 조정 허용.
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Check, ChevronDown, Info } from "lucide-react";
import { updateMyProfile } from "../api/user";
import { getApiErrorMessage } from "../utils/errorMessage";
import { useDictionary } from "../components/DictContext";
import {
  INVEST_LEVELS,
  type InvestLevel,
} from "../utils/investLevel";

const PASS_THRESHOLD = 4;

type Stage = {
  index: 1 | 2 | 3;
  passLevel: InvestLevel; // 통과 시 도달하는 등급
  failLevel: InvestLevel; // 실패 시 확정되는 등급
  terms: string[];
};

const STAGES: Stage[] = [
  {
    index: 1,
    passLevel: "중급자",
    failLevel: "초급자",
    terms: [
      "PER",
      "RSI",
      "외국인 순매수/순매도",
      "어닝 서프라이즈",
      "컨센서스",
      "국채금리",
      "달러인덱스(DXY)",
    ],
  },
  {
    index: 2,
    passLevel: "고급자",
    failLevel: "중급자",
    terms: [
      "ROE",
      "PBR",
      "볼린저밴드",
      "숏커버링",
      "목표주가 괴리율",
      "섹터 로테이션",
      "장단기 금리차(스프레드)",
    ],
  },
  {
    index: 3,
    passLevel: "전문가",
    failLevel: "고급자",
    terms: [
      "DCF",
      "EV/EBITDA",
      "멀티플 리레이팅",
      "공매도 잔고비율",
      "Put/Call Ratio",
      "듀레이션",
      "테이퍼링",
    ],
  },
];

const LEVEL_DESCRIPTIONS: Record<InvestLevel, string> = {
  초급자: "기본 용어 위주의 쉬운 풀이로 분석 결과를 받아봅니다.",
  중급자: "주요 보조지표·수급 지표 해석을 포함한 분석을 받아봅니다.",
  고급자: "가치평가·매크로 지표를 함께 다룬 분석을 받아봅니다.",
  전문가: "파생·고급 매크로 용어를 그대로 사용한 분석을 받아봅니다.",
};

export default function InvestLevelSurveyPage() {
  const navigate = useNavigate();
  const { setInvestLevel: setCtxInvestLevel } = useDictionary();

  // 진행 상태: 1·2·3 단계 중 어느 단계인지, 또는 "결과" 단계인지.
  const [step, setStep] = useState<1 | 2 | 3 | "result">(1);
  // 현재 단계의 체크 상태 (단계 간 이동 시 초기화)
  const [checked, setChecked] = useState<Set<string>>(new Set());
  // 최종 확정 등급 (결과 단계에서 드롭다운으로 수동 조정 가능)
  const [finalLevel, setFinalLevel] = useState<InvestLevel>("초급자");
  // 드롭다운 열림 여부
  const [levelDropdownOpen, setLevelDropdownOpen] = useState(false);
  // 저장 진행 / 에러
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
      // 다음 단계로
      setChecked(new Set());
      setStep((currentStage.index + 1) as 1 | 2 | 3);
      return;
    }
    // 결과 확정
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
      setSaveError(getApiErrorMessage(e, "투자레벨 저장에 실패했습니다."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-6">
        {/* 헤더 */}
        <header className="w-full bg-surface border-b border-line px-4 py-3 flex items-center justify-between">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-ink">
            투자레벨 설문
          </h1>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-1 text-sm text-ink-3 hover:text-ink transition-colors"
          >
            <ChevronLeft size={16} />
            돌아가기
          </button>
        </header>

        {/* 안내: 등급이 높을수록 좋은 것이 아니다 */}
        {step !== "result" && (
          <section className="w-full rounded-2xl border border-accent/30 bg-accent-soft px-5 py-4 flex items-start gap-3">
            <Info size={18} className="text-accent flex-shrink-0 mt-0.5" />
            <div className="flex flex-col gap-1.5">
              <p className="text-sm font-semibold text-ink">
                등급이 높다고 더 좋은 것이 아닙니다.
              </p>
              <p className="text-xs sm:text-sm text-ink-2 leading-relaxed">
                투자레벨은 분석 보고서에 쓰이는 용어의 난이도를 정하는 기준일 뿐입니다.
                익숙하지 않은 용어가 가득한 보고서는 오히려 의사결정을 어렵게 만듭니다.
                실제로 본인이 편하게 이해하는 만큼만 솔직하게 선택해주세요.
              </p>
            </div>
          </section>
        )}

        {/* 단계 카드 */}
        {currentStage && (
          <section
            key={currentStage.index}
            className="w-full bg-surface rounded-2xl border-2 border-line p-5 sm:p-6 flex flex-col gap-5"
          >
            <div className="flex flex-col gap-1.5">
              <h2 className="text-base sm:text-lg font-bold text-ink">
                아래 용어 중, 뜻을 알거나 실제로 투자 판단에 써본 적 있는 것을 모두 선택해주세요.
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
                    className={`text-left px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-3 ${
                      selected
                        ? "border-accent bg-accent/10"
                        : "border-line bg-surface hover:border-line-strong"
                    }`}
                  >
                    <span
                      className={`w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 ${
                        selected
                          ? "border-accent bg-accent"
                          : "border-line-strong bg-surface"
                      }`}
                    >
                      {selected && <Check size={14} className="text-white" />}
                    </span>
                    <span
                      className={`text-sm sm:text-base ${
                        selected ? "text-ink font-medium" : "text-ink-2"
                      }`}
                    >
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
                다음
              </button>
            </div>
          </section>
        )}

        {/* 결과 화면 */}
        {step === "result" && (
          <div className="flex flex-col gap-5">
            <section className="w-full rounded-2xl border-2 border-accent/30 bg-accent-soft p-6 sm:p-8 flex flex-col gap-4 items-center text-center">
              <p className="text-sm text-ink-3">고객님의 투자레벨은</p>
              <h2 className="text-3xl sm:text-4xl font-bold text-accent">
                {finalLevel}
              </h2>
              <p className="text-ink-2 text-sm sm:text-base leading-relaxed max-w-md">
                {LEVEL_DESCRIPTIONS[finalLevel]}
              </p>
            </section>

            {/* 수동 조정 드롭다운 */}
            <section className="w-full bg-surface rounded-2xl border border-line p-5 sm:p-6 flex flex-col gap-3">
              <div>
                <h3 className="text-sm font-bold text-ink tracking-tight">
                  등급을 직접 조정할 수 있습니다
                </h3>
                <p className="mt-1 text-xs text-ink-3">
                  설문 결과와 다른 등급이 더 적합하다면 아래에서 변경하세요. 분석 보고서의 용어 난이도가 함께 바뀝니다.
                </p>
              </div>

              <div className="relative w-full sm:w-64">
                <button
                  type="button"
                  onClick={() => setLevelDropdownOpen((o) => !o)}
                  className={`inline-flex items-center justify-between gap-2 w-full pl-3.5 pr-3 py-2.5 rounded-lg bg-bg-sunk border text-sm text-ink transition-colors ${
                    levelDropdownOpen ? "border-accent" : "border-line"
                  }`}
                >
                  <span className="truncate">{finalLevel}</span>
                  <ChevronDown
                    size={16}
                    className={`text-ink-3 flex-shrink-0 pointer-events-none transition-transform ${
                      levelDropdownOpen ? "rotate-180" : ""
                    }`}
                  />
                </button>
                {levelDropdownOpen && (
                  <div className="absolute z-30 top-full left-0 mt-1 w-full bg-surface border border-line rounded-lg shadow-pop overflow-hidden">
                    {INVEST_LEVELS.map((lv) => (
                      <button
                        key={lv}
                        type="button"
                        onClick={() => {
                          setFinalLevel(lv);
                          setLevelDropdownOpen(false);
                        }}
                        className={`w-full text-left px-3.5 py-2.5 text-sm transition-colors ${
                          lv === finalLevel
                            ? "bg-accent-soft text-accent font-semibold"
                            : "text-ink hover:bg-bg-sunk"
                        }`}
                      >
                        {lv}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </section>

            {saveError && (
              <p className="text-sm text-danger">{saveError}</p>
            )}

            <div className="flex flex-col sm:flex-row justify-between gap-3">
              <button
                type="button"
                onClick={handleRestart}
                disabled={saving}
                className="px-5 py-2.5 rounded-lg border border-line bg-surface text-sm font-medium text-ink-2 hover:bg-bg-sunk transition-colors disabled:opacity-50"
              >
                다시 응답하기
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="px-7 py-2.5 rounded-lg bg-accent text-white text-sm font-semibold hover:bg-accent-ink transition-all disabled:opacity-50"
              >
                {saving ? "저장 중..." : "저장하고 설정으로 돌아가기"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
