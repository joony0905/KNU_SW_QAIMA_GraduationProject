export type ReportFeatureType = "FEATURE1" | "FEATURE2" | "FEATURE3";
export type ReportSubjectType = "STOCK" | "PORTFOLIO";

export interface AnalysisReportSummary {
  reportId: number;
  featureType: ReportFeatureType;
  subjectType: ReportSubjectType;
  title: string;
  userName: string;
  stockCode?: string | null;
  companyName?: string | null;
  portfolioSummary?: string | null;
  analysisModel?: string | null;
  investLevel?: string | null;
  riskProfile?: string | null;
  analysisWindow?: string | null;
  priceBasis?: string | null;
  generatedAt: string;
  dataAsOf?: string | null;
}

export interface AnalysisReportDetail extends AnalysisReportSummary {
  requestPayload: unknown;
  resultSnapshot: unknown;
  warnings?: unknown;
}
