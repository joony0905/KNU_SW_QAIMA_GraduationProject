# SAFE-WM 최종 구현안 v1

## 1. 구현 목표

SAFE-WM은 고령 고객의 자산 안정성 리스크를 분석하고, 고객이 이해할 수 있는 설명과 PB가 확인할 상담 우선순위를 제공하는 독립 서비스다.

QAIMA 저장소 루트 안의 `safe-wm/` 디렉터리에 구현하지만, 향후 `safe-wm/`만 별도 레포지토리로 분리할 수 있도록 독립 앱으로 구성한다.

## 2. 경계 원칙

- 구현 및 수정 범위는 `safe-wm/` 내부로 제한한다.
- QAIMA의 `backend/`, `analysis/`, `frontend/` 코드는 참고만 한다.
- SAFE-WM 코드에서 QAIMA 내부 모듈을 직접 import하지 않는다.
- QAIMA 연동은 HTTP adapter를 통해서만 수행한다.
- QAIMA 서버가 꺼져도 SAFE-WM은 mock fallback으로 동작해야 한다.
- MVP는 고객 Mock 데이터 기반으로 동작한다.

## 3. 시스템 아키텍처

```text
SAFE-WM Frontend (React, port 5174)
  |
  | HTTP
  v
SAFE-WM Backend (FastAPI, port 8100)
  |
  +-- Customer Repository
  |     - mock_customers.json
  |
  +-- Asset Stability Engine
  |     - 부동산 편중
  |     - 유동성 부족
  |     - 현금흐름 부족
  |     - 대출/금리 리스크
  |     - 금융자산 시장 리스크
  |     - 연금/은퇴자금 부족
  |
  +-- QAIMA HTTP Adapter (optional)
  |     - Feature3 portfolio analysis
  |     - base-rate / macro-rates
  |     - 실패 시 fallback + warning
  |
  +-- RAG Store
  |     - rag_guides.md
  |     - 고령 금융소비자 보호 가이드
  |     - PB 상담 체크리스트
  |
  +-- LangGraph Multi-Agent Workflow
        |
        +-- Preparation Harness
        +-- Profile Agent
        +-- Risk Analysis Agent
        +-- Stability Assessment Agent
        +-- Severity Classification Agent
        +-- Evidence & Reasoning Agent
        +-- RAG Advisor Agent
        +-- Compliance Guard Agent
        +-- PB Action Agent
```

## 3.1 AI Agent 공통요건 대응

SAFE-WM의 AI Agent는 판단-행동-검증/개선 구조를 가진다.

판단:

- 고객 자산, 부채, 현금흐름, 시장 데이터를 해석하여 자산 안정성 점수와 위험 우선순위를 도출한다.

행동:

- 위험 등급에 따라 고객용 설명, PB 상담 요약, 확인 필요 항목, 상담 우선순위를 생성한다.

검증/개선:

- Compliance Guard Agent가 생성 결과를 검토한다.
- 투자권유 표현, 확정 표현, 개인정보 노출 위험이 발견되면 RAG Advisor Agent로 되돌려 문구를 재생성한다.

## 3.2 핵심 설계 원칙

SAFE-WM은 투자 추천 AI 또는 자동 자산 운용 AI가 아니라, Rule + Agent 기반 자산 안정성 분석 시스템이자 PB 보조형 AI Agent 플랫폼이다.

핵심 원칙:

```text
판단: Agent 기반
계산: Rule 기반
설명: LLM 기반
검증: Compliance Loop 기반
```

Hybrid 구조를 채택하는 이유:

- 모든 판단을 Rule로 처리하면 자산 안정성 해석 로직이 지나치게 복잡해진다.
- 모든 판단을 LLM Agent로 처리하면 점수 일관성, 금융 신뢰성, 환각 위험이 커진다.
- 따라서 Agent는 리스크 해석과 evidence 생성을 담당하고, 최종 점수 계산과 등급 산출은 고정 scoring policy가 담당한다.

최종 책임 분리:

