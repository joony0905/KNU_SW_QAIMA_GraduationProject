// 사용자의 투자 지식 수준(=백엔드 User.experience 문자열) 표준 라벨.
// 기본값(미설문/미설정)은 "초급자".
export const INVEST_LEVELS = ["초급자", "중급자", "고급자", "전문가"] as const;
export type InvestLevel = (typeof INVEST_LEVELS)[number];

export const DEFAULT_INVEST_LEVEL: InvestLevel = "초급자";

export const toInvestLevel = (v: string | null | undefined): InvestLevel =>
  (INVEST_LEVELS as readonly string[]).includes(v ?? "")
    ? (v as InvestLevel)
    : DEFAULT_INVEST_LEVEL;
