export interface IndicatorSpec {
  ema?: {
    period?: number;
  };
  bollinger?: {
    period?: number;
    stdDev?: number;
  };
  stochastic?: {
    kPeriod?: number;
    dPeriod?: number;
    smooth?: number;
  };
}

export interface IndicatorPoint1 {
  t: string;               // ISO8601
  value: number | null;
}

export interface BollingerPoint {
  t: string;               // ISO8601
  upper: number;
  mid: number;
  lower: number;
}

export interface StochPoint {
  t: string;               // ISO8601
  k: number;
  d: number;
}

export interface IndicatorBundle {
  // spec는 선택
  spec?: IndicatorSpec | null;

  // EMA는 Dict. fallback 시 {} (null 금지)
  ema: Record<string, IndicatorPoint1[]>;

  // BB/Stoch는 고정 파라미터. 미구현/스킵이면 null 허용
  bb20_2: BollingerPoint[] | null;
  stoch14_3_3: StochPoint[] | null;

  // warnings는 항상 배열
  warnings: string[];
}