```text
Agent 기반:
  - 리스크 해석
  - risk severity 판단
  - evidence 생성
  - 설명 생성
  - 상담 액션 생성

Rule/Guard 기반:
  - 점수 계산
  - severity -> penalty 변환
  - Asset Stability Score 산출
  - Risk Grade 산출
  - Priority 산출
  - deterministic compliance validation
```

MVP 구현에서는 Compliance를 LLM Agent의 주관적 판단으로 처리하지 않는다. `Compliance Guard`는 deterministic rule validator로 구현하고, LLM/Agent는 설명 생성과 상담 액션 생성을 담당한다.

## 3.3 기술 스택

```text
Frontend: React, Vite
Backend: FastAPI
Agent Orchestration: LangGraph
RAG: Markdown guide + simple keyword retrieval / Chroma optional
LLM: OpenAI API
Rule Engine: Python
External Adapter: QAIMA HTTP API
Storage: JSON Mock, SQLite optional
```

## 3.4 데이터 확장 방향

### MyData API 확장

MVP는 Mock 데이터를 사용하지만, 실제 서비스 전환 시 고객 동의 기반 MyData API를 통해 다음 데이터를 연계한다.

```text
금융자산:
  - 예금
  - 적금
  - 증권계좌
  - 펀드
  - ETF
  - 현금성 자산

부채:
  - 담보대출
  - 신용대출
  - 변동금리 여부
  - 대출 금리
  - 상환 스케줄

현금흐름:
  - 월 입출금
  - 소비 패턴
  - 고정지출
  - 소득 흐름

연금:
  - 국민연금
  - 퇴직연금
  - 개인연금
  - 예상 연금 수령액

보험:
  - 보험 가입 현황
  - 의료/실손 여부
  - 보장 범위
```

MyData만으로 부동산 처분 가능성, 가족 부양, 의료비 전망, 실거주 여부 같은 맥락은 확정할 수 없으므로 고객/PB 확인 항목으로 둔다.

### Market API 확장

외부 리스크 신호는 다음 범위로 확장한다.

```text
금리:
  - 기준금리
  - 대출금리
  - 시장금리

환율:
  - 원/달러
  - 환율 변동성

시장 변동성:
  - KOSPI/KOSDAQ 변동성
  - VIX 유사 지표

수급 데이터:
  - 외국인 수급
  - 기관 수급

부동산:
  - 국토교통부 실거래가
  - 지역별 가격 변화
```

## 4. 디렉터리 구조

```text
safe-wm/
  README.md
  .gitignore

  backend/
    requirements.txt
    app/
      main.py
      core/
        config.py
        logging.py
      api/
        health.py
        customers.py
        analysis.py
      models/
        common.py
        customer.py
        analysis.py
      services/
        customer_repository.py
        data_normalizer.py
        data_validator.py
        risk_context_builder.py
        asset_stability_engine.py
        real_estate_risk.py
        rate_risk.py
        qaima_client.py
        rag_retriever.py
        report_generator.py
        compliance_guard.py
        pb_action.py
      agents/
        workflow.py
        state.py
        preparation_harness.py
        profile_agent.py
        risk_agent.py
        stability_assessment_agent.py
        severity_classification_agent.py
        evidence_reasoning_agent.py
        rag_advisor_agent.py
        compliance_guard_agent.py
        pb_action_agent.py
      data/
        mock_customers.json
        rag_guides.md

  frontend/
    package.json
    index.html
    src/
      main.tsx
      App.tsx
      api/
        wm.ts
      types/
        wm.ts
      pages/
        WmDashboard.tsx
        WmCustomerDetail.tsx
      components/
        RiskBadge.tsx
        ScoreGauge.tsx
        AssetMixPanel.tsx
        CashflowPanel.tsx
        RiskFactorList.tsx
        PbActionPanel.tsx
        CompliancePanel.tsx

  docs/
    policy_v1.md
    real_estate_data_policy.md
    implementation_plan_v1.md
    data_contract_v1.md
    architecture_v1.md
    evaluation_alignment_v1.md
    policies/
      scoring_policy.md
      compliance_guard.md
      risk_explanation_guide.md
      pb_action_policy.md
      customer_explanation_prompt.md
```

## 5. Backend API 계약

### 5.1 Health

