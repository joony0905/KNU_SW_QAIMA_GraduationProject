# 공통 차트 조회 로직 및 Fallback 정책 문서 - 2026-05-23

## 목적

현재 프로젝트의 공통 차트 조회 흐름을 코드 기준으로 정리한다.

범위:

- 프론트 차트 API 호출
- Spring 차트 API 진입점
- DB 조회 및 외부 fetch 판단
- `StockClient` 최종 라우터 역할
- KIS -> Marketstack -> Yahoo fallback 흐름
- provider별 인터페이스와 지원 범위
- 저장, dedupe, 응답 timestamp 정책

이 문서는 분석/정책 문서이며, 이 문서 작성 요청에서는 코드 수정은 하지 않았다.

## 전체 흐름 요약

```text
Frontend
  fetchCandles / fetchCandlesBefore
    |
    v
GET /api/v1/charts/candles
    |
    v
ChartController
    |
    v
ChartService
    |
    v
StockService.getOrCreateStockByCode(stockCode)
    |
    v
CandleLoadService
    |
    |-- DB hit and fresh -> DB rows 반환
    |
    |-- DB empty/stale/insufficient -> StockClient.fetchCandles(...)
                                      |
                                      v
                              StockApiClient
                                      |
                                      |-- KIS
                                      |-- Marketstack fallback
                                      |-- Yahoo fallback
                                      |-- EMPTY
                                      |
                                      v
                             CandleFetchResult
    |
    v
저장/merge/dedupe
    |
    v
CandleMapper.toSeries(...)
    |
    v
ApiResponse<CandleSeriesResponse>
```

## 프론트엔드 호출부

파일:

- `frontend/src/api/charts.ts`
- `frontend/src/api/endpoints.ts`
- `frontend/src/pages/StocksMockPage.tsx`
- `frontend/src/pages/Feature2MockPage.tsx`
- `frontend/src/components/TradingViewWidget.tsx`

### 기본 범위 조회

`fetchCandles(stockCode, freq, from, to)`는 다음 endpoint를 호출한다.

```text
GET /charts/candles?stockCode={stockCode}&freq={freq}&from={from}&to={to}
```

프론트 API base 설정상 backend 실제 controller path는:

```text
/api/v1/charts/candles
```

### 과거 추가 조회

`fetchCandlesBefore(stockCode, freq, to, limit)`는 같은 endpoint를 사용하되 `from` 없이 `limit`을 전달한다.

```text
GET /charts/candles?stockCode={stockCode}&freq={freq}&to={to}&limit={limit}
```

이 경로는 차트 줌아웃/스크롤 시 과거 candle을 부분적으로 추가 로딩하기 위해 사용된다.

## Backend API 진입점

파일:

- `backend/src/main/java/com/qaima/api/Chart/ChartController.java`

메서드:

```java
@GetMapping("/candles")
public Mono<ApiResponse<CandleSeriesResponse>> getCandles(
    @RequestParam String stockCode,
    @RequestParam Freq freq,
    @RequestParam(required = false) OffsetDateTime from,
    @RequestParam OffsetDateTime to,
    @RequestParam(required = false) Integer limit
)
```

분기:

```text
limit != null
  -> chartService.getCandlesBefore(stockCode, freq, to, limit)

limit == null
  -> chartService.getCandles(stockCode, freq, from, to)
```

응답 DTO:

```java
CandleSeriesResponse {
  stockCode: string
  freq: string
  source: string
  timezone: string
  data: List<CandleDto>
}

CandleDto {
  t: long   // epoch seconds
  o: double
  h: double
  l: double
  c: double
  v: long
}
```

현재 controller 응답 timezone label:

```text
Asia/Seoul
```

주의:

- `CandleSeriesResponse` 주석에는 아직 `"UTC"`라고 남아 있으나, 실제 controller 값은 `CandleTimePolicy.DEFAULT_TRADING_ZONE` 기반 `Asia/Seoul`이다.

## ChartService 역할

파일:

- `backend/src/main/java/com/qaima/service/chart/ChartService.java`

역할:

- 종목 코드로 `Stock` entity를 확보한다.
- 차트 데이터 로딩은 `CandleLoadService`에 위임한다.
- API response/warning 판단은 하지 않는다. 이 책임은 `ChartController`가 가진다.

주요 흐름:

```java
stockService.getOrCreateStockByCode(stockCode)
    .flatMap(stock -> candleLoadService.load(stock, freq, from, to));
```

