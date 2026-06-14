# SAFE-WM 부동산 실데이터 확보 방안

## 1. 목적

SAFE-WM의 부동산 데이터는 개별 부동산의 감정평가를 확정하기 위한 목적이 아니라, 고령 고객의 자산 안정성 리스크를 판단하기 위한 보조 데이터로 사용한다.

주요 분석 목적은 다음과 같다.

- 부동산 편중 리스크
- 유동성 부족 리스크
- 지역 가격 하락 리스크
- 담보대출 부담 리스크
- 임대소득 의존도 리스크

## 2. 기본 원칙

- MVP 단계에서는 Mock 부동산 데이터를 사용한다.
- 실제 서비스 단계에서는 고객 동의 기반 금융 데이터, 고객/PB 입력, 공개 부동산 API를 결합한다.
- 공개 API 데이터는 추정 및 리스크 보정용으로 사용하며, 확정 시세나 감정평가로 표현하지 않는다.
- 부동산 소유 여부, 주거 목적, 가족 상황, 처분 가능성 등은 API만으로 확정하지 않고 고객/PB 확인 항목으로 둔다.

## 3. 우선 활용 데이터

### 3.1 국토교통부 실거래가 API

우선순위: 1순위

활용 목적:

- 고객 입력 부동산 평가금액 검증 및 보정
- 지역/유형별 최근 거래가격 확인
- 최근 거래 사례 부족 여부 확인
- 거래가격 하락 추세 보조 판단

대상 유형:

- 아파트
- 연립/다세대
- 단독/다가구
- 오피스텔
- 토지
- 상업/업무용
- 공장/창고 등

SAFE-WM 반영 필드:

```text
propertyEstimatedValue
recentTransactionPrice
regionalMedianTransactionPrice
transactionSampleCount
transactionRecencyMonths
priceChangeByRegion
```

분석 반영:

```text
부동산 편중 리스크 = 보정 부동산 가치 / 총자산
담보 리스크 = 담보대출 잔액 / 보정 부동산 가치
거래 유동성 리스크 = 최근 거래 건수 및 거래 시점
```

### 3.2 한국부동산원 R-ONE 부동산통계

우선순위: 2순위

활용 목적:

- 지역별 주택가격지수 흐름 반영
- 아파트 매매가격지수 변동률 반영
- 전세가격지수 변동률 반영
- 공동주택 실거래가격지수 반영
- 오피스텔 가격동향 반영
- 지가변동률 반영
- 상업용부동산 임대동향 반영
- 거래현황 기반 시장 냉각 여부 판단

SAFE-WM 반영 필드:

```text
regionalHousingPriceIndex
apartmentPriceChangeRate
jeonsePriceChangeRate
officetelPriceChangeRate
landPriceChangeRate
commercialRentIndex
transactionVolumeTrend
regionalStressScore
```

분석 반영:

```text
지역 가격 하락 리스크 = 가격지수 변동률 + 실거래가 하락률
유동성 리스크 = 부동산 비중 + 거래량 둔화
임대소득 리스크 = 상업용/오피스텔 임대동향
```

### 3.3 공공데이터포털 부동산종합정보

우선순위: 후순위

활용 목적:

- 토지/건물 속성 보강
- 용도지역/지구 정보 확인
- 건물 유형 및 토지 특성 확인
- 정밀 부동산 속성 리스크 분석

SAFE-WM 반영 필드:

```text
landUse
buildingType
buildingAge
landCategory
propertyAttributeRisk
```

초기 MVP 및 1차 실데이터 전환에는 필수로 보지 않는다. 국토교통부 실거래가 API와 R-ONE 통계 연동 이후, 정밀 분석 단계에서 추가한다.

## 4. 단계별 도입 계획

### Phase 1. MVP

- Mock 부동산 데이터 사용
- 고객별 지역, 유형, 추정가, 유동성 등급 입력
- 부동산 편중 및 담보대출 리스크를 rule 기반으로 계산

필수 mock 필드:

```text
type
region
estimatedValue
isPrimaryResidence
liquidityLevel
priceChange1y
linkedLoanId
```

### Phase 2. 실데이터 1차

- 국토교통부 실거래가 API 연동
- 고객/PB 입력 평가금액을 최근 실거래가로 검증
- 거래 사례 부족 시 confidence를 낮게 부여

산출:

```text
estimatedValueConfidence
recentTransactionSummary
transactionLiquiditySignal
```

### Phase 3. 리스크 고도화

- 한국부동산원 R-ONE 통계 연동
- 지역 가격지수, 거래량, 전세/매매 동향 반영
- 지역 부동산 시장 스트레스 점수 산출

산출:

```text
regionalStressScore
regionalPriceTrend
regionalLiquidityTrend
```

### Phase 4. 정밀 속성 분석

- 공공데이터포털 부동산종합정보 연동
- 토지/건물 속성, 용도지역, 건물 노후도 등을 보조 지표로 반영

산출:

```text
propertyAttributeRisk
landUseRisk
buildingAgeRisk
```

## 5. SAFE-WM 분석 반영 방식

부동산 데이터는 Asset Stability Score의 다음 항목에 반영한다.

```text
realEstateConcentrationPenalty
liquidityShortagePenalty
regionalPriceStressPenalty
collateralDebtPenalty
incomePropertyStressPenalty
```

예시:

```text
부동산 비중 > 60% -> 부동산 편중 패널티
현금성 자산 < 12개월 생활비 -> 유동성 부족 패널티
지역 가격지수 하락 + 거래량 둔화 -> 지역 스트레스 패널티
담보대출 잔액 / 부동산 추정가 > 40% -> 담보대출 부담 패널티
임대소득 의존도가 높고 임대지표가 악화 -> 임대소득 리스크 패널티
```

## 6. 리스크 및 제한사항

- 공개 API 데이터는 최신성, 표본 수, 지역 단위 차이에 따라 정확도가 달라질 수 있다.
- 실거래가는 거래가 발생한 표본만 반영하므로 현재 시세와 차이가 있을 수 있다.
- 고객의 실제 소유 여부와 처분 가능성은 공개 API만으로 판단하지 않는다.
- 부동산 추정가는 상담 보조 지표이며 감정평가 또는 투자 권유로 표현하지 않는다.
- API 호출량, 인증키, 이용 약관, 상업적 활용 조건은 실서비스 전 별도 검토한다.

## 7. 권장 결론

초기 실데이터 전환은 다음 조합으로 진행한다.

```text
1차: 국토교통부 실거래가 API
2차: 한국부동산원 R-ONE 부동산통계
3차: 공공데이터포털 부동산종합정보
```

SAFE-WM MVP에서는 Mock 데이터로 부동산 리스크 산식을 먼저 검증하고, 이후 국토교통부 실거래가 API와 R-ONE 통계를 순차적으로 붙이는 방식이 가장 현실적이다.
