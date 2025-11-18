export default function StocksMockPage() {
  return (
    <div className="relative bg-white w-full flex gap-5 max-md:flex-col">
      <div className="flex-1 bg-gray-100 rounded-lg border border-gray-400 border-solid p-4 max-md:mb-5">
        <div className="bg-gray-200 rounded-lg border border-gray-400 border-solid h-12 mb-4 flex items-center px-4">
          <div className="text-xs text-center text-black mr-auto ml-12 max-md:ml-4">
            종목 기본 정보
          </div>
          <div className="text-xs text-center text-black">
            티커 | 현재가 | 등락률 | 시가총액
          </div>
        </div>

        <div className="bg-white rounded-md border border-gray-300 border-solid h-[100px] mb-4 relative">
          <div className="absolute text-xs text-cyan-400 left-4 top-2">
            stock + stock_realtime_cache + financials (파생) 조인
          </div>
          <div className="absolute text-xs text-center text-black left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[220px]">
            ex: 삼성전자 (005930)
            <br />
            현재가 78,300 (+1.2%) · 시가총액 468조
            <br />
            52주 고가 / 저가, PER, PBR 등
          </div>
        </div>

        <div className="bg-white rounded-md border border-gray-300 border-solid min-h-[766px] max-md:min-h-[400px] relative p-4">
          <div className="absolute text-xs text-cyan-400 left-4 top-4">
            캔들차트=price_ohlcv / RSI·볼린저밴드=indicator_value 캐시
          </div>
          <div className="text-xs text-center text-black mt-8 mb-8">
            캔들차트 + 볼린저밴드 + RSI + 거래량
          </div>
          <img
            src="https://api.builder.io/api/v1/image/assets/TEMP/9ef9d853f827953ac9586639bcb6a1a0eeaebccf?width=1092"
            alt=""
            className="w-full h-auto mx-auto max-w-[546px]"
          />
          <div className="text-xs text-black mt-8 leading-relaxed">
            종목 기본 정보 → stock, exchange, stock_realtime_cache, financials
            <br />
            차트(캔들·거래량·볼밴·RSI) → 시세: price_ohlcv → 지표: indicator_value
            (캐시된 기술적 지표)
            <br />
            재무/밸류 박스 &amp; 지표카드 → 회계 수치: financials →
            (부채비율/유동비율/ROE 등 최근 분기값)
            <br />
            리스크 라벨 / 점수화 블록 → analysis_request.response_text��서 추출
            &amp; 캐시
            <br />
            상세 분석 설명(투자 아이디어, 모니터링 포인트 등) →
            analysis_request.response_text (GPT 결과를 그대로 저장/재사용)
            <br />
            뉴스/키워드 근거 → news, news_security_map, sentiment_result,
            sentiment_daily_s2c
            <br />내 포트폴리오 / 내정보 → users, user_invest_profile, portfolios,
            portfolio_holding
            <br />
            사전 용어 팝업 → dictionary, (dictionary_match_log로 조회 이벤트 로깅)
          </div>
        </div>
      </div>

      <div className="flex-1 bg-yellow-100 rounded-lg border border-yellow-300 border-solid p-4 max-md:w-full">
        <div className="bg-amber-300 rounded-lg border border-yellow-300 border-solid h-12 mb-4 flex items-center px-4">
          <div className="text-xs text-center text-black flex-1 ml-8">
            🤖 QAIMA 종목 분석 리포트
          </div>
          <div className="text-xs text-center text-black">
            리스크: ⚠ 중간
          </div>
        </div>

        <div className="bg-white rounded-md border border-yellow-300 border-solid p-6 mb-4 max-md:h-auto">
          <div className="flex flex-wrap gap-4 mb-4">
            <img
              src="https://api.builder.io/api/v1/image/assets/TEMP/f507419ad0a1d5b2d7c993e09ff091fb0791ceaf?width=638"
              alt=""
              className="flex-1 min-w-[280px] h-auto max-sm:w-full"
            />
            <div className="flex-1 min-w-[280px]">
              <div className="text-xs text-cyan-400 mb-2">
                financial
              </div>
              <div className="text-xs text-center text-black mt-24">
                제무제표
              </div>
            </div>
          </div>
          <img
            src="https://api.builder.io/api/v1/image/assets/TEMP/ffcb6ee3fbfd6fecda7bdb4e03846166766cd31c?width=640"
            alt=""
            className="w-80 h-auto max-sm:w-full"
          />
        </div>

        <div className="bg-white rounded-md border border-yellow-300 border-solid p-6 mb-4 max-md:h-auto relative">
          <div className="absolute text-xs text-center text-cyan-400 left-6 top-24">
            analysis_request.response_text
          </div>
          <div className="text-xs text-center text-black leading-relaxed max-sm:text-left">
            상세 분석 예시 (TESLA, NASDAQ: TSLA)
            <br />
            <br />
            📈 투자 아이디어:
            <br />
            테슬라는 전기차 산업의 시장 리더로, 2025년에도 글로벌 전동�� 추세의
            핵심 수혜주로 평가된다.
            <br />
            최근 Cybertruck 양산 확대 및 차세대 4680 배터리 생산 효율 개선
            소식이 주가 상승 모멘텀을 제공하고 있다.
            <br />
            또한, 자율주행 소프트웨어(FSD) 구독 모델이 본격적으로 매출에 반영될
            경우,
            <br />
            기존 자동차 제조업을 넘어선 **SaaS형 수익 구조 전환**이 가속화될
            전망이다.
            <br />
            <br />
            <br />
            📊 모니터링 포인트:
            <br />- 마진 회복 속도 (2025E 영업이익률 11% 이상 회복 여부)
            <br />- FSD 소프트웨어 수익 비중의 점진적 증가
            <br />- 전력 비용 안정 및 Gigafactory 생산량 추이
            <br />
            <br />
            📉 기술적 관점:
            <br />
            현재 주가는 20일 이동평균선을 상향 돌파하며 단기 반등세를 시도
            중이나,
            <br />
            240달러 부근의 강한 저항선이 존재한다.
            <br />
            RSI는 64 수준으로 **단기 과매수권에 근접**,
            <br />
            거래량은 이전 상승 파동 대비 약 85% 수준으로 매수세의 지속성 검증이
            필요하다.
            <br />
            <br />
            🧭 종합 의견:
            <br />
            단기적으로는 변동성이 큰 구간에서 차익 실현 매물이 나올 가능성이
            있으나,
            <br />
            중장기 관점에서 FSD·에너지·AI 생태계 확장성은 여전히 유효하다.
            <br />
            따라서 보수적 투자자는 220달러 이하 구간에서 분할 매수 접근이
            바람직하며,
            <br />
            고위험 투자자는 AI 테마 연동 시 단기 트레이딩 기회도 기대할 수 있다.
          </div>
        </div>

        <button className="w-40 h-10 bg-blue-500 rounded-md border border-sky-600 border-solid text-xs text-center text-black">
          분석
        </button>
      </div>
    </div>
  );
}
