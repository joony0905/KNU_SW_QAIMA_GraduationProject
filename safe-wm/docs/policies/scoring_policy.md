# SAFE-WM Scoring Policy v1

## 1. 목적

Asset Stability Score와 Risk Grade를 일관되게 산출하기 위한 scoring policy다.

## 2. 기본 점수

```text
baseScore = 100
```

## 3. Severity Penalty

```text
NONE penalty = 0
LOW penalty = 5
MID penalty = 10
HIGH penalty = 20
Score = 100 - totalPenalty
```

`NONE`은 해당 항목에서 위험 신호가 관찰되지 않은 상태다.
`LOW`는 낮지만 존재하는 위험 신호이므로 5점 penalty를 적용한다.

## 4. Risk Grade

```text
LOW: 80 이상
MID: 60 이상 80 미만
HIGH: 60 미만
```

## 5. 주요 Risk Category

```text
REAL_ESTATE_CONCENTRATION
LIQUIDITY_SHORTAGE
CASHFLOW_DEFICIT
DEBT_INTEREST_RATE
MARKET_VOLATILITY
PENSION_SHORTFALL
LIFECYCLE_RISK
```