```text
GET /api/wm/health
```

Response:

```json
{
  "status": "ok",
  "service": "safe-wm",
  "version": "0.1.0"
}
```

### 5.2 고객 목록

```text
GET /api/wm/customers
```

Response:

```json
{
  "customers": [
    {
      "customerId": "C001",
      "name": "김OO",
      "age": 68,
      "region": "전북 전주",
      "summary": {
        "totalAssets": 900000000,
        "realEstateRatio": 0.72,
        "cashCoverageMonths": 6.4
      },
      "lastAnalysis": {
        "assetStabilityScore": 62,
        "riskGrade": "HIGH",
        "priority": "URGENT",
        "mainRiskFactors": ["REAL_ESTATE_CONCENTRATION", "LIQUIDITY_SHORTAGE"]
      }
    }
  ]
}
```

### 5.3 고객 상세

```text
GET /api/wm/customers/{customer_id}
```

Response:

```json
{
  "customerId": "C001",
  "name": "김OO",
  "age": 68,
  "region": "전북 전주",
  "monthlyIncome": 1200000,
  "monthlyExpense": 2800000,
  "pensionIncome": 900000,
  "cashAssets": 18000000,
  "financialAssets": [],
  "realEstateAssets": [],
  "loans": [],
  "pensionAssets": 70000000,
  "insuranceAssets": 30000000,
  "confirmationItems": []
}
```

### 5.4 고객 분석

```text
POST /api/wm/customers/{customer_id}/analysis
```

Request:

```json
{
  "includeQaima": false,
  "includeDebug": true
}
```

Response:

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
  "debug": {
    "traceId": "trace-...",
    "steps": []
  }
}
```

## 6. 데이터 계약

상세 데이터 계약은 `docs/data_contract_v1.md`에 별도로 둔다.

### 6.1 Customer

필수 필드:

```text
customerId
name
age
region
monthlyIncome
monthlyExpense
pensionIncome
cashAssets
financialAssets
realEstateAssets
loans
pensionAssets
insuranceAssets
confirmationItems
```

### 6.2 데이터 출처 필드

모든 주요 입력 및 산출 데이터는 출처와 신뢰도를 가질 수 있다.

```text
dataSource: MOCK | CUSTOMER_INPUT | PB_INPUT | MYDATA | PUBLIC_API | QAIMA_API | DERIVED
confidence: HIGH | MEDIUM | LOW
needsConfirmation: true | false
```

### 6.3 Risk Factor

```json
{
  "code": "REAL_ESTATE_CONCENTRATION",
  "severity": "HIGH",
  "title": "부동산 편중",
  "description": "총자산 중 부동산 비중이 높습니다.",
  "evidence": [],
  "dataSource": "DERIVED",
  "confidence": "HIGH",
  "needsConfirmation": false
}
```

### 6.4 Score Breakdown

```json
{
  "category": "REAL_ESTATE_CONCENTRATION",
  "baseScore": 100,
  "penalty": 18,
  "resultScore": 82,
  "reason": "부동산 비중이 60%를 초과합니다.",
  "evidence": []
}
```

## 7. 분석 엔진 설계

### 7.1 Analysis & Scoring Workflow

```text
Input Layer
  ↓
Preparation Harness
  - Data Normalizer
  - Data Validator
  - Risk Context Builder
  ↓
QAIMA Context Enrichment Adapter (optional)
  ↓
Enriched Risk Context
  ↓
Multi-Agent Assessment
  ↓
Rule-based Scoring Harness
  ↓
Analysis Output
```

단계별 역할:

```text
Input Layer:
  - Mock/MyData/Public API/QAIMA API/customer input 수집

Preparation Harness:
  - 고객 자산, 부채, 현금흐름, 연금, 부동산 데이터 정규화
  - 누락 필드와 확인 필요 항목 생성
  - risk context 생성

QAIMA Context Enrichment Adapter:
  - QAIMA Feature3/금리 API 응답을 risk context에 보강
  - 점수 계산을 직접 수행하지 않음
  - 실패 시 fallback warning을 남기고 기존 risk context 유지

