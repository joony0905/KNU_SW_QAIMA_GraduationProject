export default function StocksMockPage() {
  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="flex flex-col items-center gap-5 p-5 w-full max-w-[1754px] mx-auto">

        <div className="self-stretch px-5 py-6 bg-white border-b border-neutral-900 inline-flex justify-start items-center gap-2.5 overflow-hidden">
            <div className="justify-start text-black text-4xl font-medium font-['Inter']">심층분석</div>
        </div>
        
        {/* 종목 기본 정보 박스 */}
        <div className="self-stretch h-24 inline-flex justify-center items-center gap-44">
            <div className="w-[702px] h-20 px-5 py-3.5 bg-white rounded-[10px] outline outline-1 outline-offset-[-1px] outline-stone-300 inline-flex flex-col justify-start items-start">
                <div className="inline-flex justify-start items-start gap-5">
                    <div className="w-32 inline-flex flex-col justify-start items-start">
                        <div className="self-stretch h-11 relative bg-white outline outline-1 outline-offset-[-1px] outline-black overflow-hidden">
                            <div className="left-[36px] top-[7px] absolute text-center justify-start text-black text-2xl font-medium font-['Inter']">국내</div>
                            <div className="w-6 h-5 left-[108.19px] top-[34px] absolute origin-top-left rotate-180 bg-neutral-700 rounded-sm" />
                        </div>
                    </div>
                    <div className="w-[521px] flex justify-between items-center">
                        <div className="text-center justify-center text-zinc-500 text-2xl font-medium font-['Inter']">종목을 입력해주세요</div>
                        <img className="w-10 h-10" src="https://placehold.co/40x40" />
                    </div>
                </div>
            </div>
            <div className="flex justify-start items-center gap-3">
                <div className="flex justify-start items-center gap-7">
                    <div className="w-64 inline-flex flex-col justify-start items-end">
                        <div className="self-stretch h-11 relative bg-white outline outline-1 outline-offset-[-1px] outline-black overflow-hidden">
                            <div className="left-[86px] top-[7px] absolute text-center justify-start text-black text-2xl font-medium font-['Inter']">내 관심</div>
                            <div className="w-6 h-5 left-[251.38px] top-[31.50px] absolute origin-top-left rotate-180 bg-neutral-700 rounded-sm" />
                        </div>
                    </div>
                </div>
                <div className="flex justify-start items-center gap-6">
                    <div className="w-[468.30px] p-2.5 outline outline-[3px] outline-offset-[-3px] outline-stone-300 flex justify-center items-center gap-5">
                        <div className="flex justify-center items-center gap-7">
                            <div className="flex justify-start items-center gap-2.5">
                                <div className="w-40 justify-center text-black text-xl font-medium font-['Inter']">SK하이닉스</div>
                                <div className="w-28 inline-flex flex-col justify-start items-start">
                                    <div className="self-stretch text-right justify-center text-red-600 text-xl font-semibold font-['Inter']">612,000</div>
                                    <div className="self-stretch text-right justify-center text-black text-base font-medium font-['Inter']">9,922,488</div>
                                </div>
                            </div>
                            <div className="w-4 h-3.5 bg-red-600 rounded-sm" />
                        </div>
                        <div className="w-24 inline-flex flex-col justify-center items-end gap-1">
                            <div className="text-center justify-center text-red-600 text-xl font-semibold font-['Inter']">6,000</div>
                            <div className="text-right justify-center text-red-600 text-base font-medium font-['Inter']">+0.99%</div>
                        </div>
                    </div>
                    <div className="w-10 h-10 bg-zinc-300 rounded-full" />
                    <div className="w-5 h-7 justify-center text-black text-4xl font-normal font-['Inter'] leading-[52px]">+</div>
                </div>
            </div>
        </div>

        {/* 메인 중앙 2열 레이아웃 */}
        <div className="flex flex-col justify-center items-center gap-[30px] self-stretch">

          {/* 좌측 차트 + 간략 재무 정보 */}
          <div className="flex justify-center items-start gap-10 self-stretch">

            {/* 왼쪽 섹션 */}
            <div className="flex flex-col items-start gap-5">

              {/* 종목 상세 정보 박스 */}
              <div className="w-[1000px] p-5 bg-zinc-100 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5">
                  <div className="self-stretch h-[629px] flex flex-col justify-start items-start gap-5">
                      <div className="w-96 flex flex-col justify-start items-start">
                          <div className="w-80 flex flex-col justify-start items-start gap-1">
                              <div className="inline-flex justify-center items-center gap-3.5">
                                  <div className="flex justify-start items-center">
                                      <div className="justify-start text-black text-4xl font-medium font-['Inter']">삼성전자</div>
                                      <div className="w-28 h-5 justify-start text-black text-2xl font-medium font-['Inter']">(005930)</div>
                                  </div>
                              </div>
                              <div className="self-stretch justify-start text-black text-xs font-medium font-['Inter']">10/31 오후 8:21 KST</div>
                              <div className="self-stretch inline-flex justify-start items-center gap-px">
                                  <div className="w-36 justify-center text-black text-4xl font-medium font-['Inter']">107,500</div>
                                  <div className="flex justify-start items-center gap-[5px]">
                                      <div className="justify-center text-red-600 text-xl font-medium font-['Inter']">+3,400</div>
                                      <div className="flex justify-start items-center">
                                          <div className="justify-center text-red-600 text-xl font-medium font-['Inter']">(</div>
                                          <div className="justify-center text-red-600 text-xl font-medium font-['Inter']">+3.27%</div>
                                          <div className="justify-center text-red-600 text-xl font-medium font-['Inter']">)</div>
                                      </div>
                                  </div>
                                  <div className="w-4 h-3.5 bg-red-600 rounded-sm" />
                              </div>
                          </div>
                          <div className="self-stretch inline-flex justify-start items-center gap-2.5">
                              <div className="w-14 py-2.5 bg-zinc-300 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">1일</div>
                              </div>
                              <div className="w-14 py-2.5 bg-stone-50 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">5일</div>
                              </div>
                              <div className="w-14 py-2.5 bg-stone-50 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">1개월</div>
                              </div>
                              <div className="w-14 py-2.5 bg-stone-50 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">1년</div>
                              </div>
                              <div className="w-14 py-2.5 bg-stone-50 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">5년</div>
                              </div>
                              <div className="w-14 py-2.5 bg-stone-50 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
                                  <div className="text-center justify-start text-black text-xl font-medium font-['Inter'] leading-10">최대</div>
                              </div>
                          </div>
                      </div>
                      <img className="self-stretch h-96" src="https://placehold.co/960x434" />
                  </div>
              </div>

              {/* 간략 재무 정보 박스 */}
              <div className="w-[1000px] px-7 py-2.5 bg-zinc-100 rounded-2xl inline-flex justify-start items-start gap-36">
                  <div className="flex-1 inline-flex flex-col justify-start items-start gap-[5px]">
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">전날 종가</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">107,500.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">고가</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">111,500.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">52주 최고</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">111,500.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">시가 총액</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">705.23조</div>
                      </div>
                  </div>
                  <div className="flex-1 inline-flex flex-col justify-start items-start gap-[5px]">
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">시작가</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">106,900.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">저가</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">106,500.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">52주 최저</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">49,900.00</div>
                      </div>
                      <div className="self-stretch inline-flex justify-between items-center">
                          <div className="justify-center text-black text-xl font-medium font-['Inter'] leading-10">발행 주식</div>
                          <div className="text-right justify-center text-black text-xl font-medium font-['Inter'] leading-10">67.36억</div>
                      </div>
                  </div>
              </div>

            </div>
            {/* 재무제표 */}
              <div className="h-[884px] px-8 py-11 bg-zinc-100 rounded-2xl inline-flex justify-center items-center gap-2.5 overflow-hidden">
                  <div className="w-[610px] inline-flex flex-col justify-center items-center gap-5">
                      <div className="justify-start text-black text-4xl font-normal font-['Inter']">재무제표</div>
                      <div className="w-[610px] flex flex-col justify-start items-start gap-5">
                          <div className="self-stretch flex flex-col justify-start items-start gap-2.5">
                              <div className="self-stretch justify-start text-black text-2xl font-normal font-['Inter']">수익성</div>
                              <div className="self-stretch rounded-2xl inline-flex justify-start items-start flex-wrap content-start">
                                  <div className="w-48 h-20 p-2.5 bg-white rounded-tl-2xl border-r border-b border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-24 inline-flex flex-col justify-start items-start gap-2.5">
                                          <div className="self-stretch justify-start text-black text-xl font-medium font-['Inter']">EPS</div>
                                          <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">주당순이익</div>
                                      </div>
                                      <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                  </div>
                                  <div className="w-48 h-20 p-2.5 bg-white border-r border-b border-stone-300 flex justify-between items-start">
                                      <div className="inline-flex flex-col justify-start items-start gap-2.5">
                                          <div className="self-stretch justify-start text-black text-xl font-medium font-['Inter']">ROE</div>
                                          <div className="justify-start text-black text-base font-normal font-['Inter']">자기자본이익률</div>
                                      </div>
                                      <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                  </div>
                                  <div className="w-48 h-20 p-2.5 bg-white rounded-tr-2xl border-b border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-24 inline-flex flex-col justify-start items-start gap-2.5">
                                          <div className="self-stretch justify-start text-black text-xl font-medium font-['Inter']">ROA</div>
                                          <div className="justify-start text-black text-base font-normal font-['Inter']">총자산이익률</div>
                                      </div>
                                      <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                  </div>
                                  <div className="flex justify-start items-center">
                                      <div className="w-72 p-2.5 bg-white rounded-bl-2xl border-r border-stone-300 flex justify-center items-start gap-2.5">
                                          <div className="w-52 inline-flex flex-col justify-start items-start gap-1.5">
                                              <div className="self-stretch inline-flex justify-center items-center gap-12">
                                                  <div className="w-24 justify-start text-black text-xl font-medium font-['Inter'] leading-tight">Operating Margin</div>
                                                  <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                              </div>
                                              <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">영업이익률</div>
                                          </div>
                                      </div>
                                      <div className="w-72 self-stretch p-2.5 bg-white rounded-br-2xl flex justify-center items-center gap-2.5">
                                          <div className="inline-flex flex-col justify-start items-start gap-1.5">
                                              <div className="self-stretch inline-flex justify-center items-center gap-12">
                                                  <div className="justify-start text-black text-xl font-medium font-['Inter']">Net Margin</div>
                                                  <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                              </div>
                                              <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">순이익률</div>
                                          </div>
                                      </div>
                                  </div>
                              </div>
                          </div>
                          <div className="flex flex-col justify-start items-start gap-2.5">
                              <div className="justify-start text-black text-2xl font-normal font-['Inter']">가치(밸류에이션)</div>
                              <div className="w-[600px] inline-flex justify-start items-start flex-wrap content-start">
                                  <div className="w-72 p-2.5 bg-white rounded-tl-2xl border-r border-b border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-52 rounded-tl-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-24 justify-start text-black text-xl font-medium font-['Inter']">PER</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">20배</div>
                                          </div>
                                          <div className="w-52 justify-start text-black text-base font-normal font-['Inter']">주가수익비율</div>
                                      </div>
                                  </div>
                                  <div className="w-72 p-2.5 bg-white rounded-tr-2xl border-b border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-52 rounded-tr-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-24 justify-start text-black text-xl font-medium font-['Inter']">PBR</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">2배</div>
                                          </div>
                                          <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">주가순사잔비율</div>
                                      </div>
                                  </div>
                                  <div className="w-72 p-2.5 bg-white rounded-bl-2xl border-r border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-52 inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-24 justify-start text-black text-xl font-medium font-['Inter']">PSR</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">2배</div>
                                          </div>
                                          <div className="w-52 justify-start text-black text-base font-normal font-['Inter']">주가매출비율</div>
                                      </div>
                                  </div>
                                  <div className="w-72 p-2.5 bg-white rounded-br-2xl flex justify-center items-start gap-2.5">
                                      <div className="w-52 inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-24 justify-start text-black text-xl font-medium font-['Inter']">BPS</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                          </div>
                                          <div className="w-52 justify-start text-black text-base font-normal font-['Inter']">주당순자산가치</div>
                                      </div>
                                  </div>
                              </div>
                          </div>
                          <div className="flex flex-col justify-start items-start gap-2.5">
                              <div className="justify-start text-black text-2xl font-normal font-['Inter']">재무안정성</div>
                              <div className="inline-flex justify-start items-center">
                                  <div className="w-72 h-24 p-2.5 bg-white rounded-tl-2xl rounded-bl-2xl border-r border-stone-300 flex justify-center items-center gap-2.5">
                                      <div className="w-52 rounded-tl-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">Debt Ratio</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">35%</div>
                                          </div>
                                          <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">부채비율</div>
                                      </div>
                                  </div>
                                  <div className="w-72 h-24 p-2.5 bg-white rounded-tr-2xl rounded-br-2xl flex justify-center items-start gap-2.5">
                                      <div className="w-52 rounded-tr-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="justify-start text-black text-lg font-medium font-['Inter'] leading-tight">Interest<br/>Coverage Ratio</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                          </div>
                                          <div className="w-52 justify-start text-black text-base font-normal font-['Inter']">이자보상비율</div>
                                      </div>
                                  </div>
                              </div>
                          </div>
                          <div className="flex flex-col justify-start items-start gap-2.5">
                              <div className="justify-start text-black text-2xl font-normal font-['Inter']">유동성</div>
                              <div className="inline-flex justify-start items-center">
                                  <div className="w-72 p-2.5 bg-white rounded-tl-2xl rounded-bl-2xl border-r border-stone-300 flex justify-center items-start gap-2.5">
                                      <div className="w-52 rounded-tl-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-32 justify-start text-black text-xl font-medium font-['Inter']">Current Ratio</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                          </div>
                                          <div className="w-52 justify-start text-black text-base font-normal font-['Inter']">유동비율</div>
                                      </div>
                                  </div>
                                  <div className="w-72 p-2.5 bg-white rounded-tr-2xl rounded-br-2xl flex justify-center items-start gap-2.5">
                                      <div className="w-52 rounded-tr-2xl inline-flex flex-col justify-start items-start gap-1.5">
                                          <div className="self-stretch inline-flex justify-between items-center">
                                              <div className="w-28 justify-start text-black text-xl font-medium font-['Inter']">Quick Ratio</div>
                                              <div className="justify-start text-black text-xl font-medium font-['Inter']">12.5%</div>
                                          </div>
                                          <div className="self-stretch justify-start text-black text-base font-normal font-['Inter']">당좌비율</div>
                                      </div>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>
              </div>

          </div>

          {/* 분석 결과 박스 */}
          <div className="w-[1714px] h-[572px] px-[677px] py-16 bg-zinc-100 rounded-2xl inline-flex flex-col justify-center items-center gap-2.5 overflow-hidden">
              <div className="px-10 py-3.5 bg-sky-800 rounded-2xl inline-flex justify-center items-end gap-2.5">
                  <div className="text-center justify-start text-white text-3xl font-medium font-['Inter']">분석 결과 보기</div>
              </div>
          </div>

        </div>
      </div>
    </div>
  );
}