과거 추가 조회:

```java
stockService.getOrCreateStockByCode(stockCode)
    .flatMap(stock -> candleLoadService.loadBefore(stock, freq, to, limit));
```

## CandleLoadService 역할

파일:

- `backend/src/main/java/com/qaima/service/candle/CandleLoadService.java`

역할:

- DB 우선 조회
- stale/insufficient 판단
- 외부 provider fetch 위임
- 신규 candle 저장
- 기존/신규 row merge
- 일봉 logical trading date dedupe
- Feature3 raw price 경로 재사용

### 일반 범위 조회 `load`

입력:

```java
Stock stock
Freq freq
OffsetDateTime from
OffsetDateTime to
```

DB 조회:

```java
priceOhlcvRepository.findRange(
    stockCode,
    freq,
    dbRangeFromForRead(stock, freq, from),
    to
)
```

국내 일봉은 legacy `15:00` row가 range 시작점에서 빠지지 않도록 `from - 9h`로 DB 조회 범위를 넓힌다.

DB 결과가 충분하고 최신이면:

```text
CandleLoadResult(dbCandles, DB)
```

DB가 비었거나 최신 거래일까지 없으면:

```text
stockClient.fetchCandles(stock, freq, from, to)
  -> save(...)
  -> CandleLoadResult(savedOrMerged, providerSource)
```

### Feature3용 조회 `loadForFeature3`

기능3 원시 종가 시계열에서 사용한다.

일반 차트와의 차이:

- DB row가 있더라도 `requiredRows`보다 적으면 외부 fetch를 시도한다.
- 외부 fetch 실패 시 기존 DB row를 최대한 반환한다.
- KIS decode error는 숨기지 않고 전파한다.

### 과거 추가 조회 `loadBefore`

입력:

```java
Stock stock
Freq freq
OffsetDateTime to
int limit
```

1차 DB 조회:

```java
priceOhlcvRepository.findBefore(stockCode, freq, to, PageRequest.of(0, limit))
```

DB hit이면 desc 결과를 asc로 정렬해서 반환한다.

DB miss이면:

```text
computeLookbackFrom(freq, to, limit)
  -> stockClient.fetchCandles(stock, freq, from, to)
  -> save(...)
  -> findBefore(...) 재조회
  -> asc 정렬 반환
```

`computeLookbackFrom`은 provider가 직접 `limit` 조회를 지원하지 않는 경우를 감안해 넉넉한 range를 잡는다.

```text
ONE_MIN     -> to - n minutes
FIVE_MIN    -> to - 5n minutes
FIFTEEN_MIN -> to - 15n minutes
ONE_H       -> to - n hours
ONE_D       -> to - n days
ONE_W       -> to - n weeks
ONE_M       -> to - n months
```

여기서 `n = max(limit * 200, 200)`.

## DB Repository

파일:

- `backend/src/main/java/com/qaima/repository/PriceOhlcvRepository.java`

### 범위 조회

```java
findRange(stockCode, freq, from, to)
```

조건:

```sql
stockCode = :stockCode
freq = :freq
ts >= :from
ts < :to
order by ts asc
```

### 과거 조회

```java
findBefore(stockCode, freq, to, pageable)
```

조건:

```sql
stockCode = :stockCode
freq = :freq
ts < :to
order by ts desc
limit pageable
```

### 벌크 범위 조회

```java
findRangeBulk(stockCodes, freq, from, to)
```

기능3/클러스터링 등 여러 종목 시계열 조회에서 사용될 수 있는 구조다.

## 시간 정책

파일:

- `backend/src/main/java/com/qaima/service/candle/CandleTimePolicy.java`

### 기본 trading zone

```java
DEFAULT_TRADING_ZONE = Asia/Seoul
```

### timezone 해석 순서

1. `stock.exchange.timezone` 값이 있으면 우선 사용
2. 값이 없거나 invalid이면 exchange code mapping 사용
3. 미국 일부 거래소는 `America/New_York`
4. 그 외는 `Asia/Seoul`

미국 mapping:

```text
NASDAQ, NYSE, AMEX, XNAS, XNYS, ARCX, BATS
  -> America/New_York
```

기본 fallback:

```text
Asia/Seoul
```

### 일봉 canonical timestamp

`Freq.ONE_D`는 provider timestamp 자체가 아니라 trading date를 기준으로 canonical timestamp를 만든다.

