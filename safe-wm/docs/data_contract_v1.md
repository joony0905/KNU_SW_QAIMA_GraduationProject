# SAFE-WM 데이터 계약 v1

## 1. 공통 타입

### DataSource

```text
MOCK
CUSTOMER_INPUT
PB_INPUT
MYDATA
PUBLIC_API
QAIMA_API
DERIVED
FALLBACK
```

### Confidence

```text
HIGH
MEDIUM
LOW
```

### RiskGrade

```text
LOW
MID
HIGH
```

### RiskSeverity

```text
NONE
LOW
MID
HIGH
```

`NONE`은 해당 항목의 위험 신호가 관찰되지 않은 상태이며 penalty 0점이다.
`LOW`는 낮지만 존재하는 위험 신호이며 penalty 5점을 적용한다.

### Priority

```text
NORMAL
WATCH
URGENT
```

### Severity

```text
INFO
WARN
HIGH
CRITICAL
```

## 2. Customer

```json
{
  "customerId": "C001",
  "name": "김OO",
  "age": 68,
  "region": "전북 전주",
  "retirementStatus": "RETIRED",
  "householdSize": 2,
  "monthlyIncome": 1200000,
  "monthlyExpense": 2800000,
  "pensionIncome": 900000,
  "otherIncome": 0,
  "cashAssets": 18000000,
  "depositAssets": 40000000,
  "financialAssets": [],
  "realEstateAssets": [],
  "loans": [],
  "pensionAssets": 70000000,
  "insuranceAssets": 30000000,
  "confirmationItems": []
}
```

## 3. FinancialAsset

```json
{
  "assetId": "FA001",
  "assetType": "STOCK",
  "stockCode": "005930",
  "name": "삼성전자",
  "quantity": 100,
  "avgPrice": 70000,
  "currentPrice": 75000,
  "currency": "KRW",
  "marketValue": 7500000,
  "dataSource": "MOCK",
  "confidence": "HIGH",
  "needsConfirmation": false
}
```

## 4. RealEstateAsset

```json
{
  "propertyId": "RE001",
  "type": "APARTMENT",
  "region": "전북 전주",
  "estimatedValue": 650000000,
  "ownershipRatio": 1.0,
  "isPrimaryResidence": true,
  "monthlyRentIncome": 0,
  "linkedLoanId": "L001",
  "liquidityLevel": "LOW",
  "priceChange1y": -0.04,
  "dataSource": "MOCK",
  "confidence": "MEDIUM",
  "needsConfirmation": true
}
```

## 5. Loan

```json
{
  "loanId": "L001",
  "type": "MORTGAGE",
  "balance": 180000000,
  "interestRate": 0.045,
  "interestType": "VARIABLE",
  "monthlyPayment": 1200000,
  "maturityDate": "2038-12-31",
  "collateralType": "REAL_ESTATE",
  "linkedPropertyId": "RE001",
  "dataSource": "MOCK",
  "confidence": "HIGH",
  "needsConfirmation": false
}
```

## 6. ConfirmationItem

```json
{
  "code": "PRIMARY_RESIDENCE_SELLABILITY",
  "label": "실거주 부동산 처분 가능성",
  "description": "현재 거주 중인 부동산이므로 실제 유동화 가능 여부 확인이 필요합니다.",
  "required": true,
  "dataSource": "CUSTOMER_INPUT",
  "confidence": "LOW"
}
```

## 7. AnalysisResponse

```json
{
  "customerId": "C001",
  "analysisId": "wm-20260615-C001-001",
  "assetStabilityScore": 62,
  "riskGrade": "HIGH",
  "priority": "URGENT",
  "riskAlert": {
    "level": "HIGH",
    "title": "우선 상담 필요",
    "message": "부동산 편중과 현금흐름 부족이 동시에 관찰됩니다."
  },
  "scoreBreakdown": [],
  "riskFactors": [],
  "analysisEvidence": [],
  "cashflowAnalysis": {},
  "liquidityAnalysis": {},
  "realEstateRisk": {},
  "debtRateRisk": {},
  "marketRisk": {},
  "customerExplanation": {},
  "pbActions": [],
  "compliance": {},
  "warnings": [],
  "agentTrace": [
    {
      "agent": "ProfileAgent",
      "status": "SUCCESS",
      "summary": "고객 자산/부채/현금흐름 프로필 정리 완료"
    },
    {
      "agent": "RiskAnalysisAgent",
      "status": "SUCCESS",
      "summary": "자산 안정성 점수 62점, HIGH 등급 산출"
    },
    {
      "agent": "ComplianceGuardAgent",
      "status": "PASS",
      "summary": "투자권유 및 확정 표현 없음"
    }
  ],
  "debug": null
}
```