Multi-Agent Assessment:
  - 위험 항목별 severity 판단
  - evidence 생성
  - 대표 리스크 Top 3 후보 생성

Rule-based Scoring Harness:
  - scoring_policy.md 기준으로 severity를 penalty로 변환
  - Asset Stability Score 계산
  - Risk Grade 산출

Analysis Output:
  - Risk Signals
  - Risk Alert
  - Analysis Evidence
  - Asset Stability Score
  - Risk Grade
```

Preparation Harness 내부 모듈:

```text
Data Normalizer:
  - MyData/Public API/QAIMA/customer input/mock data를 공통 Customer model로 정규화

Data Validator:
  - 필수 필드 누락, 비정상 값, 확인 필요 항목 생성

Risk Context Builder:
  - 총자산, 부동산 비중, 현금 커버 개월 수, 부채비율 등 파생 지표 생성
```

### 7.2 Stability Assessment Agent

Stability Assessment Agent는 고객 데이터를 바탕으로 각 위험 항목의 심각도를 판단한다.

예시:

```text
부동산 위험 = HIGH
현금흐름 위험 = MID
연금 안정성 = LOW
대출/금리 위험 = HIGH
```

Agent는 판단 결과와 함께 evidence를 생성한다.

```json
{
  "riskCategory": "REAL_ESTATE",
  "severity": "HIGH",
  "evidence": [
    "총자산 중 부동산 비중 72%",
    "실거주 부동산으로 유동화 가능성 확인 필요"
  ]
}
```

Multi-Agent Assessment는 내부적으로 다음 agent로 세분화한다.

```text
Stability Assessment Agent:
  - 위험 항목별 안정성 평가

Severity Classification Agent:
  - LOW/MID/HIGH severity 분류

Evidence & Reasoning Agent:
  - severity 판단 근거와 설명 가능한 evidence 생성
```

### 7.3 Rule-based Scoring

```text
Asset Stability Score = 100
- realEstateConcentrationPenalty
- liquidityShortagePenalty
- cashflowDeficitPenalty
- debtInterestRatePenalty
- marketVolatilityPenalty
- pensionShortfallPenalty
```

severity to penalty 기본 정책:

```text
NONE penalty = 0
LOW penalty = 5
MID penalty = 10
HIGH penalty = 20
Score = 100 - totalPenalty
```

`LOW`는 낮지만 존재하는 위험 신호로 5점 penalty를 적용한다. 위험 신호가 관찰되지 않은 항목은 `NONE`으로 분류하고 penalty 0점을 적용한다.

세부 penalty는 `docs/policies/scoring_policy.md`에서 관리한다. Penalty는 항상 양수로 표현하고, 점수 계산은 `100 - totalPenalty`로만 수행한다.

### 7.4 위험 등급

```text
LOW: 80 이상
MID: 60 이상 80 미만
HIGH: 60 미만
```

### 7.5 상담 우선순위

```text
URGENT:
  - riskGrade = HIGH
  - 또는 월 현금흐름 적자 + 현금성 자산 12개월 미만
  - 또는 변동금리 대출 부담이 높음

WATCH:
  - riskGrade = MID
  - 또는 단일 주요 리스크가 HIGH

NORMAL:
  - riskGrade = LOW
```

## 7.6 LLM 사용 원칙

LLM은 판단 주체가 아니라 설명 생성 계층으로 사용한다.

LLM 역할:

```text
- PB 설명 생성
- 고객 설명 생성
- 쉬운 자연어 변환
- RAG 기반 설명 생성
```

금지:

```text
- 투자 추천
- 확정 표현
- 자산 운용 판단
- 수익 보장 표현
```

## 7.7 md 기반 정책 구조

정책과 프롬프트는 코드에 하드코딩하지 않고 markdown 정책 파일로 분리한다.

```text
docs/policies/scoring_policy.md
docs/policies/compliance_guard.md
docs/policies/risk_explanation_guide.md
docs/policies/pb_action_policy.md
docs/policies/customer_explanation_prompt.md
```

목적:

```text
scoring_policy.md:
  - severity -> penalty 변환
  - Risk Grade 기준

