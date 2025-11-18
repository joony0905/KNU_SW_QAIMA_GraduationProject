import React, { useEffect, useRef, memo } from "react";

function TradingViewWidget() {
  const container = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!container.current) return;

    // 중복 삽입 방지
    container.current.innerHTML = "";

    const script = document.createElement("script");
    script.src =
      "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js";
    script.type = "text/javascript";
    script.async = true;
    script.innerHTML = `
      {
        "allow_symbol_change": true,
        "calendar": true,
        "details": true,
        "hide_side_toolbar": true,
        "hide_top_toolbar": false,
        "hide_legend": false,
        "hide_volume": false,
        "hotlist": false,
        "interval": "D",
        "locale": "kr",
        "save_image": true,
        "style": "0",
        "symbol": "NASDAQ:AAPL",
        "theme": "white",
        "timezone": "Etc/UTC",
        "backgroundColor": "rgba(255, 255, 255, 1)",
        "gridColor": "rgba(242, 242, 242, 0.06)",
        "watchlist": [],
        "withdateranges": false,
        "compareSymbols": [],
        "studies": [
          "STD;Stochastic_RSI",
          "STD;Bollinger_Bands"
        ],
        "autosize": true
      }`;

    container.current.appendChild(script);
  }, []);

  return (
    <div
      ref={container}
      className="tradingview-widget-container"
      style={{ width: "100%", height: "100%" }}
    >
      <div
        className="tradingview-widget-container__widget"
        style={{ width: "100%", height: "100%" }}
      />
    </div>
  );
}

export default memo(TradingViewWidget);
