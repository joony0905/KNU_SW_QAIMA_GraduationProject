# SAFE-WM 시스템 아키텍처 v1

## 1. 전체 구조

```text
Browser
  |
  v
SAFE-WM Frontend
  |
  v
SAFE-WM Backend
  |
  +-- Mock Data Store
  +-- Rule Engine
  +-- QAIMA HTTP Adapter
  +-- RAG Store
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
  +-- Audit Log
```

## 2. 런타임 포트

```text
SAFE-WM Backend:  8100
SAFE-WM Frontend: 5174
QAIMA Spring:     8080
QAIMA FastAPI:    8000
```

## 3. 요청 흐름

```text
Frontend
  -> POST /api/wm/customers/{customer_id}/analysis
  -> Backend creates traceId
  -> Load customer
  -> Optional QAIMA adapter calls
  -> LangGraph Multi-Agent Workflow
     -> Preparation Harness
     -> Profile Agent
     -> Risk Analysis Agent
     -> Stability Assessment Agent
     -> Severity Classification Agent
     -> Evidence & Reasoning Agent
     -> RAG Advisor Agent
     -> Compliance Guard Agent
        -> PASS: PB Action Agent
        -> FAIL: RAG Advisor Agent retry
  -> Audit Log
  -> Return response with warnings/agentTrace/debug
```

## 4. LangGraph Multi-Agent Layer

```text
START
  ↓
Preparation Harness
  ↓
Profile Agent
  ↓
Risk Analysis Agent
  ↓
Stability Assessment Agent
  ↓
Severity Classification Agent
  ↓
Evidence & Reasoning Agent
  ↓
RAG Advisor Agent
  ↓
Compliance Guard Agent
  ├─ PASS → PB Action Agent
  └─ FAIL → RAG Advisor Agent 재생성
  ↓
END
```

Agent responsibilities:

```text
Preparation Harness:
  - Data Normalizer, Data Validator, Risk Context Builder 실행
  - Agent workflow 표준 state 생성

Profile Agent:
  - 고객 자산/부채/현금흐름 데이터 정리
  - 누락 데이터 확인 항목 생성

Risk Analysis Agent:
  - Asset Stability Score 계산
  - 주요 위험 요인 Top 3 도출
  - QAIMA 시장 리스크 반영

Stability Assessment Agent:
  - 위험 항목별 안정성 평가

Severity Classification Agent:
  - LOW/MID/HIGH severity 분류

Evidence & Reasoning Agent:
  - Risk Alert와 Analysis Evidence 후보 생성

RAG Advisor Agent:
  - rag_guides.md에서 고령층 상담 가이드 검색
  - 고객용/PB용 설명 초안 생성

Compliance Guard Agent:
  - 투자권유 표현 탐지
  - 확정적 표현 탐지
  - 개인정보 노출 점검
  - 실패 시 재생성 요청

PB Action Agent:
  - 상담 우선순위 결정
  - 후속 액션 생성
```

## 5. RAG Store

MVP RAG는 markdown 기반 simple keyword retrieval로 시작한다.

```text
rag_guides.md
  -> rag_retriever.py
  -> RAG Advisor Agent
```

고도화 시 Chroma vector store를 선택적으로 붙인다.

```text
rag_guides.md
  -> chunks
  -> embeddings
  -> Chroma
  -> RAG Advisor Agent
```

## 6. AI Agent 공통요건 대응

```text
판단:
  고객 자산, 부채, 현금흐름, 시장 데이터를 해석하여 자산 안정성 점수와 위험 우선순위를 도출한다.

행동:
  위험 등급에 따라 고객용 설명, PB 상담 요약, 확인 필요 항목, 상담 우선순위를 생성한다.

검증/개선:
  Compliance Guard Agent가 생성 결과를 검토한다.
  투자권유 표현, 확정 표현, 개인정보 노출 위험이 발견되면 RAG Advisor Agent로 되돌려 문구를 재생성한다.
```

## 7. 외부 연동

SAFE-WM은 QAIMA 내부 코드를 import하지 않는다. QAIMA 연동은 HTTP adapter 한 곳에서만 수행한다.

```text
qaima_client.py
  GET  /api/v1/feature2/cards/base-rate
  GET  /api/v1/feature2/cards/macro-rates
POST /api/v1/feature3/analysis
```

## 8. Fallback 원칙

```text
QAIMA enabled + success
  -> QAIMA data used for context enrichment only

QAIMA disabled
  -> fallback data used
  -> warning: QAIMA_DISABLED

QAIMA timeout/failure
  -> fallback data used
  -> warning: QAIMA_CONNECTION_FAILED or QAIMA_TIMEOUT
```

QAIMA는 SAFE-WM score를 직접 계산하지 않는다. QAIMA Feature3/금리 응답은 risk context를 보강하는 enrichment signal로만 사용한다.

## 9. 디버그 추적

모든 분석 요청에는 `traceId`를 부여한다.

```text
traceId
  -> backend logs
  -> agentTrace
  -> debug.steps
  -> frontend error panel
```

프론트는 에러 발생 시 사용자에게 traceId를 보여준다. 백엔드는 같은 traceId로 각 단계의 로그를 남긴다.

## 10. Audit Log

SAFE-WM은 PB/고객에게 생성 설명을 보여주기 전에 분석 및 검증 결과를 감사 가능한 형태로 기록한다.

기록 대상:

```text
traceId
customerId
analysisId
Asset Stability Score
Risk Grade
Risk Alert
Analysis Evidence summary
agentTrace
compliance result
generated output summary
fallback warnings
```

MVP에서는 application log 또는 JSONL로 시작하고, 실제 서비스 전환 시 별도 감사로그 저장소로 분리한다.

## 11. 보안 및 개인정보 원칙

- MVP는 Mock 데이터만 사용한다.
- 로그에는 주민번호, 주소 상세, 계좌번호 등 민감 정보를 남기지 않는다.
- 고객명은 시연 데이터에서 마스킹한다.
- 실제 서비스 전환 시 마이데이터 동의, 접근권한, 감사로그 설계를 별도 적용한다.