compliance_guard.md:
  - 금지 표현
  - 허용 표현
  - 재생성 조건

risk_explanation_guide.md:
  - 고객 친화 설명 기준
  - 고령 고객 설명 원칙

pb_action_policy.md:
  - 상담 우선순위 정책
  - 후속 액션 템플릿

customer_explanation_prompt.md:
  - 고객용 설명 prompt template
```

## 8. QAIMA Adapter 설계

### 8.1 원칙

- QAIMA 내부 코드를 import하지 않는다.
- QAIMA API 응답 계약은 참고만 한다.
- HTTP 호출 실패는 SAFE-WM 전체 실패로 전파하지 않는다.
- 실패 시 warning을 남기고 mock fallback을 사용한다.
- QAIMA는 scoring engine이 아니라 context enrichment adapter로만 사용한다.
- QAIMA 응답은 `risk_context_builder -> qaima_client enrich optional -> enriched_risk_context` 흐름에서만 반영한다.

### 8.2 환경변수

```text
SAFE_WM_QAIMA_BASE_URL=http://localhost:8080
SAFE_WM_ENABLE_QAIMA=false
SAFE_WM_QAIMA_TIMEOUT_SECONDS=5
```

### 8.3 참고 API

```text
POST /api/v1/feature3/analysis
GET  /api/v1/feature2/cards/base-rate
GET  /api/v1/feature2/cards/macro-rates
GET  /api/v1/feature2/cards/macro-rates-series
```

### 8.4 실패 처리

```text
QAIMA_TIMEOUT
QAIMA_CONNECTION_FAILED
QAIMA_INVALID_RESPONSE
QAIMA_DISABLED
```

각 실패는 `warnings[]`와 debug step에 기록한다.

## 9. 에러 및 디버그 로그 설계

SAFE-WM은 구현 초기부터 디버그 가능성을 확보한다. 모든 분석 요청은 `traceId`를 생성하고, 주요 단계별 로그를 남긴다.

### 9.1 로그 원칙

- API 요청 시작/종료 로그를 남긴다.
- 분석 단계별 입력 요약과 산출 요약을 남긴다.
- 외부 API 호출은 요청 대상, timeout, status, fallback 여부를 남긴다.
- 생성된 PB/고객 설명은 score, evidence, compliance result와 함께 audit log로 남긴다.
- 개인정보성 상세 값은 로그에 직접 남기지 않고 요약값만 남긴다.
- 에러 발생 시 traceId로 프론트와 백엔드 로그를 연결한다.

### 9.2 로그 포맷

```json
{
  "ts": "2026-06-15T10:20:30+09:00",
  "level": "INFO",
  "service": "safe-wm",
  "traceId": "trace-abc",
  "customerId": "C001",
  "step": "asset_stability_engine",
  "event": "completed",
  "message": "asset stability score calculated",
  "metadata": {
    "score": 62,
    "riskGrade": "HIGH",
    "riskFactorCount": 3
  }
}
```

### 9.3 분석 단계 Debug Step

API 응답의 `debug.steps`는 `includeDebug=true`일 때만 반환한다.

```json
{
  "step": "qaima_macro_rates",
  "status": "FALLBACK",
  "startedAt": "2026-06-15T10:20:30+09:00",
  "endedAt": "2026-06-15T10:20:31+09:00",
  "durationMs": 850,
  "message": "QAIMA macro-rates unavailable. fallback used.",
  "warnings": ["QAIMA_CONNECTION_FAILED"]
}
```

### 9.4 주요 단계

```text
request_received
customer_loaded
data_normalizer
data_validator
risk_context_builder
qaima_market_risk
qaima_macro_rates
real_estate_risk
rate_risk
asset_stability_engine
stability_assessment_agent
severity_classification_agent
evidence_reasoning_agent
rag_context_layer
report_generator
compliance_guard
pb_action
audit_log_written
response_completed
```

### 9.5 Audit Log

Audit Log는 MVP에서는 JSONL 또는 application log로 시작한다. 실제 서비스 전환 시에는 DB 또는 감사로그 저장소로 분리한다.

기록 대상:

```text
- traceId
- customerId
- analysisId
- Asset Stability Score
- Risk Grade
- Risk Alert
- Analysis Evidence summary
- agentTrace
- compliance result
- generated output summary
- fallback warnings
```

민감한 상세 개인정보와 원문 금융거래 내역은 audit log에 남기지 않는다.

### 9.6 에러 응답

```json
{
  "error": {
    "code": "CUSTOMER_NOT_FOUND",
    "message": "Customer not found.",
    "traceId": "trace-abc"
  }
}
```

대표 에러 코드:

```text
CUSTOMER_NOT_FOUND
CUSTOMER_DATA_INVALID
ANALYSIS_FAILED
QAIMA_CONNECTION_FAILED
QAIMA_TIMEOUT
REPORT_GENERATION_FAILED
COMPLIANCE_CHECK_FAILED
```

## 10. Frontend 설계

### 10.1 화면

```text
/
  PB Dashboard
  - 고객 리스트
  - 안정성 점수
  - 위험 등급
  - 상담 우선순위
  - 주요 리스크

