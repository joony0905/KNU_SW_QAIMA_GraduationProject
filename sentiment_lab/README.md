# sentiment-lab

- `sentiment-lab`은 QAIMA 운영 코드와 분리된 뉴스 감성 모델 실험/학습용 sandbox입니다.  
실험 코드, 데이터 전처리, 학습, 평가, 단건 추론 테스트를 독립적으로 수행하고, 나중에 검증된 모델과 최소 추론 모듈만 `qaima/analysis/`로 옮기는 것을 전제로 합니다.

## 출처
- huggingface에 공유된 카카오뱅크 KF-DeBERTa 모델
- github에 공개된 finance_sentiment_corpus를 1차 파인튜닝으로 사용
- 이후 실제 뉴스데이터 300개로 2차 파인튜닝 진행 예정 
- https://huggingface.co/kakaobank/kf-deberta-base
- https://github.com/ukairia777/finance_sentiment_corpus

## 원칙

- `qaima/analysis/`와 분리된 독립 실험 공간
- `pathlib.Path` 기반 경로 관리
- Windows 환경
- 현재는 운영 FastAPI 연동 없이 실험 코드만 포함

## 디렉토리 구조

```text
sentiment-lab/
  README.md
  requirements.txt
  .gitignore
  config/
    __init__.py
    paths.py
    labels.py
  data/
    raw/
    interim/
    processed/
  models/
    .gitkeep
  outputs/
    eval/
    logs/
    predictions/
  scripts/
    __init__.py
    prepare_finance_corpus.py
    prepare_real_news.py
  src/
    __init__.py
    dataset.py
    preprocess.py
    train.py
    evaluate.py
    inference_test.py
    utils.py
```

## 파일 역할

- `config/paths.py`: 프로젝트 루트, 데이터, 모델, 출력 경로 관리
- `config/labels.py`: 감성 라벨 및 매핑 정의
- `scripts/prepare_finance_corpus.py`: 금융 감성 코퍼스 정제용 스크립트 뼈대
- `scripts/prepare_real_news.py`: 실제 뉴스 샘플 전처리 스크립트 뼈대
- `src/preprocess.py`: 모델 입력 텍스트 조합 함수
- `src/dataset.py`: 데이터프레임 컬럼 검증 유틸
- `src/train.py`: 모델/토크나이저 로드 중심의 학습 진입점 뼈대
- `src/evaluate.py`: 평가 확장용 함수 뼈대
- `src/inference_test.py`: 단건 추론 테스트 실행 코드
- `src/utils.py`: 시드 고정, JSON 저장 헬퍼

## 실행 순서 예시

예시:
- `python -m venv .venv`
- `.venv\Scripts\activate`
- `pip install -r requirements.txt`
- `python -m src.inference_test`

추천 흐름:
- 원본 데이터 배치: `data/raw/`
- 전처리 스크립트 작성/실행: `python -m scripts.prepare_finance_corpus`
- 실험용 학습 진입점 확인: `python -m src.train`
- 단건 추론 확인: `python -m src.inference_test`

## 참고

- 현재 스켈레톤은 더미 데이터 없이도 import 및 실행 진입점 확인이 가능하도록 작성되어 있습니다.
- 실제 모델 다운로드와 가중치 로드는 네트워크 환경과 로컬 캐시에 따라 시간이 걸릴 수 있습니다.