```java
LocalDate tradingDate = tradingDate(ts, tradingZone);
OffsetDateTime canonicalTs = tradingDate.atStartOfDay(tradingZone).toOffsetDateTime();
```

### legacy `15:00` 호환

국내 일봉 legacy row:

```text
2026-04-02 15:00:00
```

을 logical KST day:

```text
2026-04-03
```

로 읽기 위한 임시 호환 로직이 있다.

적용 조건:

- zone이 `Asia/Seoul`
- local time이 정확히 `15:00`
- `ONE_D` logical date 계산/응답 canonicalization 경로

이 로직은 DB cleanup 전 호환 계층이며, 장기적으로는 DB legacy row 정리 후 제거 또는 축소하는 것이 좋다.

## StockClient 인터페이스

파일:

- `backend/src/main/java/com/qaima/external/StockClient.java`

공통 provider 라우터 인터페이스다.

```java
public interface StockClient {
    Mono<StockDto> fetchStock(Stock stock);
    Mono<ApiResponse<StockMeta>> fetchTickerMeta(String symbol);
    Mono<MarketStackTickersResponse> fetchTickers();
    Mono<CandleFetchResult> fetchCandles(
        Stock stock,
        Freq freq,
        OffsetDateTime from,
        OffsetDateTime to
    );
}
```

차트 공통 조회에서 중요한 메서드는:

```java
fetchCandles(...)
```

반환 타입:

```java
CandleFetchResult {
  List<PriceOhlcvDto> candles
  CandleSource source
}
```

## StockApiClient: 최종 라우터

파일:

- `backend/src/main/java/com/qaima/external/StockApiClient.java`

`@Primary` 구현체이므로 `StockClient` 주입 시 기본 라우터 역할을 한다.

주입 provider:

```java
private final KrStockClient krClient;
private final GlobalStockClient globalClient;
private final YahooFinancePriceClient yahooClient;
```

timeout:

```text
KIS         3초
Marketstack 4초
Yahoo       4초
```

### 차트 fallback 순서

현재 `fetchCandles` fallback 순서:

```text
1. KIS
2. Marketstack
3. Yahoo
4. EMPTY
```

실제 흐름:

```java
fetchCandlesFromKis(...)
  .map(list -> new CandleFetchResult(list, KIS))
  .switchIfEmpty(fetchCandlesFromMarketstack(...).map(... MARKETSTACK))
  .switchIfEmpty(fetchCandlesFromYahoo(...).map(... YAHOO))
  .switchIfEmpty(new CandleFetchResult(List.of(), EMPTY))
```

### symbol/code mapping

입력 stock code canonicalization:

```text
005930      -> 005930
005930.KS   -> 005930
aapl        -> AAPL
```

Marketstack symbol:

```text
국내 6자리 숫자 -> {code}.XKRX
그 외          -> canonical code 그대로
```

Yahoo symbol:

```text
KOSDAQ         -> {code}.KQ
KOSPI, KONEX   -> {code}.KS
기타 국내 6자리 -> {code}.KS, {code}.KQ 순차 시도
비국내          -> canonical code 그대로
```

KIS market division:

현재 구현은 모든 국내 exchange code를 `"J"`로 보낸다.

```text
KOSPI  -> J
KOSDAQ -> J
KONEX  -> J
KRX    -> J
default -> J
```

## Provider 1: KIS

파일:

- `backend/src/main/java/com/qaima/external/KrStockClient.java`

차트 메서드:

```java
fetchCandles(stockCode, marketDivCode, freq, from, to)
```

KIS endpoint:

```text
/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice
```

주요 query parameter:

```text
FID_COND_MRKT_DIV_CODE = marketDivCode
FID_INPUT_ISCD         = stockCode
FID_PERIOD_DIV_CODE    = interval
FID_INPUT_DATE_1       = yyyyMMdd(from)
FID_INPUT_DATE_2       = yyyyMMdd(to)
FID_ORG_ADJ_PRC        = 0
```

interval mapping:

```text
ONE_D -> D
ONE_W -> W
ONE_M -> M
ONE_H -> 60M
그 외 -> D
```

KIS response mapping:

```text
stck_bsop_date -> ts
stck_oprc      -> open
stck_hgpr      -> high
stck_lwpr      -> low
stck_clpr      -> close
acml_vol       -> volume
```

KIS date parse:

