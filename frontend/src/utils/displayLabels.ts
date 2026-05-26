import type { InvestLevel } from "./investLevel";
import type { RiskProfileLabel } from "./riskProfile";

export const isEnglishLanguage = (language?: string | null): boolean =>
  (language ?? "").toLowerCase().startsWith("en");

const INVEST_LEVEL_EN: Record<InvestLevel, string> = {
  초급자: "Beginner",
  중급자: "Intermediate",
  고급자: "Advanced",
  전문가: "Expert",
};

const RISK_PROFILE_EN: Record<RiskProfileLabel, string> = {
  안정형: "Conservative",
  안정추구형: "Moderately Conservative",
  위험중립형: "Balanced",
  적극투자형: "Growth-Oriented",
  공격투자형: "Aggressive",
};

const RISK_PROFILE_DESCRIPTION_EN: Record<RiskProfileLabel, string> = {
  안정형: "Prioritizes principal preservation with a conservative stance",
  안정추구형: "Seeks returns above deposits while keeping loss risk low",
  위험중립형: "Balances risk and return",
  적극투자형: "Accepts volatility for above-market return potential",
  공격투자형: "Actively accepts high volatility for higher return potential",
};

const FEATURE3_PROFILE_TYPE_EN: Record<string, string> = {
  CONSERVATIVE: "Conservative",
  NEUTRAL: "Neutral",
  MODERATE: "Moderate",
  BALANCED: "Balanced",
  AGGRESSIVE: "Aggressive",
  AGGRESIVE: "Aggressive",
};

export const investLevelLabel = (value: string | null | undefined, language?: string | null): string => {
  if (!value) return "-";
  if (!isEnglishLanguage(language)) return value;
  return INVEST_LEVEL_EN[value as InvestLevel] ?? value;
};

export const riskProfileLabel = (value: string | null | undefined, language?: string | null): string => {
  if (!value) return "-";
  if (!isEnglishLanguage(language)) return value;
  return RISK_PROFILE_EN[value as RiskProfileLabel] ?? FEATURE3_PROFILE_TYPE_EN[value] ?? value;
};

export const riskProfileDescription = (value: string | null | undefined, language?: string | null): string | null => {
  if (!value) return null;
  if (!isEnglishLanguage(language)) return null;
  return RISK_PROFILE_DESCRIPTION_EN[value as RiskProfileLabel] ?? null;
};
