import { useState } from "react";
import api from "../lib/apiClient";
import ApiResultBox from "../components/ApiResultBox";

export default function PingPage() {
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const handlePing = async () => {
    setLoading(true);
    setErr("");
    try {
      const res = await api.get("/api/v1/ping");
      setResult(res);
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "12px" }}>
        API 응답 테스트
      </h1>
      <p style={{ marginBottom: "12px" }}>
        /api/v1/ping 을 호출해 Spring Boot 서버가 살아있는지 확인합니다.
      </p>
      <button
        onClick={handlePing}
        style={{
          background: "#0f172a",
          color: "white",
          border: "none",
          borderRadius: "6px",
          padding: "8px 14px",
          cursor: "pointer",
          marginBottom: "16px",
        }}
      >
        /api/v1/ping 호출
      </button>

      <ApiResultBox loading={loading} error={err} data={result} />
    </div>
  );
}
