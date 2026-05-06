// src/pages/SurveyPage.tsx
import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft } from "lucide-react";

const RISK_GAMMA_STORAGE_KEY = "qaima_risk_gamma";
const SURVEY_RESULT_STORAGE_KEY = "qaima_survey_result";

// 원본 문서 기준 총점 최댓값. Q4(파생상품 경험)는 informational 항목으로 점수 산정에서 제외.
const MAX_SCORE = 72;

type SingleOption = { label: string; score: number };
type InfoOption = { label: string };

type Question =
  | { id: string; number: number; title: string; subtitle?: string; type: "single"; options: SingleOption[] }
  | { id: string; number: number; title: string; subtitle?: string; type: "multi"; options: SingleOption[] }
  | { id: string; number: number; title: string; subtitle?: string; type: "info"; options: InfoOption[] };

const QUESTIONS: Question[] = [
  {
    id: "age",
    number: 1,
    title: "고객님의 연령대를 선택해주세요.",
    type: "single",
    options: [
      { label: "만 19세 이하", score: 6 },
      { label: "만 20세 ~ 40세", score: 8 },
      { label: "만 41세 ~ 50세", score: 6 },
      { label: "만 51세 ~ 60세", score: 4 },
      { label: "만 61세 이상", score: 2 },
    ],
  },
  {
    id: "investmentPeriod",
    number: 2,
    title: "투자 예정 기간은 어느 정도인가요?",
    type: "single",
    options: [
      { label: "3년 이상", score: 10 },
      { label: "2년 이상 ~ 3년 미만", score: 8 },
      { label: "1년 이상 ~ 2년 미만", score: 6 },
      { label: "6개월 이상 ~ 1년 미만", score: 4 },
      { label: "6개월 미만", score: 2 },
    ],
  },
  {
    id: "investmentExperience",
    number: 3,
    title: "투자해보신 상품을 모두 선택해주세요.",
    subtitle: "중복 선택 가능. 선택한 상품 중 가장 높은 점수가 반영됩니다.",
    type: "multi",
    options: [
      { label: "공격투자형 상품", score: 10 },
      { label: "적극투자형 상품", score: 8 },
      { label: "위험중립형 상품", score: 6 },
      { label: "안정추구형 상품", score: 4 },
      { label: "안정형 상품", score: 2 },
    ],
  },
  {
    id: "derivativeExperience",
    number: 4,
    title: "파생상품 등 투자 경험이 있으신가요?",
    subtitle: "ELS / DLS / 파생펀드 등. 참고용 항목으로 점수 산정에는 반영되지 않습니다.",
    type: "info",
    options: [
      { label: "1년 미만" },
      { label: "1년 이상 ~ 3년 미만" },
      { label: "3년 이상" },
      { label: "경험 없음" },
    ],
  },
  {
    id: "lossTolerance",
    number: 5,
    title: "감내할 수 있는 손실 수준은 어느 정도인가요?",
    type: "single",
    options: [
      { label: "기대수익이 높다면 원금에 손실이 발생해도 상관하지 않음", score: 10 },
      { label: "투자원금 중 일부의 손실을 감수할 수 있음", score: 8 },
      { label: "투자원금에서 최소한의 손실만을 감수할 수 있음", score: 6 },
      { label: "무슨 일이 있어도 투자원금은 보전되어야 함", score: 2 },
    ],
  },
  {
    id: "investmentAssetRatio",
    number: 6,
    title: "총 자산 대비 투자성 자산의 비중은?",
    type: "single",
    options: [
      { label: "10% 이하", score: 2 },
      { label: "30% 이하", score: 4 },
      { label: "50% 이하", score: 6 },
      { label: "70% 이하", score: 8 },
      { label: "70% 초과", score: 10 },
    ],
  },
  {
    id: "monthlyIncome",
    number: 7,
    title: "월 소득 현황을 선택해주세요.",
    type: "single",
    options: [
      { label: "500만원 초과", score: 6 },
      { label: "500만원 이하", score: 5 },
      { label: "300만원 이하", score: 4 },
      { label: "200만원 이하", score: 3 },
      { label: "100만원 이하", score: 2 },
    ],
  },
  {
    id: "investmentPurpose",
    number: 8,
    title: "투자 목적은 무엇인가요?",
    type: "single",
    options: [
      { label: "생계(단기)자금 운용", score: 2 },
      { label: "예적금 수준 수익률 기대", score: 4 },
      { label: "시장 평균 이상 수익률 기대", score: 6 },
      { label: "적극적인 재산(자산) 증식", score: 8 },
    ],
  },
  {
    id: "financialKnowledge",
    number: 9,
    title: "금융 지식 수준 / 이해도는?",
    type: "single",
    options: [
      { label: "금융투자상품에 투자한 경험이 없음", score: 0 },
      { label: "주식·채권·펀드 등 널리 알려진 상품의 구조와 위험을 일정 부분 이해", score: 4 },
      { label: "주식·채권·펀드 등 널리 알려진 상품의 구조와 위험을 깊이 있게 이해", score: 8 },
      { label: "파생상품을 포함한 대부분의 금융투자상품 구조와 위험을 이해", score: 12 },
    ],
  },
];

