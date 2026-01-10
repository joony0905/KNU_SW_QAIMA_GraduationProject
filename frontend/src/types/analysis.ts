// src/types/analysis.ts
export interface AnalysisResult {
  summary: string;
  business: string;
  financial: string;
  valuation: string;
  risk: string;
  outlook: string;
  analysisText: string;
}

export interface AnalysisResponse {
  stock: any; // 일단 any로
  candles: any[];
  indicators: any[];
  financials: any[];
  analysis: AnalysisResult;
}
