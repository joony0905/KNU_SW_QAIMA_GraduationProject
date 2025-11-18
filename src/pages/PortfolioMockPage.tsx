// src/pages/PortfolioMockPage.tsx
import CrudTable from "../components/CrudTable";

export default function PortfolioMockPage() {
  const sample = [
    { id: 1, name: "005930.KS (삼성전자)" },
    { id: 2, name: "TSLA" },
    { id: 3, name: "NVDA" },
  ];

  return (
    <div style={{ display: "grid", gap: "16px" }}>
      <h1 style={{ fontSize: "1.25rem", fontWeight: 600 }}>
        기능3 포트폴리오(Mock)
      </h1>
      <p>
        좌측 테이블은 현재 보유 종목을 간단히 입력하는 영역이고, 우측(아래) 영역에는
        나중에 GPT 분석 결과를 붙일 예정이다.
      </p>

      <div
        style={{
          display: "grid",
          gap: "16px",
          gridTemplateColumns: "1.2fr 0.8fr",
        }}
      >
        <CrudTable title="보유종목" initialRows={sample} />

        <div
          style={{
            background: "white",
            borderRadius: "8px",
            padding: "12px",
            boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
            minHeight: "120px",
          }}
        >
          <h2 style={{ fontWeight: 600, marginBottom: "8px" }}>
            분석 결과 (Mock)
          </h2>
          <p style={{ fontSize: "0.85rem", color: "#64748b" }}>
            실제로는 /api/v1/portfolio/analyze 로 포트폴리오 JSON을 보내고,
            백엔드가 Python FastAPI 거쳐서 분석 텍스트를 내려주는 흐름이다.
            지금은 목업이므로 고정 문장을 보여준다.
          </p>
          <ul style={{ marginTop: "8px", paddingLeft: "18px", fontSize: "0.85rem" }}>
            <li>삼성전자 비중이 높습니다. 산업군 분산을 검토하세요.</li>
            <li>미국 성장주 비중이 있는 상태입니다.</li>
            <li>성향 설문에 따라 리밸런싱 문구를 다르게 노출할 수 있습니다.</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