type Answers = Record<string, number | number[]>;

type ProfileType = {
  name: string;
  maxGamma: number;
  description: string;
  bgClass: string;
  textClass: string;
};

const PROFILE_TYPES: ProfileType[] = [
  {
    name: "안정형",
    maxGamma: 0.20,
    description:
      "원금 보전을 최우선으로 하는 보수적 성향. 예적금 수준의 안정 자산 위주 배분이 적합합니다.",
    bgClass: "bg-emerald-50 border-emerald-200",
    textClass: "text-emerald-700",
  },
  {
    name: "안정추구형",
    maxGamma: 0.40,
    description:
      "원금 손실 위험을 최소화하면서 예적금 이상의 수익을 추구. 채권 중심의 배분이 적합합니다.",
    bgClass: "bg-sky-50 border-sky-200",
    textClass: "text-sky-700",
  },
  {
    name: "위험중립형",
    maxGamma: 0.60,
    description:
      "위험과 수익의 균형을 추구. 채권과 주식을 균형 있게 배분합니다.",
    bgClass: "bg-amber-50 border-amber-200",
    textClass: "text-amber-700",
  },
  {
    name: "적극투자형",
    maxGamma: 0.80,
    description:
      "시장 평균 이상의 수익을 위해 변동성을 감수. 주식 비중이 높은 배분이 적합합니다.",
    bgClass: "bg-orange-50 border-orange-200",
    textClass: "text-orange-700",
  },
  {
    name: "공격투자형",
    maxGamma: 1.00,
    description:
      "장기 고수익 추구를 위해 단기 변동성을 적극 감수. 주식·고변동성 자산 위주 배분이 적합합니다.",
    bgClass: "bg-rose-50 border-rose-200",
    textClass: "text-rose-700",
  },
];

