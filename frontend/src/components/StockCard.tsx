interface StockCardProps {
  name: string;
  price: string;
  volume: string;
  change: string;
  changeRate: string;
  getColorClass: (rate: string) => string;
}

export default function StockCard({
  name,
  price,
  volume,
  change,
  changeRate,
  getColorClass,
}: StockCardProps) {
  const colorClass = getColorClass(changeRate);

  const numericChange = Number(
    change.replace(/,/g, "").trim()
  ); // "6,000" -> 6000, "-2,000" -> -2000

  // 0일 때 퍼센트 표시용 문자열
  const displayRate =
    numericChange === 0 ? "0.00%" : changeRate;

  return (
    <div
      className="
        flex items-center
        min-w-[260px] sm:min-w-[280px]
        px-2 py-1
        outline outline-stone-300 bg-white
        rounded-sm
        lg:w-[380px] lg:px-2 lg:py-1 lg:outline-[1px] lg:rounded-sm
      "
    >
      {/* 1열: 종목명 */}
      <div className="min-w-0 text-left 
                      flex-shrink 
                      text-[clamp(10px,1.5vw,14px)] 
                      lg:w-[140px] lg:text-base">
        <div className="font-semibold text-black leading-tight whitespace-nowrap overflow-hidden">
          {name}
        </div>
      </div>


      {/* 2열: 현재가/거래량 + 방향 표시 */}
      <div className="flex-1 flex items-center justify-end gap-1.5 lg:gap-3 min-w-0">
        <div className="flex flex-col items-end min-w-0">
          <div className={`${colorClass} font-semibold 
                          text-[clamp(10px,1.5vw,14px)] 
                          lg:text-base leading-tight whitespace-nowrap overflow-hidden`}>
            {price}
          </div>
          <div className="text-black font-medium 
                          text-[clamp(9px,1.2vw,13px)] 
                          lg:text-sm leading-tight whitespace-nowrap overflow-hidden">
            {volume}
          </div>
        </div>


        {/* 방향 표시: 상승/하락/보합 */}
        {numericChange > 0 && (
          // ↑ 삼각형 (사이즈 한 단계 키움)
          <div
            className={`${colorClass} w-0 h-0 
                        border-l-[6px] border-r-[6px] 
                        border-b-[9px] border-transparent 
                        border-b-current`}
          />
        )}

        {numericChange < 0 && (
          // ↓ 역삼각형 (사이즈 한 단계 키움)
          <div
            className={`${colorClass} w-0 h-0 
                        border-l-[6px] border-r-[6px] 
                        border-t-[9px] border-transparent 
                        border-t-current`}
          />
        )}

        {numericChange === 0 && (
          // - (보합, 매우 굵게 + 한 단계 크게)
          <span
            className={`
              ${colorClass}
              text-xl sm:text-2xl
              font-extrabold
              leading-none
            `}
          >
            -
          </span>
        )}
      </div>

      {/* 3열: 상승 금액 + 퍼센트 */}
      <div className="flex flex-col items-end gap-0.5 flex-shrink-0 min-w-0 
                      text-[clamp(10px,1.5vw,14px)] lg:w-[90px] lg:text-base">
        <div className={`${colorClass} font-semibold leading-tight whitespace-nowrap overflow-hidden`}>
          {change}
        </div>
        <div className={`${colorClass} font-medium 
                        text-[clamp(9px,1.2vw,13px)] 
                        lg:text-sm leading-tight whitespace-nowrap overflow-hidden`}>
          {displayRate}
        </div>
      </div>

    </div>
  );
}
