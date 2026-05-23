import api from "./apiClient";
import type { ApiResponse } from "../types/common/api";
import type { AnalysisReportDetail, AnalysisReportSummary, ReportFeatureType } from "../types/report";

export const fetchMyReports = async (
  featureType?: ReportFeatureType | "ALL",
): Promise<AnalysisReportSummary[]> => {
  const res = await api.get<ApiResponse<AnalysisReportSummary[]>>("/reports/me", {
    params: featureType && featureType !== "ALL" ? { featureType } : undefined,
  });
  return res.data.data ?? [];
};

export const fetchReportDetail = async (reportId: number): Promise<AnalysisReportDetail> => {
  const res = await api.get<ApiResponse<AnalysisReportDetail>>(`/reports/${reportId}`);
  return res.data.data;
};