```text
yyyyMMdd -> Asia/Seoul start of day
```

KIS 실패 처리:

- HTTP error -> `KIS_HTTP_ERROR`
- JSON decode error -> `KIS_DECODE_ERROR`
- KIS business error -> `KIS_BIZ_ERROR`
- market closed류 -> `KIS_MARKET_CLOSED`

`StockApiClient.fetchCandlesFromKis` 정책:

- 정상 non-empty list -> `KIS`
- empty list -> fallback 진행
- `KIS_DECODE_ERROR` -> 상위로 전파
- 그 외 KIS error -> empty 처리 후 provider fallback 진행

`CandleLoadService` 정책:

- `KIS_DECODE_ERROR`는 숨기지 않고 전파
- `KIS_HTTP_ERROR`, `KIS_BIZ_ERROR`, `KIS_MARKET_CLOSED`는 `EMPTY` fallback으로 처리하는 방어 로직이 있다.
- 단, 현재는 `StockApiClient`가 대부분의 non-decode KIS error를 provider fallback으로 흡수한다.

## Provider 2: Marketstack

파일:

- `backend/src/main/java/com/qaima/external/GlobalStockClient.java`

차트 메서드:

```java
fetchCandlesByMkstackCode(mkstackCode, freq, from, to)
```

endpoint:

```text
/eod
```

query parameter:

```text
access_key
symbols   = mkstackCode
date_from = from.toLocalDate()
date_to   = to.toLocalDate()
limit     = 5000
```

지원 범위:

- `StockApiClient`에서 `Freq.ONE_D`일 때만 Marketstack fallback을 호출한다.
- `ONE_D`가 아니면 fallback skip 후 Yahoo 단계로 넘어간다.

Marketstack date parse 순서:

1. `epochSeconds`가 있으면 `UTC OffsetDateTime`
2. ISO offset datetime parse
3. `yyyy-MM-dd'T'HH:mm:ssZ` parse
4. date-only `yyyy-MM-dd`는 UTC start of day

응답 필수값:

- `open`, `high`, `low`, `close`가 모두 있어야 유효 row
- `volume` null이면 `BigDecimal.ZERO`

실패 처리:

- empty list -> Yahoo fallback
- timeout/error -> Yahoo fallback

## Provider 3: Yahoo

파일:

- `backend/src/main/java/com/qaima/external/YahooFinancePriceClient.java`

차트 메서드:

```java
fetchCandles(symbol, freq, from, to)
```

endpoint:

```text
https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
```

query parameter:

```text
period1 = from.toEpochSecond()
period2 = max(period1 + 86400, to.toEpochSecond())
interval = 1d
events = history
includeAdjustedClose = false
```

지원 범위:

- `Freq.ONE_D`만 지원
- `ONE_D`가 아니면 empty list 반환

Yahoo symbol 후보는 `StockApiClient.yahooSymbolsOf(...)`가 만든다.

국내 unknown/ambiguous exchange는 다음 순서로 시도한다.

```text
{code}.KS
{code}.KQ
```

응답 mapping:

```text
timestamp[i]         -> ts (UTC OffsetDateTime)
quote.open[i]        -> open
quote.high[i]        -> high
quote.low[i]         -> low
quote.close[i]       -> close
quote.volume[i]      -> volume
```

실패 처리:

- 특정 symbol 실패/empty -> 다음 symbol 후보
- 모든 후보 실패/empty -> EMPTY

## 저장 정책

저장은 `CandleLoadService.save(...)`에서 담당한다.

외부 provider는 `PriceOhlcvDto`를 반환하고, 저장 직전에 entity로 변환된다.

```text
PriceOhlcvDto
  -> CandleLoadService.toEntity(...)
  -> PriceOhlcv
  -> priceOhlcvRepository.saveAll(...)
```

`Freq.ONE_D` 저장 시:

```java
CandleTimePolicy.canonicalTs(ts, freq, tradingZone)
```

즉 신규 일봉 저장은 trading date 기준 `00:00` canonical timestamp가 목표다.

중복 방지:

```text
stockId | freq | tradingDate
```

로 logical key를 만든다.

DB primary key는 여전히:

```text
stock_id, ts, freq
```

이므로 runtime dedupe가 중요하다. 특히 legacy `15:00` row와 canonical `00:00` row가 DB상 서로 다른 PK로 공존할 수 있다.

## 응답 정책

