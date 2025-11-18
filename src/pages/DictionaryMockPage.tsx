// src/pages/DictionaryMockPage.tsx
export default function DictionaryMockPage() {
  const terms = [
    { key: "PER", desc: "주가수익비율. Price / Earnings" },
    { key: "PBR", desc: "주가순자산비율. Price / Book Value" },
    { key: "ROE", desc: "자기자본이익률. Net Income / Equity" },
    { key: "Beta", desc: "시장 대비 민감도 지표" },
    { key: "Dividend Yield", desc: "배당수익률. Dividend / Price" },
  ];

  return (
    <div style={{ display: "grid", gap: "16px" }}>
      <h1 style={{ fontSize: "1.25rem", fontWeight: 600 }}>
        기능4 사전(Mock)
      </h1>
      <p>
        실제 QAIMA에서는 분석 텍스트 안에 용어가 등장하면 이 사전과 매핑해서
        모달로 띄운다. 지금은 목록 형태만 보여준다.
      </p>

      <div
        style={{
          background: "white",
          borderRadius: "8px",
          padding: "12px",
          boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
        }}
      >
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #e2e8f0", textAlign: "left" }}>
              <th style={{ padding: "6px" }}>용어</th>
              <th style={{ padding: "6px" }}>설명</th>
            </tr>
          </thead>
          <tbody>
            {terms.map((t) => (
              <tr key={t.key} style={{ borderBottom: "1px solid #e2e8f0" }}>
                <td style={{ padding: "6px", fontWeight: 600 }}>{t.key}</td>
                <td style={{ padding: "6px" }}>{t.desc}</td>
              </tr>
            ))}
            {terms.length === 0 && (
              <tr>
                <td colSpan={2} style={{ padding: "6px", color: "#94a3b8" }}>
                  등록된 용어가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div
        style={{
          background: "#e2e8f0",
          padding: "10px 12px",
          borderRadius: "6px",
          fontSize: "0.8rem",
          color: "#0f172a",
        }}
      >
        후속 작업: 이 페이지에서 용어를 선택하면
        /api/v1/dict/{`{term}`} 을 호출해서 상세 설명, 예시, 관련 지표까지
        불러오도록 확장할 수 있다.
      </div>
    </div>
  );
}
