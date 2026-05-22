import api from "./apiClient";
import type {
  BaseRateSeriesPoint,
  BaseRateMetrics,
  Feature2AnalyzeResponse,
  Feature2InvestorFlow,
  Feature2MacroRates,
  Feature2MacroRatesSeries,
  IndustryIndexBlock,
  RelatedStockCard,
  ShortSellingSeriesPoint,
  ShortSellingMetrics,
} from "../types/feature2";
import type { ApiResponse } from "../types/common/api";
import type { NewsItemDto } from "../types/news";
import type { InvestLevel } from "../utils/investLevel";

const publicCardConfig = (params?: Record<string, unknown>) => ({
  params,
  _skipAuthRedirect: true,
});

export const fetchFeature2Analysis = async (
  stockCode: string,
  freq?: string,
  window?: number,
  peerCount?: number,
  displayLimit?: number,
  llmVendor?: string,
  from?: string,
  to?: string,
  investLevel?: InvestLevel,
): Promise<ApiResponse<Feature2AnalyzeResponse>> => {
  const body: Record<string, unknown> = { stockCode };
  if (freq) body.freq = freq;
  if (window) body.window = window;
  if (from) body.from = from;
  if (to) body.to = to;
  if (peerCount) body.peerCount = peerCount;
  if (displayLimit) body.displayLimit = displayLimit;
  if (llmVendor) body.llmVendor = llmVendor;
  if (investLevel) body.investLevel = investLevel;
  const res = await api.post<ApiResponse<Feature2AnalyzeResponse>>(
    "/feature2/analyze",
    body,
  );
  return res.data;
};

export const fetchFeature2NewsList = async (
  stockCode: string,
): Promise<ApiResponse<NewsItemDto[]>> => {
  const res = await api.get<ApiResponse<NewsItemDto[]>>(
    "/feature2/news",
    publicCardConfig({ stockCode }),
  );
  return res.data;
};

export const fetchFeature2BaseRate = async (): Promise<ApiResponse<BaseRateMetrics | null>> => {
  const res = await api.get<ApiResponse<BaseRateMetrics | null>>(
    "/feature2/cards/base-rate",
    publicCardConfig(),
  );
  return res.data;
};

export const fetchFeature2BaseRateSeries = async (
  limit = 365,
): Promise<ApiResponse<BaseRateSeriesPoint[]>> => {
  const res = await api.get<ApiResponse<BaseRateSeriesPoint[]>>(
    "/feature2/cards/base-rate-series",
    publicCardConfig({ limit }),
  );
  return res.data;
};

export const fetchFeature2MacroRates = async (): Promise<ApiResponse<Feature2MacroRates | null>> => {
  const res = await api.get<ApiResponse<Feature2MacroRates | null>>(
    "/feature2/cards/macro-rates",
    publicCardConfig(),
  );
  return res.data;
};

export const fetchFeature2MacroRatesSeries = async (
  limit = 120,
): Promise<ApiResponse<Feature2MacroRatesSeries | null>> => {
  const res = await api.get<ApiResponse<Feature2MacroRatesSeries | null>>(
    "/feature2/cards/macro-rates-series",
    publicCardConfig({ limit }),
  );
  return res.data;
};

export const fetchFeature2IndustryIndex = async (
  stockCode: string,
  freq = "ONE_D",
  window = 120,
): Promise<ApiResponse<IndustryIndexBlock | null>> => {
  const res = await api.get<ApiResponse<IndustryIndexBlock | null>>(
    "/feature2/cards/industry-index",
    publicCardConfig({ stockCode, freq, window }),
  );
  return res.data;
};

export const fetchFeature2ShortSelling = async (
  stockCode: string,
): Promise<ApiResponse<ShortSellingMetrics | null>> => {
  const res = await api.get<ApiResponse<ShortSellingMetrics | null>>(
    "/feature2/cards/short-selling",
    publicCardConfig({ stockCode }),
  );
  return res.data;
};

export const fetchFeature2ShortSellingSeries = async (
  stockCode: string,
  limit = 60,
): Promise<ApiResponse<ShortSellingSeriesPoint[]>> => {
  const res = await api.get<ApiResponse<ShortSellingSeriesPoint[]>>(
    "/feature2/cards/short-selling-series",
    publicCardConfig({ stockCode, limit }),
  );
  return res.data;
};

export const fetchFeature2RelatedStocks = async (
  stockCode: string,
  limit = 30,
): Promise<ApiResponse<RelatedStockCard[]>> => {
  const res = await api.get<ApiResponse<RelatedStockCard[]>>(
    "/feature2/cards/related-stocks",
    publicCardConfig({ stockCode, limit }),
  );
  return res.data;
};

export const fetchFeature2InvestorFlow = async (
  stockCode: string,
  limit = 60,
): Promise<ApiResponse<Feature2InvestorFlow | null>> => {
  const res = await api.get<ApiResponse<Feature2InvestorFlow | null>>(
    "/feature2/cards/investor-flow",
    publicCardConfig({ stockCode, limit }),
  );
  return res.data;
};
