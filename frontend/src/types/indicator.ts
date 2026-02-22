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
  t: string;
  value: number | null;
}

export interface BollingerPoint {
  t: string;
  mid: number | null;
  upper: number | null;
  lower: number | null;
}

export interface StochPoint {
  t: string;
  k: number | null;
  d: number | null;
}

export interface IndicatorBundle {
  spec?: IndicatorSpec;
  ema20?: IndicatorPoint1[];
  bb20_2?: BollingerPoint[];
  stoch14_3_3?: StochPoint[];
  warnings?: string[];
}
