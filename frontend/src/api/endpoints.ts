export const ENDPOINTS = {
  auth: {
    login: "/auth/login",
    signup: "/auth/signup",
  },
  users: {
    riskProfile: "/users/me/risk-profile",
  },
  stocks: {
    search: (query: string) => `/stocks/search?q=${encodeURIComponent(query)}`,
    getByCode: (stockCode: string) => `/stocks/code/${stockCode}`,
    financials: (ticker: string, years = 5, periodType?: string, periodNo?: number) =>
      `/stocks/${ticker}/financials?years=${years}${periodType ? `&periodType=${periodType}` : ""}${periodNo !== undefined ? `&periodNo=${periodNo}` : ""}`,
    financialsByYear: (ticker: string, year: number, periodType?: string, periodNo?: number) => {
      const base = `/stocks/${ticker}/financials/${year}`;
      const params = new URLSearchParams();
      if (periodType) params.set("periodType", periodType);
      if (periodNo !== undefined) params.set("periodNo", String(periodNo));
      const qs = params.toString();
      return qs ? `${base}?${qs}` : base;
    },
    analysisTicker: (symbol: string) => `/stocks/marketstack/ticker/${symbol}`,
    marketSnapshot: (stockCode: string, asOfDate?: string) => {
      const base = `/stocks/code/${stockCode}/market-snapshot`;
      return asOfDate ? `${base}?asOfDate=${asOfDate}` : base;
    },
  },
  watchlist: {
    getMyItems: "/watchlist/me",
    addMyItem: "/watchlist/me/items",
    deleteItem: (itemId: number) => `/watchlist/items/${itemId}`,
    updateItem: (itemId: number) => `/watchlist/items/${itemId}`,
  },
  featuredStocks: {
    getByTopic: (topic: string, limit = 30) =>
      `/featured-stocks?topic=${topic}&limit=${limit}`,
  },
  analysis: {
    analyze: () => "/feature1/analyze",
  },
  portfolio: {
    analyze: () => "/feature3/analysis",
    overlayCachePreview: () => "/feature3/overlay-cache/preview",
  },
  dictionary: {
    search: (params: { q?: string; initial?: string; page?: number; size?: number }) => {
      const query = new URLSearchParams();
      if (params.q) query.set("q", params.q);
      if (params.initial) query.set("initial", params.initial);
      if (params.page !== undefined) query.set("page", String(params.page));
      if (params.size !== undefined) query.set("size", String(params.size));
      const qs = query.toString();
      return `/dictionary${qs ? "?" + qs : ""}`;
    },
    getOne: (term: string) => `/dictionary/${encodeURIComponent(term)}`,
    autocomplete: (q: string, size?: number) =>
      `/dictionary/autocomplete?q=${encodeURIComponent(q)}${size !== undefined ? "&size=" + size : ""}`,
    initials: () => "/dictionary/initials",
  },
  news: {
    listByStock: (stockCode: string, limit = 15) =>
      `/feature2/news?stockCode=${stockCode}&limit=${limit}`,
    detail: (newsId: number) => `/feature2/news/${newsId}`,
  },
  charts: {
    candles: (stockCode: string, freq: string, from: string, to: string) =>
      `/charts/candles?stockCode=${stockCode}&freq=${freq}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,

    candlesBefore: (stockCode: string, freq: string, to: string, limit: number) =>
      `/charts/candles?stockCode=${stockCode}&freq=${encodeURIComponent(freq)}&to=${encodeURIComponent(to)}&limit=${encodeURIComponent(String(limit))}`,
  },
};
