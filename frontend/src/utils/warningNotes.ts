// Backend warning code -> user-facing notes.
// Shared by AnalysisResultPanel and SettingPage(saved reports).

type WarningMessageMap = Array<[RegExp, string]>;

const WARNING_MESSAGE_MAP_KO: WarningMessageMap = [
  [/^MARKET_SNAPSHOT_PARTIAL$/, "일부 투자지표는 데이터가 부족해 비어 있을 수 있어요.\n현재 보고서는 위쪽에 출력된 투자 지표들 기반으로 분석됐으니 참고해주세요."],
  [/^INDICATOR_CALC_FAILED$/, "일부 보조지표는 분석 구간 또는 데이터 상태에 따라 계산되지 않을 수 있어요."],
  [/^TTM_FALLBACK_TO_ANNUAL$/, "최근 4개 분기 데이터가 부족해 일부 지표는 연간 기준으로 보정될 수 있어요."],
  [/^MARKET_SNAPSHOT_TTM_FALLBACK_TO_ANNUAL$/, "각 분기의 데이터가 부족해 일부 투자지표는 연간 실적 기준으로 계산될 수 있어요."],
  [/^PRICE_STALE_USED$/, "실시간 가격 대신 최근 캐시 가격이 사용될 수 있어요."],
  [/^PRICE_FETCH_FAILED$/, "실시간 가격을 가져오지 못해 일부 가격 기반 지표가 정확하지 않을 수 있어요."],
  [/^STOCK_NOT_FOUND$/, "분석 대상 종목 정보를 찾지 못했어요."],
  [/^INDUSTRY_MISSING$/, "종목의 산업 분류 정보가 없어 산업 기반 비교가 제한될 수 있어요."],
  [/^INDUSTRY_INDEX_MISSING$/, "산업지수 데이터가 없어 산업 동조 분석이 제한될 수 있어요."],
  [/^INDUSTRY_INDEX_OHLCV_EMPTY$/, "산업지수 가격 데이터가 비어 있어 산업 동조 분석이 제한될 수 있어요."],
  [/^INDUSTRY_INDEX_(SAVE|FETCH)_FAILED$/, "산업지수 데이터를 불러오는 중 일시적인 문제가 발생했어요."],
  [/^PEER_CLUSTER_LOW_POSITIVE_CORR_CANDIDATES$/, "양의 동행성이 충분한 유사 종목 후보가 적어 표시 종목 수가 줄어들 수 있어요."],
  [/^PEER_CLUSTER_(CACHE_MISS|MISSING|EMPTY|DB_EMPTY|ANCHOR_MISSING|INDUSTRY_ID_MISSING)$/, "비교할 유사 종목 데이터를 준비하지 못해 결과가 제한될 수 있어요."],
  [/^PEER_CLUSTER_(CACHE_READ_FAILED|CACHE_WRITE_FAILED|DB_READ_FAILED|JSON_PARSE_FAILED|LOAD_FAILED|INTERNAL_ERROR)$/, "유사 종목 데이터를 처리하는 중 일시적인 문제가 있었어요."],
  [/^INDUSTRY_ADJUSTED_RETURN_FALLBACK_RAW$/, "산업지수 보정 수익률을 계산할 수 없어 원 수익률 기준으로 유사 종목을 비교했어요."],
  [/^PEER_CORR_STABILITY_INSUFFICIENT_DATA$/, "구간별 상관 안정성을 판단하기에는 일부 종목의 데이터가 부족할 수 있어요."],
  [/^PEER_FILTER_RELAXED$/, "요청한 유사 종목 수를 평가하기 위해 극단값 필터를 완화했어요."],
  [/^PEER_CANDIDATES_LOW_AFTER_QUALITY_FILTER$/, "재무 품질 필터링 이후 유사 종목 후보가 줄어들었어요."],
  [/^PEER_CANDIDATES_LOW_AFTER_LIQUIDITY_FILTER$/, "유동성 필터링 이후 유사 종목 후보가 줄어들었어요."],
  [/^PEER_CANDIDATES_LOW_AFTER_VOLATILITY_FILTER$/, "변동성 필터링 이후 유사 종목 후보가 줄어들었어요."],
  [/^PEER_COUNT_REDUCED_BY_CANDIDATE_SIZE$/, "동일 산업 내 비교 가능한 후보 수가 요청한 종목 수보다 적어 실제 표시 수가 줄었어요."],
  [/^PEER_CANDIDATES_INSUFFICIENT$/, "비교할 유사 종목 후보 수가 부족해 일부 결과가 제한될 수 있어요."],
  [/^PEER_DISPLAY_CANDIDATES_LOW$/, "표시 가능한 유사 종목 수가 적어 결과가 간략하게 보일 수 있어요."],
  [/^INSUFFICIENT_PEERS_AFTER_SERIES_FILTER$/, "가격 시계열 조건을 만족하는 유사 종목 후보가 부족했어요."],
  [/^MARKET_CAP_EXCLUDED(_V\d+)?$/, "시가총액 조건에서 일부 종목이 비교 대상에서 제외됐어요."],
  [/^NEWS_NOT_FOUND$/, "관련 뉴스를 찾지 못해 뉴스 기반 분석이 제한될 수 있어요."],
  [/^SENTIMENT_NOT_FOUND$/, "감성 분석 결과를 찾지 못해 일부 지표가 비어 있을 수 있어요."],
  [/^NEWS_FILTER_(APPLIED|EXPANDED_FETCH)$/, "종목 관련성이 높은 뉴스를 선별하기 위해 뉴스 검색 범위를 조정했어요."],
  [/^NEWS_FILTER_INSUFFICIENT_RESULT$/, "조건에 맞는 관련 뉴스 수가 충분하지 않아 일부 뉴스 지표가 제한될 수 있어요."],
  [/^NEWS_BODY_LOW_CONFIDENCE(:|$)/, "일부 뉴스는 본문 추출 신뢰도가 낮아 감성 점수 해석에 주의가 필요해요."],
  [/^NEWS_BODY_FETCH_FAILED(:|$)/, "일부 뉴스는 본문을 가져오지 못해 감성 분석 대상에서 제외됐어요."],
  [/^NEWS_SENTIMENT_LOCAL_/, "뉴스 감성 모델 처리 중 일부 결과가 제한됐어요."],
  [/^NEWS_SENTIMENT_FAILED(:|$)/, "일부 뉴스는 감성 점수를 산출하지 못했어요."],
  [/^NEWS_SENTIMENT_INVALID_(RESPONSE|SCORE)(:|$)/, "일부 뉴스의 감성 점수 응답이 올바르지 않아 결과에서 제외됐어요."],
  [/^NEWS_LIST_FETCH_FAILED$/, "뉴스 목록을 가져오지 못해 뉴스 기반 분석이 제한될 수 있어요."],
  [/^NEWS_DETAIL_(NOT_FOUND|FETCH_FAILED)(:|$)/, "일부 뉴스의 상세 정보를 가져오지 못했어요."],
  [/^NEWS_META_UPSERT_FAILED$/, "일부 뉴스 메타 정보 저장에 실패했지만 분석에는 영향이 없어요."],
  [/^NEWS_INVALID_ITEM_SKIPPED$/, "형식이 맞지 않는 뉴스 항목 일부를 제외했어요."],
  [/^NEWS_PUBDATE_PARSE_FAILED$/, "일부 뉴스 발행시각을 해석하지 못해 현재 시각 기준으로 보정될 수 있어요."],
  [/^NEWS_CACHE_(READ|WRITE)_FAILED$/, "뉴스 캐시 처리 중 일시적인 문제가 있었지만 분석에는 영향이 없어요."],
  [/^SHORT_SELLING_MISSING$/, "공매도 데이터가 없어 공매도 분석이 제한될 수 있어요."],
  [/^SHORT_SELLING_LOAD_FAILED$/, "공매도 데이터를 불러오는 중 일시적인 문제가 있었어요."],
  [/^BASE_RATE_MISSING$/, "기준금리 데이터가 없어 거시 비교 분석이 제한될 수 있어요."],
  [/^BASE_RATE_LOAD_FAILED$/, "기준금리 데이터를 불러오는 중 일시적인 문제가 있었어요."],
  [/^EXTERNAL_API_FALLBACK_USED$/, "외부 데이터 공급이 불안정해 보조 경로로 데이터를 가져왔어요."],
  [/^LLM_EXPLAIN_TIMEOUT$/, "설명 생성이 지연되어 일부 해설이 생략될 수 있어요."],
  [/^LLM_EXPLAIN_RATE_LIMITED$/, "설명 생성 요청이 많아 해설 생성이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_MAX_OUTPUT_TOKENS$/, "설명 생성 분량 제한으로 일부 해설이 축약될 수 있어요."],
  [/^LLM_EXPLAIN_PARSE_FAILED$/, "설명 생성 결과를 구조화하는 과정에서 일부 내용이 누락될 수 있어요."],
  [/^LLM_EXPLAIN_HTTP_500/, "설명 생성 서버가 일시적으로 불안정해 일부 해설이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_HTTP_/, "설명 생성 중 일시적인 오류가 발생해 일부 해설이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_INCOMPLETE(:|$)/, "설명 생성이 중간에 종료되어 일부 해설이 축약될 수 있어요."],
  [/^LLM_EXPLAIN_REFUSAL$/, "설명 생성 정책에 따라 일부 응답이 제한될 수 있어요."],
  [/^LLM_EXPLAIN_FAILED$/, "해설 생성에 실패해 일부 설명이 비어 있을 수 있어요."],
  [/^FEAT2_INTERNAL_ERROR$/, "분석 중 일부 단계에서 내부 오류가 있었지만 가능한 범위에서 결과를 표시했어요."],
];

const WARNING_MESSAGE_MAP_EN: WarningMessageMap = [
  [/^MARKET_SNAPSHOT_PARTIAL$/, "Some investment metrics may be missing because the data is incomplete.\nThe report is based on the metrics shown above."],
  [/^INDICATOR_CALC_FAILED$/, "Some technical indicators may be unavailable for the selected period or data state."],
  [/^TTM_FALLBACK_TO_ANNUAL$/, "Some metrics may use annual figures because recent quarterly data is insufficient."],
  [/^MARKET_SNAPSHOT_TTM_FALLBACK_TO_ANNUAL$/, "Some investment metrics may be calculated from annual results because quarterly data is insufficient."],
  [/^PRICE_STALE_USED$/, "A recent cached price may have been used instead of a real-time price."],
  [/^PRICE_FETCH_FAILED$/, "Real-time price data could not be fetched, so some price-based metrics may be less accurate."],
  [/^STOCK_NOT_FOUND$/, "The target stock could not be found."],
  [/^INDUSTRY_MISSING$/, "Industry classification is missing, so industry-based comparisons may be limited."],
  [/^INDUSTRY_INDEX_MISSING$/, "Industry index data is missing, so industry co-movement analysis may be limited."],
  [/^INDUSTRY_INDEX_OHLCV_EMPTY$/, "Industry index price data is empty, so industry co-movement analysis may be limited."],
  [/^INDUSTRY_INDEX_(SAVE|FETCH)_FAILED$/, "There was a temporary issue loading industry index data."],
  [/^PEER_CLUSTER_LOW_POSITIVE_CORR_CANDIDATES$/, "There were too few positively correlated peer candidates, so fewer peers may be displayed."],
  [/^PEER_CLUSTER_(CACHE_MISS|MISSING|EMPTY|DB_EMPTY|ANCHOR_MISSING|INDUSTRY_ID_MISSING)$/, "Peer data could not be prepared, so the result may be limited."],
  [/^PEER_CLUSTER_(CACHE_READ_FAILED|CACHE_WRITE_FAILED|DB_READ_FAILED|JSON_PARSE_FAILED|LOAD_FAILED|INTERNAL_ERROR)$/, "There was a temporary issue processing peer data."],
  [/^INDUSTRY_ADJUSTED_RETURN_FALLBACK_RAW$/, "Industry-adjusted returns could not be calculated, so peers were compared using raw returns."],
  [/^PEER_CORR_STABILITY_INSUFFICIENT_DATA$/, "Some stocks may not have enough data to assess correlation stability."],
  [/^PEER_FILTER_RELAXED$/, "Outlier filters were relaxed to evaluate the requested number of peers."],
  [/^PEER_CANDIDATES_LOW_AFTER_QUALITY_FILTER$/, "Fewer peer candidates remained after financial quality filtering."],
  [/^PEER_CANDIDATES_LOW_AFTER_LIQUIDITY_FILTER$/, "Fewer peer candidates remained after liquidity filtering."],
  [/^PEER_CANDIDATES_LOW_AFTER_VOLATILITY_FILTER$/, "Fewer peer candidates remained after volatility filtering."],
  [/^PEER_COUNT_REDUCED_BY_CANDIDATE_SIZE$/, "The number of displayed peers was reduced because comparable candidates in the same industry were limited."],
  [/^PEER_CANDIDATES_INSUFFICIENT$/, "There were not enough peer candidates, so some results may be limited."],
  [/^PEER_DISPLAY_CANDIDATES_LOW$/, "Only a small number of peers can be displayed, so the result may appear simplified."],
  [/^INSUFFICIENT_PEERS_AFTER_SERIES_FILTER$/, "Too few peer candidates satisfied the price-series requirements."],
  [/^MARKET_CAP_EXCLUDED(_V\d+)?$/, "Some stocks were excluded from comparison by market-cap conditions."],
  [/^NEWS_NOT_FOUND$/, "No related news was found, so news-based analysis may be limited."],
  [/^SENTIMENT_NOT_FOUND$/, "Sentiment results were not found, so some metrics may be empty."],
  [/^NEWS_FILTER_(APPLIED|EXPANDED_FETCH)$/, "The news search scope was adjusted to select more relevant articles."],
  [/^NEWS_FILTER_INSUFFICIENT_RESULT$/, "There were not enough matching related articles, so some news metrics may be limited."],
  [/^NEWS_BODY_LOW_CONFIDENCE(:|$)/, "Some article bodies were extracted with low confidence; interpret their sentiment scores carefully."],
  [/^NEWS_BODY_FETCH_FAILED(:|$)/, "Some article bodies could not be fetched and were excluded from sentiment analysis."],
  [/^NEWS_SENTIMENT_LOCAL_/, "Some results were limited while processing the local news sentiment model."],
  [/^NEWS_SENTIMENT_FAILED(:|$)/, "Some articles could not receive a sentiment score."],
  [/^NEWS_SENTIMENT_INVALID_(RESPONSE|SCORE)(:|$)/, "Some sentiment responses were invalid and excluded from the result."],
  [/^NEWS_LIST_FETCH_FAILED$/, "The news list could not be fetched, so news-based analysis may be limited."],
  [/^NEWS_DETAIL_(NOT_FOUND|FETCH_FAILED)(:|$)/, "Some article details could not be fetched."],
  [/^NEWS_META_UPSERT_FAILED$/, "Some news metadata could not be saved, but this does not affect the analysis."],
  [/^NEWS_INVALID_ITEM_SKIPPED$/, "Some malformed news items were skipped."],
  [/^NEWS_PUBDATE_PARSE_FAILED$/, "Some publication times could not be parsed and may be adjusted using the current time."],
  [/^NEWS_CACHE_(READ|WRITE)_FAILED$/, "There was a temporary issue with the news cache, but the analysis can continue."],
  [/^SHORT_SELLING_MISSING$/, "Short-selling data is missing, so short-selling analysis may be limited."],
  [/^SHORT_SELLING_LOAD_FAILED$/, "There was a temporary issue loading short-selling data."],
  [/^BASE_RATE_MISSING$/, "Base-rate data is missing, so macro comparison may be limited."],
  [/^BASE_RATE_LOAD_FAILED$/, "There was a temporary issue loading base-rate data."],
  [/^EXTERNAL_API_FALLBACK_USED$/, "A fallback data path was used because an external data provider was unstable."],
  [/^LLM_EXPLAIN_TIMEOUT$/, "Explanation generation was delayed, so some commentary may be omitted."],
  [/^LLM_EXPLAIN_RATE_LIMITED$/, "Explanation generation was limited because request volume was high."],
  [/^LLM_EXPLAIN_MAX_OUTPUT_TOKENS$/, "Some commentary may be shortened due to the output length limit."],
  [/^LLM_EXPLAIN_PARSE_FAILED$/, "Some commentary may be missing because the generated explanation could not be fully structured."],
  [/^LLM_EXPLAIN_HTTP_500/, "The explanation service was temporarily unstable, so some commentary may be limited."],
  [/^LLM_EXPLAIN_HTTP_/, "A temporary error occurred during explanation generation, so some commentary may be limited."],
  [/^LLM_EXPLAIN_INCOMPLETE(:|$)/, "Explanation generation ended early, so some commentary may be shortened."],
  [/^LLM_EXPLAIN_REFUSAL$/, "Some response content may be limited by explanation-generation policy."],
  [/^LLM_EXPLAIN_FAILED$/, "Explanation generation failed, so some commentary may be empty."],
  [/^FEAT2_INTERNAL_ERROR$/, "Some analysis steps had internal errors, but available results are shown where possible."],
];

const isEnglish = (language?: string | null) => (language ?? "").toLowerCase().startsWith("en");

const warningCode = (raw: unknown): string | null => {
  if (typeof raw === "string") return raw;
  if (!raw || typeof raw !== "object") return null;
  const record = raw as Record<string, unknown>;
  return typeof record.code === "string" ? record.code : null;
};

export function mapWarningsToNotes(warnings?: unknown[] | string[] | null, language?: string | null): string[] {
  if (!warnings || warnings.length === 0) return [];
  const messageMap = isEnglish(language) ? WARNING_MESSAGE_MAP_EN : WARNING_MESSAGE_MAP_KO;
  const messages: string[] = [];
  for (const raw of warnings) {
    const code = warningCode(raw);
    if (!code) continue;
    let matched = false;
    for (const [pattern, message] of messageMap) {
      if (pattern.test(code)) {
        messages.push(message);
        matched = true;
        break;
      }
    }
    if (!matched) continue;
  }
  return Array.from(new Set(messages));
}

export function expandWarningLines(notes: string[]): string[] {
  return notes.flatMap((note) =>
    note
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean),
  );
}