/customers/:customerId
  Customer Detail
  - 고객 요약
  - Asset Stability Score
  - 자산 구성
  - 현금흐름
  - 부동산 리스크
  - 대출/금리 리스크
  - 시장 리스크
  - 고객용 설명
  - PB 상담 액션
  - Compliance 결과
  - Debug traceId
```

### 10.2 UI 원칙

- 랜딩 페이지를 만들지 않고 PB Dashboard를 첫 화면으로 둔다.
- 금융 업무 도구처럼 조용하고 정보 밀도 높은 UI로 구성한다.
- HIGH 위험 고객이 먼저 보이도록 정렬한다.
- 각 점수와 리스크에는 근거를 표시한다.
- 에러 발생 시 traceId와 함께 확인 가능한 메시지를 보여준다.

## 11. LangGraph Multi-Agent Workflow

### 11.1 Workflow

```text
START
  ↓
Profile Agent
  ↓
Risk Analysis Agent
  ↓
RAG Advisor Agent
  ↓
Compliance Guard Agent
  ├─ PASS → PB Action Agent
  └─ FAIL → RAG Advisor Agent 재생성
  ↓
END
```

### 11.2 Agent 역할

Preparation Harness:

- Data Normalizer 실행
- Data Validator 실행
- Risk Context Builder 실행
- Agent workflow가 사용할 표준 state 생성

Profile Agent:

- 고객 자산/부채/현금흐름 데이터 정리
- 누락 데이터 확인 항목 생성
- 직접 입력, mock, QAIMA, 공개 API 등 데이터 출처 정규화

Risk Analysis Agent:

- Asset Stability Score 계산
- 주요 위험 요인 Top 3 도출
- QAIMA 시장 리스크 반영
- 부동산, 대출/금리, 현금흐름, 연금 리스크를 통합

Stability Assessment Agent:

- 자산 집중도, 현금흐름, 연금, 부동산, 긴급 유동성, 생애주기, 대출/금리 부담을 평가

Severity Classification Agent:

- 각 risk item을 LOW/MID/HIGH로 분류
- scoring policy에 넘길 severity map 생성

Evidence & Reasoning Agent:

- severity별 판단 근거 생성
- Risk Alert와 Analysis Evidence 후보 생성

RAG Advisor Agent:

- `rag_guides.md`에서 고령층 상담 가이드 검색
- 고객용 설명 초안 생성
- PB용 상담 초안 생성
- LLM 사용 시 검색 근거를 함께 전달

Compliance Guard Agent:

- 투자권유 표현 탐지
- 확정적 표현 탐지
- 개인정보 노출 점검
- 실패 시 재생성 요청

PB Action Agent:

- 상담 우선순위 결정
- 후속 액션 생성
- PB 확인 필요 항목과 상담 질문 생성

### 11.2.1 Analysis & Scoring Workflow와 LLM & Compliance Workflow 분리

PPT와 제안서 가독성을 위해 workflow는 2개 흐름으로 설명한다.

Analysis & Scoring Workflow:

```text
Input Layer
  -> Preparation Harness
  -> Multi-Agent Assessment
  -> Rule-based Scoring Harness
