export type RiskProfileLabel = "안정형" | "안정추구형" | "위험중립형" | "적극투자형" | "공격투자형";

export type RiskProfileOption = {
  label: RiskProfileLabel;
  gamma: number;
  description: string;
};

export const FEATURE3_RISK_GAMMA_STORAGE_KEY = "qaima_feature3_risk_gamma";
export const FEATURE3_CASH_LIMIT_STORAGE_KEY = "qaima_feature3_cash_limit";
export const FEATURE3_CASH_LIMIT_MANUAL_STORAGE_KEY = "qaima_feature3_cash_limit_manual";
export const SURVEY_RESULT_STORAGE_KEY = "qaima_survey_result";

export const RISK_PROFILE_OPTIONS: readonly RiskProfileOption[] = [
  { label: "안정형", gamma: 0.2, description: "원금 보전을 우선하는 보수적 성향" },
  { label: "안정추구형", gamma: 0.4, description: "손실 위험을 낮추면서 예금 이상의 수익 추구" },
  { label: "위험중립형", gamma: 0.6, description: "위험과 수익의 균형 추구" },
  { label: "적극투자형", gamma: 0.8, description: "시장 평균 이상의 수익을 위해 변동성 감수" },
  { label: "공격투자형", gamma: 1.0, description: "고수익을 위해 높은 변동성 적극 감수" },
] as const;

export const clampRiskGamma = (v: number): number => {
  if (!Number.isFinite(v)) return 0;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return Math.round(v * 100) / 100;
};

export const formatRiskGamma = (v: number): string => clampRiskGamma(v).toFixed(2);

export const clampCashLimit = (v: number): number => {
  if (!Number.isFinite(v)) return 0.2;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return Math.round(v * 100) / 100;
};

export const autoCashLimitForRiskScore = (riskScore: number): number => {
  return clampCashLimit(1 - clampRiskGamma(riskScore));
};

export const riskProfileLabelForGamma = (gamma: number | null | undefined): RiskProfileLabel | null => {
  if (gamma === null || gamma === undefined || !Number.isFinite(gamma)) return null;
  const value = clampRiskGamma(gamma);
  if (value <= 0.2) return "안정형";
  if (value <= 0.4) return "안정추구형";
  if (value <= 0.6) return "위험중립형";
  if (value <= 0.8) return "적극투자형";
  return "공격투자형";
};

export const riskProfileOptionForGamma = (gamma: number | null | undefined): RiskProfileOption | null => {
  const label = riskProfileLabelForGamma(gamma);
  return RISK_PROFILE_OPTIONS.find((option) => option.label === label) ?? null;
};

export const readStoredFeature3RiskGamma = (): number | null => {
  const saved = localStorage.getItem(FEATURE3_RISK_GAMMA_STORAGE_KEY);
  if (saved === null) return null;
  const parsed = Number(saved);
  return Number.isFinite(parsed) ? clampRiskGamma(parsed) : null;
};

export const readStoredFeature3CashLimit = (): number | null => {
  const saved = localStorage.getItem(FEATURE3_CASH_LIMIT_STORAGE_KEY);
  if (saved === null) return null;
  const parsed = Number(saved);
  return Number.isFinite(parsed) ? clampCashLimit(parsed) : null;
};

export const readStoredFeature3CashLimitManual = (): boolean => {
  return localStorage.getItem(FEATURE3_CASH_LIMIT_MANUAL_STORAGE_KEY) === "true";
};

export const syncFeature3RiskDefaults = (gamma: number): void => {
  const clamped = clampRiskGamma(gamma);
  localStorage.setItem(FEATURE3_RISK_GAMMA_STORAGE_KEY, String(clamped));
  localStorage.setItem(FEATURE3_CASH_LIMIT_STORAGE_KEY, String(autoCashLimitForRiskScore(clamped)));
  localStorage.setItem(FEATURE3_CASH_LIMIT_MANUAL_STORAGE_KEY, "false");
};

export const storeFeature3RiskGamma = (gamma: number): void => {
  localStorage.setItem(FEATURE3_RISK_GAMMA_STORAGE_KEY, String(clampRiskGamma(gamma)));
};

export const storeFeature3CashLimit = (cashLimit: number, manual: boolean): void => {
  localStorage.setItem(FEATURE3_CASH_LIMIT_STORAGE_KEY, String(clampCashLimit(cashLimit)));
  localStorage.setItem(FEATURE3_CASH_LIMIT_MANUAL_STORAGE_KEY, manual ? "true" : "false");
};