`CandleMapper.toSeries(...)`가 최종 chart series를 만든다.

정책:

- null row 제거
- o/h/l/c 필수값 누락 row 제거
- `ONE_D`는 `CandleTimePolicy` 기준 canonical timestamp로 출력
- 같은 logical trading date의 중복 row 제거
- 최종 `CandleDto.t`는 epoch seconds

응답 source:

```text
DB
KIS
MARKETSTACK
YAHOO
EMPTY
```

warning:

- candles empty 또는 source `EMPTY` -> `NO_DATA`
- source `MARKETSTACK` -> `CHART_FALLBACK_TO_GLOBAL`

현재 Yahoo fallback에 대한 별도 warning 문자열은 없다. `source`에는 `YAHOO`가 표시된다.

## Feature1/Feature2와 공통 차트

기능1, 기능2의 메인 차트는 공통 차트 endpoint를 사용한다.

프론트:

- 기능1: `StocksMockPage.tsx`
- 기능2: `Feature2MockPage.tsx`

공통:

- `fetchCandles(...)`
- `fetchCandlesBefore(...)`
- `TradingViewWidget`

기능2의 줌아웃 추가 로딩도 기능1과 같은 `fetchCandlesBefore(...)` 경로를 사용한다.

## Feature3와 공통 차트의 관계

Feature3의 raw price series는 chart controller를 직접 통과하지는 않지만, 내부에서 `CandleLoadService.loadForFeature3(...)`를 사용한다.

따라서 다음 정책은 공통으로 적용된다.

- DB 조회
- external fallback
- `StockClient` 라우터
- daily canonical timestamp 저장
- logical trading date dedupe

단, Feature3 benchmark series는 `industry_index_ohlcv`와 `IndustryIndexFetcher`를 사용하는 별도 경로다.

## 주요 인터페이스 정리

### `StockClient`

외부 시세 라우터 추상화.

```java
Mono<CandleFetchResult> fetchCandles(
    Stock stock,
    Freq freq,
    OffsetDateTime from,
    OffsetDateTime to
);
```

### `CandleFetchResult`

provider fetch 결과.

```java
List<PriceOhlcvDto> candles
CandleSource source
```

### `CandleLoadResult`

DB 저장/merge 후 service 결과.

```java
List<PriceOhlcv> candles
CandleSource source
```

### `PriceOhlcvDto`

provider에서 받은 표준 OHLCV DTO.

```text
ts
freq
open
high
low
close
volume
```

### `CandleSeriesResponse`

API 최종 응답.

```text
stockCode
freq
source
timezone
data: CandleDto[]
```

## 현재 정책상 주의할 점

1. `Marketstack`과 `Yahoo` fallback은 현재 `ONE_D`만 지원한다.
2. KIS path는 `toKisInterval`에서 `ONE_H -> 60M` mapping이 있으나 endpoint 자체가 daily item chart endpoint라 실제 intraday 의미는 별도 검증이 필요하다.
3. `CandleSeriesResponse`의 Java 주석에는 timezone `"UTC"`가 남아 있지만 실제 응답은 `Asia/Seoul`이다.
4. DB에는 legacy `15:00` row가 남아 있으므로 runtime 호환과 별개로 DB cleanup migration이 필요하다.
5. `StockApiClient`가 최종 라우터이며, provider별 실패를 대부분 empty로 변환해 다음 fallback으로 넘긴다.
6. `KIS_DECODE_ERROR`는 데이터 계약/파싱 오류로 보고 fallback으로 숨기지 않는다.

## 최종 요약

공통 차트 조회는 다음 구조다.

```text
ChartController
  -> ChartService
  -> CandleLoadService
  -> PriceOhlcvRepository
  -> StockClient(StockApiClient)
     -> KIS
     -> Marketstack
     -> Yahoo
     -> EMPTY
  -> save/merge/dedupe
  -> CandleMapper
  -> CandleSeriesResponse
```

정책 핵심:

- DB 우선
- DB가 없거나 최신 거래일까지 부족하면 external fetch
- external fetch는 `StockClient` 최종 라우터가 담당
- fallback 순서는 `KIS -> Marketstack -> Yahoo -> EMPTY`
- 신규 일봉 저장은 trading-date `00:00` canonical timestamp
- 기존 legacy `15:00` row는 runtime에서 logical trading date로 보정
- API 응답은 `CandleMapper`에서 canonicalized epoch seconds로 변환