## 8. RiskFactor

```json
{
  "code": "REAL_ESTATE_CONCENTRATION",
  "severity": "HIGH",
  "title": "부동산 편중",
  "description": "총자산 중 부동산 비중이 높아 긴급 자금이 필요할 때 유동성 부담이 커질 수 있습니다.",
  "affectedAssets": ["RE001"],
  "evidence": [
    {
      "label": "부동산 비중",
      "value": 0.72,
      "unit": "ratio"
    }
  ],
  "dataSource": "DERIVED",
  "confidence": "HIGH",
  "needsConfirmation": false
}
```

## 9. Warning

```json
{
  "code": "QAIMA_CONNECTION_FAILED",
  "message": "QAIMA API connection failed. Fallback data was used.",
  "severity": "WARN",
  "source": "QAIMA_API"
}
```

## 10. DebugTrace

```json
{
  "traceId": "trace-abc",
  "steps": [
    {
      "step": "asset_stability_engine",
      "status": "SUCCESS",
      "durationMs": 12,
      "message": "Asset stability score calculated.",
      "warnings": []
    }
  ]
}
```

## 11. AgentTrace

```json
{
  "agent": "RAGAdvisorAgent",
  "status": "SUCCESS",
  "summary": "고령층 상담 가이드 2건을 근거로 고객용/PB용 설명 초안 생성",
  "startedAt": "2026-06-15T10:20:30+09:00",
  "endedAt": "2026-06-15T10:20:31+09:00",
  "durationMs": 320,
  "warnings": [],
  "retryCount": 0
}
```

### Agent

```text
ProfileAgent
RiskAnalysisAgent
RAGAdvisorAgent
ComplianceGuardAgent
PBActionAgent
```

### Agent Status

```text
SUCCESS
PASS
FAIL
RETRY
FALLBACK
SKIPPED
```

## 12. RagEvidence

```json
{
  "source": "rag_guides.md",
  "section": "고령 금융소비자 상담 원칙",
  "snippet": "고령 고객에게는 상품 권유보다 생활비 안정성과 위험 설명을 우선한다.",
  "score": 0.82
}
```

RAG 근거는 고객 설명과 PB 설명의 내부 근거로 사용한다. 고객 화면에는 긴 원문을 그대로 노출하지 않고, 요약 근거만 제공한다.

## 13. RiskAlert

```json
{
  "level": "HIGH",
  "title": "우선 상담 필요",
  "message": "부동산 편중과 현금흐름 부족이 동시에 관찰됩니다.",
  "triggeredBy": ["REAL_ESTATE_CONCENTRATION", "CASHFLOW_DEFICIT"],
  "dataSource": "DERIVED",
  "confidence": "HIGH"
}
```

## 14. AnalysisEvidence

```json
{
  "riskCategory": "REAL_ESTATE_CONCENTRATION",
  "label": "부동산 비중",
  "value": 0.72,
  "unit": "ratio",
  "reason": "총자산 중 부동산 비중이 60% 기준을 초과합니다.",
  "source": "DERIVED",
  "confidence": "HIGH"
}
```

## 15. AuditLogRecord

```json
{
  "traceId": "trace-abc",
  "customerId": "C001",
  "analysisId": "wm-20260615-C001-001",
  "assetStabilityScore": 62,
  "riskGrade": "HIGH",
  "riskAlertLevel": "HIGH",
  "analysisEvidenceCount": 4,
  "agentTraceSummary": ["ProfileAgent:SUCCESS", "ComplianceGuardAgent:PASS"],
  "complianceStatus": "PASS",
  "generatedOutputSummary": "고객용 설명 및 PB 상담 액션 생성 완료",
  "warnings": [],
  "createdAt": "2026-06-15T10:20:31+09:00"
}
```

Audit log에는 민감한 원문 금융거래 내역, 주소 상세, 계좌번호, 주민번호를 남기지 않는다.
