# fetch_financials_dart.py
import requests
import pandas as pd

API_KEY = "473c6242d994f118bab8535535cbaab46693005c"

# DART account_id → 우리가 쓸 컬럼 이름 매핑
ACCOUNT_MAP = {
    "ifrs-full_Revenue": "revenue",
    "dart_OperatingIncomeLoss": "operating_income",
    "ifrs-full_ProfitLoss": "net_income",
    "ifrs-full_Assets": "assets",
    "ifrs-full_Liabilities": "liabilities",
    "ifrs-full_Equity": "equity",

    # 아래 세 개는 응답 확인 후 account_id 를 맞춰야 함 (예시는 typical 값)
    "ifrs-full_GrossProfit": "gross_profit",
    "ifrs-full_RetainedEarnings": "retained_earnings",
    "ifrs-full_CashAndCashEquivalents": "cash_and_equivalents",
}

def safe_float(val: str):
    """문자열 금액(콤마 포함)을 float로 변환. 빈 문자열/None 이면 None."""
    if val is None:
        return None
    val = str(val).strip()
    if not val:
        return None
    try:
        return float(val.replace(",", ""))
    except ValueError:
        return None

# >>> report_date를 DART 응답에서 추출하는 유틸
def extract_report_date(df: pd.DataFrame, year: int) -> str:
    """
    DART 재무제표 응답에서 thstrm_dt(당기 기준일)를 찾아
    'YYYY-MM-DD' 형식 문자열로 반환.
    없으면 연말 기준 'YYYY-12-31'을 반환.
    """
    col_candidates = ["thstrm_dt", "thstrm_nm"]  # 혹시 모를 컬럼명 변화 대비
    dt_str = None

    for col in col_candidates:
        if col in df.columns:
            # 첫 번째 비어 있지 않은 값 사용
            series = df[col].dropna().astype(str).str.strip()
            if not series.empty:
                dt_str = series.iloc[0]
                break

    if not dt_str:
        # 안전장치: 그래도 없으면 연말 기준
        return f"{year}-12-31"

    # 보통 '2023.12.31' 형태라 가정
    dt_str = dt_str.replace(".", "-")
    # 길이 맞추기 (예외 방지용)
    # '2023-12-31' 또는 '2023-12' 등 들어올 수 있음
    parts = dt_str.split("-")
    if len(parts) == 3:
        yyyy, mm, dd = parts
    elif len(parts) == 2:
        yyyy, mm = parts
        dd = "31"
    else:
        # 이상한 값이면 그냥 year-12-31
        return f"{year}-12-31"

    yyyy = yyyy.zfill(4)
    mm = mm.zfill(2)
    dd = dd.zfill(2)
    return f"{yyyy}-{mm}-{dd}"

def fetch_finstmt_single_company(corp_code: str, year: int) -> pd.DataFrame:
    url = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json"
    params = {
        "crtfc_key": API_KEY,
        "corp_code": corp_code,
        "bsns_year": year,
        "reprt_code": "11011",  # 사업보고서(연간)
        "fs_div": "CFS",        # 연결 기준
    }
    resp = requests.get(url, params=params)
    resp.raise_for_status()
    data = resp.json()

    if data.get("status") != "000":
        raise RuntimeError(f"DART error {data.get('status')}: {data.get('message')}")

    return pd.DataFrame(data["list"])

def build_financial_row(corp_code: str, stock_code: str, year: int) -> dict:
    df = fetch_finstmt_single_company(corp_code, year)

    # >>> report_date 계산
    report_date = extract_report_date(df, year)

    # 결과 row 기본 구조 (엔티티/DTO 와 맞춤)
    row = {
        "stock_code": str(stock_code).zfill(6),  # 5930 -> "005930" 으로 zero padding
        "report_date": report_date,              # >>> 신규 필드
        "version": 1,                            # int version (NOT NULL, 기본값 1로 넣기)
        "fiscal_year": year,
        "fiscal_quarter": None,             # 연간 데이터이므로 null
        "period_type": "A",                 # Annual
        "currency": "KRW",
        "source": "DART_FNLTT_SINGL",       # 나중에 구분용
    }

    # 1) DART 계정 → 금액 매핑
    for account_id, col_name in ACCOUNT_MAP.items():
        sub = df[df["account_id"] == account_id]
        if not sub.empty:
            val_str = sub.iloc[0]["thstrm_amount"]
            row[col_name] = safe_float(val_str)
        else:
            row[col_name] = None

    # 2) 파생 지표 계산
    rev = row.get("revenue")
    op = row.get("operating_income")
    net = row.get("net_income")
    eq = row.get("equity")

    # 영업이익률 = 영업이익 / 매출액
    if rev not in (None, 0) and op is not None:
        row["operating_margin"] = op / rev
    else:
        row["operating_margin"] = None

    # 순이익률 = 당기순이익 / 매출액
    if rev not in (None, 0) and net is not None:
        row["net_margin"] = net / rev
    else:
        row["net_margin"] = None

    # ROE = 당기순이익 / 자본
    if eq not in (None, 0) and net is not None:
        row["roe"] = net / eq
    else:
        row["roe"] = None

    # 3) 아직 DART에서 바로 못 채우는 필드들 (나중에 KIS/시세 API에서 채울 예정)
    row.setdefault("capital_stock", None)       # 상장주식수
    row.setdefault("market_cap", None)          # 시가총액
    row.setdefault("per", None)
    row.setdefault("pbr", None)

    return row

def fetch_years_for_company(corp_code: str, stock_code: str, from_year: int, to_year: int) -> pd.DataFrame:
    rows = []
    for year in range(from_year, to_year + 1):
        try:
            r = build_financial_row(corp_code, stock_code, year)
            rows.append(r)
        except Exception as e:
            print(f"[WARN] {stock_code} {year} 수집 실패: {e}")
    df = pd.DataFrame(rows)

    df["stock_code"] = df["stock_code"].astype(str).str.zfill(6)

    # 컬럼 순서를 Financial 엔티티에 최대한 맞춰 정렬
    cols = [
        "stock_code",
        "report_date",      # >>> 신규 필드
        "version",
        "fiscal_year",
        "fiscal_quarter",
        "period_type",
        "currency",
        "source",
        "revenue",
        "gross_profit",
        "operating_income",
        "net_income",
        "assets",
        "liabilities",
        "equity",
        "capital_stock",
        "retained_earnings",
        "cash_and_equivalents",
        "market_cap",
        "operating_margin",
        "net_margin",
        "roe",
        "per",
        "pbr",
    ]
    # 실제 존재하는 컬럼만 남기기 (혹시 위 목록 중 일부 못 채웠어도 에러 안 나게)
    cols = [c for c in cols if c in df.columns]
    return df[cols]

if __name__ == "__main__":
    # 예시: 삼성전자
    corp_code = "00126380"   # corp_codes_kr.csv 에서 005930 에 해당하는 corp_code
    stock_code = "005930"

    df = fetch_years_for_company(corp_code, stock_code, 2019, 2023)
    print(df)
    df.to_csv("financial_005930_2019_2023.csv", index=False, encoding="utf-8")
