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
    getAnalysis: (stockCode: string, freq: string, from: string, to: string) =>
      `/feature1?stockCode=${stockCode}&freq=${freq}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  },
};
