export const ENDPOINTS = {
  auth: {
    login: "/auth/login",
    signup: "/auth/signup",
  },
  stocks: {
    search: (query: string) => `/stocks/search?q=${encodeURIComponent(query)}`,
    getByCode: (stockCode: string) => `/stocks/code/${stockCode}`,
    financials: (ticker: string, years = 5) =>
      `/stocks/${ticker}/financials?years=${years}`,
    financialsByYear: (ticker: string, year: number) =>
      `/stocks/${ticker}/financials/${year}`,
    analysisTicker: (symbol: string) => `/stocks/marketstack/ticker/${symbol}`,
  },
  watchlist: {
    getItems: (watchlistId: number) => `/watchlist/${watchlistId}`,
    addItem: "/watchlist/items",
    deleteItem: (itemId: number) => `/watchlist/items/${itemId}`,
    updateItem: (itemId: number) => `/watchlist/items/${itemId}`,
  },
  analysis: {
    analyze: () => "/feature1/analyze",
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
  charts: {
    candles: (stockCode: string, freq: string, from: string, to: string) =>
      `/charts/candles?stockCode=${stockCode}&freq=${freq}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,

    candlesBefore: (stockCode: string, freq: string, to: string, limit: number) =>
      `/charts/candles?stockCode=${stockCode}&freq=${encodeURIComponent(freq)}&to=${encodeURIComponent(to)}&limit=${encodeURIComponent(String(limit))}`,
  },
};