const getProfileType = (gamma: number): ProfileType => {
  for (const t of PROFILE_TYPES) {
    if (gamma <= t.maxGamma) return t;
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
  "age",
  "investmentPeriod",
  "lossTolerance",
  "investmentAssetRatio",
  "monthlyIncome",
  "investmentPurpose",
  "financialKnowledge",
];

const computeScore = (answers: Answers): ScoreResult => {
  let sum = 0;

  for (const id of SINGLE_SCORED_IDS) {
    const q = QUESTIONS.find((qq) => qq.id === id);
    const idx = answers[id];
    if (q?.type === "single" && typeof idx === "number") {
      sum += q.options[idx]?.score ?? 0;
    }
  }

  const q3 = QUESTIONS.find((qq) => qq.id === "investmentExperience");
  const q3Indices = answers["investmentExperience"];
  if (q3?.type === "multi" && Array.isArray(q3Indices) && q3Indices.length > 0) {
    const scores = q3Indices.map((i) => q3.options[i]?.score ?? 0);
    sum += Math.max(...scores);
  }

  // Q4: informational, score 미반영

  const rawGamma = Math.min(1, sum / MAX_SCORE);
  let gamma = rawGamma;
  let capApplied = false;
  let capReason: string | undefined;

  // Q5 한도 제한
  const q5 = QUESTIONS.find((qq) => qq.id === "lossTolerance");
  const q5Idx = answers["lossTolerance"];
  if (q5?.type === "single" && typeof q5Idx === "number") {
    const q5Score = q5.options[q5Idx]?.score ?? 0;
    if (q5Score === 2) {
      if (gamma > 0.20) {
        gamma = 0.20;
        capApplied = true;
        capReason = "'무슨 일이 있어도 원금은 보전' 응답으로 안정형 한도가 적용되었습니다.";
      }
    } else if (q5Score === 6) {
      if (gamma > 0.60) {
        gamma = 0.60;
        capApplied = true;
        capReason = "'최소한의 손실만 감수' 응답으로 위험중립형 한도가 적용되었습니다.";
      }
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
  const [answers, setAnswers] = useState<Answers>({});
  const [result, setResult] = useState<ScoreResult | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);
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
      const next = cur.includes(optIdx)
        ? cur.filter((i) => i !== optIdx)
        : [...cur, optIdx];
      return { ...prev, [qId]: next };
    });
  };

  const handleSubmit = () => {
    setSubmitAttempted(true);
    if (!allAnswered) {
      // 첫 미응답 문항으로 스크롤
      const firstUnanswered = QUESTIONS.find((q) => !isAnswered(answers, q.id));
      if (firstUnanswered) {
        const el = document.getElementById(`survey-q-${firstUnanswered.id}`);
        el?.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      return;
    }
    const r = computeScore(answers);
    setResult(r);
    // 결과로 스크롤
    setTimeout(() => {
      resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 50);
  };

  const handleApply = () => {
    if (!result) return;
    sessionStorage.setItem(SURVEY_RESULT_STORAGE_KEY, String(result.gamma));
    localStorage.setItem(RISK_GAMMA_STORAGE_KEY, String(result.gamma));
    navigate("/feature/3");
  };

  const handleReset = () => {
    setAnswers({});
    setResult(null);
    setSubmitAttempted(false);
    formTopRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[84px]">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 flex flex-col gap-6">
        <header className="w-full bg-white border-b border-neutral-200 px-4 py-3 flex items-center justify-between">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            투자 성향 설문
          </h1>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ChevronLeft size={16} />
            돌아가기
          </button>
        </header>

        <div ref={formTopRef} />

        {/* 안내 + 진행 현황 */}
        <section className="w-full bg-white rounded-2xl border border-stone-300 px-5 py-4 flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <p className="text-sm text-gray-700">
              총 <span className="font-semibold text-[#3F51B5]">{QUESTIONS.length}개</span> 문항입니다. 모두 응답하신 뒤 하단의 결과 보기 버튼을 눌러주세요.
            </p>
            <span className="text-xs text-gray-500">
              {answeredCount} / {QUESTIONS.length} 응답 ({progressPercent}%)
            </span>
          </div>
          <div className="w-full h-1.5 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-[#3F51B5] transition-all duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </section>

        {/* 문항 카드 — 스크롤 형식 */}
        {QUESTIONS.map((q) => {
          const answered = isAnswered(answers, q.id);
          const showWarning = submitAttempted && !answered;
          return (
            <section
              key={q.id}
              id={`survey-q-${q.id}`}
              className={`w-full bg-white rounded-2xl border-2 transition-colors p-5 sm:p-6 flex flex-col gap-4 scroll-mt-6 ${
                showWarning ? "border-rose-300" : "border-stone-300"
              }`}
            >
              <div className="flex flex-col gap-1.5">
                <span className="text-sm text-[#3F51B5] font-semibold">
                  Q{q.number}
                </span>
                <h2 className="text-base sm:text-lg font-bold text-gray-800">
                  {q.title}
                </h2>
                {q.subtitle && (
                  <p className="text-xs sm:text-sm text-gray-500">{q.subtitle}</p>
                )}
                {showWarning && (
                  <p className="text-xs text-rose-600 mt-1">
                    이 문항에 답변해주세요.
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-2">
                {q.options.map((opt, idx) => {
                  if (q.type === "multi") {
                    const selected =
                      (answers[q.id] as number[] | undefined)?.includes(idx) ?? false;
                    return (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => handleToggleMulti(q.id, idx)}
                        className={`text-left px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-3 ${
                          selected
                            ? "border-[#3F51B5] bg-[#3F51B5]/5"
                            : "border-gray-200 bg-white hover:border-gray-300"
                        }`}
                      >
                        <span
                          className={`w-5 h-5 rounded border-2 flex items-center justify-center flex-shrink-0 ${
                            selected
                              ? "border-[#3F51B5] bg-[#3F51B5]"
                              : "border-gray-300 bg-white"
                          }`}
                        >
                          {selected && (
                            <svg
                              className="w-3 h-3 text-white"
                              viewBox="0 0 12 12"
                              fill="none"
                            >
                              <path
                                d="M2 6L5 9L10 3"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          )}
                        </span>
                        <span
                          className={`text-sm sm:text-base ${
                            selected ? "text-gray-900 font-medium" : "text-gray-700"
                          }`}
                        >
                          {opt.label}
                        </span>
                      </button>
                    );
                  }

                  const selected = answers[q.id] === idx;
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleSelectSingle(q.id, idx)}
                      className={`text-left px-4 py-3 rounded-xl border-2 transition-all flex items-center gap-3 ${
                        selected
                          ? "border-[#3F51B5] bg-[#3F51B5]/5"
                          : "border-gray-200 bg-white hover:border-gray-300"
                      }`}
                    >
                      <span
                        className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${
                          selected ? "border-[#3F51B5]" : "border-gray-300"
                        }`}
                      >
                        {selected && (
                          <span className="w-2.5 h-2.5 rounded-full bg-[#3F51B5]" />
                        )}
                      </span>
                      <span
                        className={`text-sm sm:text-base ${
                          selected ? "text-gray-900 font-medium" : "text-gray-700"
                        }`}
                      >
                        {opt.label}
                      </span>
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
              className="w-full sm:w-auto px-10 py-3 bg-[#3F51B5] text-white font-semibold text-base rounded-xl shadow-md hover:bg-[#354499] transition-all"
            >
              결과 보기
            </button>
            {submitAttempted && !allAnswered && (
              <p className="text-sm text-rose-600">
                응답하지 않은 문항이 있습니다. 미응답 문항으로 이동했습니다.
              </p>
            )}
          </div>
        )}

        {/* 결과 섹션 */}
        {result && (
          <div ref={resultRef} className="flex flex-col gap-5 pt-2">
            <section
              className={`w-full rounded-2xl border-2 ${result.profile.bgClass} p-6 sm:p-8 flex flex-col gap-4 items-center text-center`}
            >
              <p className="text-sm text-gray-500">고객님의 투자 성향은</p>
              <h2
                className={`text-3xl sm:text-4xl font-bold ${result.profile.textClass}`}
              >
                {result.profile.name}
              </h2>
              <p className="text-gray-700 text-sm sm:text-base leading-relaxed max-w-md">
                {result.profile.description}
              </p>

              <div className="flex flex-col items-center gap-1 mt-3">
                <span className="text-xs text-gray-500">투자 성향 지수</span>
                <span
                  className={`text-5xl font-bold ${result.profile.textClass}`}
                >
                  {result.gamma.toFixed(2)}
                </span>
              </div>

              <div className="flex items-center gap-6 text-xs text-gray-500 mt-3">
                <div className="flex flex-col items-center">
                  <span>원점수</span>
                  <span className="font-semibold text-gray-700 mt-0.5">
                    {result.rawSum} / {result.rawMax}
                  </span>
                </div>
                <div className="flex flex-col items-center">
                  <span>환산점수</span>
                  <span className="font-semibold text-gray-700 mt-0.5">
                    {result.normalizedScore.toFixed(1)} / 100
                  </span>
                </div>
              </div>

              {result.capApplied && (
                <div className="mt-4 px-4 py-3 rounded-lg bg-white/70 border border-amber-300 text-xs sm:text-sm text-amber-800">
                  ⓘ {result.capReason}
                  <span className="block mt-1 text-amber-700/80">
                    한도 적용 전 지수: {result.rawGamma.toFixed(2)}
                  </span>
                </div>
              )}
            </section>

            <div className="flex flex-col sm:flex-row justify-between gap-3">
              <button
                type="button"
                onClick={handleReset}
                className="px-5 py-2.5 rounded-lg border border-gray-300 bg-white text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
              >
                다시 응답하기
              </button>
              <button
                type="button"
                onClick={handleApply}
                className="px-7 py-2.5 rounded-lg bg-[#3F51B5] text-white text-sm font-semibold hover:bg-[#354499] transition-all"
              >
                포트폴리오에 적용하기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
