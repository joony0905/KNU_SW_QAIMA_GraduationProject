export default function Feature2MockPage() {
  return (
    <div className="relative bg-white w-full">
      <div className="flex gap-0 max-md:flex-col">
        <div className="relative bg-gray-100 rounded-lg border border-gray-400 border-solid h-[800px] w-[334px] max-md:w-full max-md:h-auto max-md:mb-5">
          <div className="flex absolute top-0 left-0 justify-between items-center px-6 py-0 h-11 bg-gray-200 rounded-lg border border-gray-400 border-solid w-[334px] max-md:w-full">
            <div className="text-xs text-black">선택 종목 차트</div>
            <div className="text-xs text-black">티커 · 현재가 · 등락률</div>
          </div>
          <div className="absolute left-4 text-xs text-center text-black top-[60px] w-[258px]">
            캔들 + 볼린저밴드 + RSI + 거래량 영역 (차트 자리)
          </div>
          <img
            src="https://api.builder.io/api/v1/image/assets/TEMP/e9171333739f89d0b5181115863cfb9120813ad5?width=616"
            alt="Chart 1"
            className="absolute left-3 h-[140px] top-[191px] w-[308px] max-sm:h-auto max-sm:left-[5%] max-sm:w-[90%]"
          />
          <div className="absolute w-64 text-xs leading-4 text-center text-cyan-400 left-[38px] top-[378px]">
            종목 차트 stock, price_ohlcv, indicator_value, stock_realtime_cache
            <br />
            <br />
            유저 접근 / 내정보 (투자성향) → users, user_invest_profile, login_session
          </div>
        </div>

        <div className="relative w-[576px] max-md:w-full">
          <div className="relative bg-gray-200 rounded-lg border border-gray-400 border-solid h-[416px] w-[576px] max-md:w-full">
            <div className="flex absolute top-0 left-0 justify-between items-center px-6 py-0 h-11 rounded-lg border border-gray-400 border-solid bg-zinc-200 w-[576px] max-md:w-full">
              <div className="text-xs text-black">산업 지수 (예: 반도체 지수)</div>
              <div className="text-xs text-black">상대강도 · 변동성</div>
            </div>
            <div className="absolute left-5 text-xs text-center text-black top-[75px] w-[239px]">
              산업지수 라인차트 / 이동평균 / 거래량 히���토리
            </div>
            <img
              src="https://api.builder.io/api/v1/image/assets/TEMP/ea40e5f327633475d7d4b08ebdaab8895d44ada1?width=1090"
              alt="Chart 2"
              className="absolute h-[242px] left-[15px] top-[147px] w-[545px] max-sm:h-auto max-sm:left-[5%] max-sm:w-[90%]"
            />
            <div className="absolute left-72 text-xs leading-6 text-center text-black top-[69px] w-[204px]">
              산업지수 차트 industry_index, industry_index_ohlcv, indicator_value
            </div>
          </div>

          <div className="absolute left-0 top-[416px] w-[576px] max-md:relative max-md:w-full max-md:top-0 max-md:mt-5">
            <div className="flex justify-between items-center px-6 py-0 h-10 bg-gray-50 rounded-lg border border-gray-300 border-solid w-[576px] max-md:w-full">
              <div className="text-xs text-black">산업 내 유사 종목 (클러스터링 결과)</div>
              <div className="text-xs text-black">티커 | 현재가 | 등락률</div>
            </div>
            <div className="relative mt-10 bg-white rounded-lg border border-gray-300 border-solid h-[344px] w-[576px] max-md:w-full max-md:h-auto max-md:min-h-[344px]">
              <img
                src="https://api.builder.io/api/v1/image/assets/TEMP/a25c6e347eca133ac1bee6a52efb5a6074463f91?width=442"
                alt="Cluster visualization"
                className="absolute h-[236px] left-[29px] top-[49px] w-[221px] max-sm:h-auto max-sm:left-[5%] max-sm:w-[40%]"
              />
              <div className="absolute text-xs leading-4 text-center text-black right-[135px] top-[115px] w-[169px]">
                TSM 184.20 +2.4%
                <br />
                NVDA 136.55 +1.1%
                <br />
                AMD 78.33 -0.8%
                <br />
                (예시 데이터 자리)
              </div>
              <div className="absolute text-xs leading-4 text-center text-black left-[259px] top-[113px] w-[135px]">
                티커 클릭시
                <br />
                해당 티커로이동
              </div>
              <div className="absolute text-xs leading-5 text-center text-cyan-400 left-[301px] top-[220px] w-[157px]">
                산업 내 유사 종목 peer_cluster_cache join stock, stock_realtime_cache
              </div>
            </div>
          </div>
        </div>

        <div className="relative rounded-lg border border-gray-400 border-solid bg-zinc-200 h-[800px] w-[432px] max-md:w-full max-md:mt-5 max-md:h-auto max-md:min-h-[800px]">
          <div className="flex absolute top-0 left-0 justify-between items-center px-6 py-0 h-11 bg-gray-300 rounded-lg border border-gray-400 border-solid w-[432px] max-md:w-full">
            <div className="text-xs text-black">관련 뉴스 &amp; 감성 점수</div>
            <div className="text-xs text-black">긍정 / 중립 / 부정</div>
          </div>
          <div className="flex absolute left-4 top-[60px] flex-col gap-2.5 justify-center items-center bg-white rounded-lg border border-gray-300 border-solid h-[110px] w-[400px] max-sm:left-[5%] max-sm:w-[90%]">
            <div className="text-xs text-center text-black">반도체 수요 회복 기대</div>
            <div className="text-xs text-center text-black">Sentiment: +0.82 (긍정)</div>
          </div>
          <div className="flex absolute left-4 top-[190px] flex-col gap-2.5 justify-center items-center bg-white rounded-lg border border-gray-300 border-solid h-[110px] w-[400px] max-sm:left-[5%] max-sm:w-[90%]">
            <div className="text-xs text-center text-black">원가 부담 확대 우려</div>
            <div className="text-xs text-center text-black">Sentiment: -0.41 (부정)</div>
          </div>
          <img
            src="https://api.builder.io/api/v1/image/assets/TEMP/8f1c2b1706dcc2e98bad373190c61f10a03ddfb3?width=798"
            alt="News image"
            className="absolute h-[292px] left-[11px] top-[318px] w-[399px] max-sm:h-auto max-sm:left-[5%] max-sm:w-[90%]"
          />
          <div className="absolute text-xs leading-5 text-center text-cyan-400 left-[71px] top-[650px] w-[265px]">
            뉴스 &amp; 감성 점수 → news, news_security_map, sentiment_result, sentiment_daily_agg
          </div>
        </div>
      </div>

      <div className="relative h-56 bg-yellow-100 rounded-lg border border-yellow-300 border-solid mt-5 w-full max-md:mt-5">
        <div className="flex absolute top-0 left-0 justify-between items-center px-8 py-0 h-11 bg-amber-300 rounded-lg border border-yellow-300 border-solid w-full max-sm:px-2.5 max-sm:py-0">
          <div className="text-xs text-black max-sm:text-xs">🤖 QAIMA 산업 분석 요약</div>
          <div className="text-xs text-black max-sm:text-xs">현재 평가: ⚠ 중립</div>
        </div>
        <div className="flex absolute flex-col justify-center text-xs leading-5 text-center text-black left-[33px] top-[100px] w-[358px]">
          업황: 메모리 반도체 가격 반등 조짐.
          <br />
          리스크: 전력/원가 압박 지속.
          <br />
          시사점: 단기 모멘텀은 있으나 중장기 확신은 재무 안정성 확인 필요.
        </div>
        <div className="flex absolute flex-col justify-center text-xs leading-5 text-center text-cyan-400 left-[437px] top-[72px] w-[172px] max-sm:left-[5%] max-sm:w-[90%]">
          산업 분석 요약 analysis_request.response_text (source='feature2_external')
          <br />
          사전 용어 팝업 dictionary, (dictionary_match로 조회 이벤트 로깅)
        </div>
      </div>
    </div>
  );
}
