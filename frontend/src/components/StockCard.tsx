// frontend/src/components/StockCard.tsx
interface StockCardProps {
  name: string;
  symbol?: string;
  exchange?: string;
  price: string;
  volume: string;
  change: string;
  changeRate: string;
  getColorClass: (rate: string) => string;
  onToggle?: () => void;
  isOpen?: boolean;
}

export default function StockCard({
  name,
  symbol,
  exchange = "KOSPI",
  price,
  volume,
  change,
  changeRate,
  getColorClass,
  isOpen,
}: StockCardProps) {
  const colorClass = getColorClass(changeRate);
  const numericChange = Number(change.replace(/,/g, "").trim());
  const displayRate = numericChange === 0 ? "0.00%" : changeRate;

  return (
    <div className={`flex items-center gap-3 px-3.5 py-[9px] bg-surface border border-line shadow-card w-full ${isOpen ? "rounded-t-2xl border-b-surface" : "rounded-2xl"}`}>
      {/* 1) 종목명 + 코드 */}
      <div className="min-w-0 flex-shrink-0">
        <div className="font-semibold text-ink text-sm leading-tight tracking-tight whitespace-nowrap">
          {name}
        </div>
        {symbol && (
          <div className="text-[10px] text-ink-3 font-mono mt-0.5 whitespace-nowrap">
            {symbol} · {exchange}
          </div>
        )}
      </div>

      {/* 2) 현재가 + 거래량 */}
      <div className="flex-1 flex flex-col items-end min-w-0">
        <div className={`${colorClass} font-bold font-mono tabular text-sm leading-tight whitespace-nowrap`}>
          {price}
        </div>
        <div className="text-ink-3 font-mono tabular text-[10px] mt-0.5 whitespace-nowrap">
          {volume}
        </div>
      </div>

      {/* 3) 방향 삼각형 */}
      <div className="flex-shrink-0">
        {numericChange > 0 && (
          <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-b-[8px] border-transparent border-b-current`} />
        )}
        {numericChange < 0 && (
          <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-t-[8px] border-transparent border-t-current`} />
        )}
        {numericChange === 0 && (
          <span className={`${colorClass} text-base font-extrabold leading-none`}>-</span>
        )}
      </div>

      {/* 4) 변동금액 + 퍼센트 */}
      <div className="flex flex-col items-end flex-shrink-0 min-w-[52px]">
        <div className={`${colorClass} font-bold font-mono tabular text-sm leading-tight whitespace-nowrap`}>
          {change}
        </div>
        <div className={`${colorClass} font-medium font-mono tabular text-[10px] mt-0.5 whitespace-nowrap`}>
          {displayRate}
        </div>
      </div>

    </div>
  );
}
