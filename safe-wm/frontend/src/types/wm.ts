export type RiskGrade = "HIGH" | "MID" | "LOW";
export type Priority = "URGENT" | "WATCH" | "NORMAL";

export interface DashboardSummary {
  totalCustomers: number;
  averageAssetStabilityScore: number;
  riskGradeCounts: Record<RiskGrade, number>;
  priorityCounts: Record<Priority, number>;
  highRiskCustomerIds: string[];
  urgentCustomerIds: string[];
}

export interface CustomerSummary {
  customerId: string;
  name: string;
  age: number;
  region: string;
  retirementStatus: string;
  summary: {
    totalAssets: number;
    liquidAssets: number;
    realEstateRatio: number;
    cashCoverageMonths: number;
    monthlySurplusAfterDebt: number;
  };
  lastAnalysis: {
    assetStabilityScore: number;
    riskGrade: RiskGrade;
    priority: Priority;
    mainRiskFactors: string[];
    mainRiskTitles: string[];
  };
  riskAlert: {
    level: RiskGrade;
    title: string;
    message: string;
    triggeredBy: string[];
  };
}

export interface CustomerAlert {
  customerId: string;
  name: string;
  riskGrade: RiskGrade;
  priority: Priority;
  level: RiskGrade;
  title: string;
  message: string;
  triggeredBy: string[];
  mainRiskTitles: string[];
}

export interface AnalysisResponse {
  customerId: string;
  analysisId: string;
  traceId: string;
  assetStabilityScore: number;
  riskGrade: RiskGrade;
  priority: Priority;
  riskAlert: {
    level: RiskGrade;
    title: string;
    message: string;
    triggeredBy: string[];
  };
  scoreBreakdown: Array<{
    category: string;
    severity: string;
    baseScore: number;
    penalty: number;
    resultScore: number;
    reason: string;
  }>;
  riskFactors: Array<{
    code: string;
    severity: string;
    title: string;
    description: string;
  }>;
  customerExplanation: { text: string };
  pbExplanation: { text: string };
  pbActions: Array<{ priority: Priority; title: string; description: string }>;
  compliance: { status: string; regenerated: boolean; violations?: string[] };
  agentTrace: Array<{ agent: string; status: string; summary: string }>;
  debug?: { traceId: string; steps: Array<{ step: string; status: string; message: string }> };
}
