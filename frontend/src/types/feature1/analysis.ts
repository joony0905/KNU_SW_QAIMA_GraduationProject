import type { AnalysisBase } from '../common/analysis';
import type { IndicatorBundle } from '../indicator';

export interface Feat1Metrics {
  indicators?: IndicatorBundle;
  [key: string]: unknown;
}

export type Feat1AnalysisResult = AnalysisBase<Feat1Metrics>;