```

LLM & Compliance Workflow:

```text
RAG Context
  -> Explanation Generator
  -> Compliance Guard
  -> Loop Harness
  -> PB Action Generator
```

실제 구현에서는 하나의 LangGraph workflow 안에서 위 두 흐름을 순차 실행한다.

Service output:

```text
PB Dashboard
Customer View
Audit Log
```

구현 원칙:

```text
- LLM이 최종 점수를 계산하지 않는다.
- LLM은 해석, 요약, 설명만 수행한다.
- 최종 점수는 scoring_policy.md를 따르는 Rule-based Scoring Harness가 산출한다.
- Compliance Guard는 PB 또는 고객에게 생성 설명이 노출되기 전에 반드시 실행된다.
- 모든 생성 결과는 score, evidence, compliance result와 함께 로그에 기록한다.
```

### 11.3 RAG 설계

MVP RAG는 `rag_guides.md` 기반의 simple keyword retrieval로 시작한다.

```text
rag_retriever.py
  -> query 생성
  -> rag_guides.md section 검색
  -> 관련 guide snippet 반환
  -> RAG Advisor Agent가 설명 생성에 사용
```

고도화 시 Chroma를 선택적으로 붙인다.

```text
Markdown guide
  -> chunking
  -> embedding
  -> Chroma
  -> RAG Advisor Agent
```

### 11.4 Agent Trace

모든 agent 실행 결과는 `agentTrace`에 기록한다. `agentTrace`는 평가와 디버깅을 위해 기본 응답에 포함할 수 있으며, 민감한 입력 원문은 포함하지 않는다.

```json
[
  {
    "agent": "ProfileAgent",
    "status": "SUCCESS",
    "summary": "고객 자산/부채/현금흐름 프로필 정리 완료"
  },
  {
    "agent": "ComplianceGuardAgent",
    "status": "RETRY",
    "summary": "확정 표현 감지로 RAG Advisor Agent 재생성 요청"
  }
]
```

## 12. 구현 순서

1. Backend FastAPI 실행 골격
2. health API
3. mock customer data
4. customer repository
5. customers API
6. customer detail API
7. data models
8. asset stability rule engine
9. real estate risk module
10. rate risk module
11. report generator
12. compliance guard
13. PB action generator
14. RAG retriever
15. LangGraph state and agents
16. analysis API
17. debug trace/logging middleware
18. QAIMA adapter with fallback
19. Frontend Vite React setup
20. wm API client
21. PB Dashboard
22. Customer Detail
23. QAIMA off fallback verification
24. README and docs update

## 13. MVP 완료 기준

- `safe-wm` backend가 port 8100에서 실행된다.
- `safe-wm` frontend가 port 5174에서 실행된다.
- 고객 목록과 고객 상세가 표시된다.
- 고객별 Asset Stability Score가 계산된다.
- HIGH/MID/LOW 위험 등급이 명확히 구분된다.
- PB Dashboard에서 상담 우선순위를 볼 수 있다.
- 고객 상세에서 점수 근거와 고객용 설명을 볼 수 있다.
- Compliance Guard 결과가 표시된다.
- LangGraph Multi-Agent Workflow가 판단-행동-검증/개선 구조로 동작한다.
- RAG Advisor Agent가 `rag_guides.md` 검색 결과를 기반으로 설명 초안을 생성한다.
- `agentTrace`에서 Profile, Risk, RAG Advisor, Compliance Guard, PB Action Agent 실행 결과를 확인할 수 있다.
- includeDebug=false일 때 `debug.steps`는 응답에서 제외된다.
- includeDebug=true일 때 `debug.steps`가 응답에 포함된다.
- 금지 표현이 포함된 설명은 Compliance Guard에서 FAIL 처리된 뒤 safe explanation으로 재생성된다.
- QAIMA 미실행 시에도 fallback warning과 함께 분석이 완료된다.
- 분석 에러 또는 외부 API 실패 시 traceId와 debug log로 원인을 추적할 수 있다.
- 모든 구현 파일은 `safe-wm/` 내부에만 존재한다.
