import { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function LoginPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      if (!form.email || !form.password) {
        throw new Error("이메일과 비밀번호를 입력하세요.");
      }
      // 실제 로그인 API 붙이면 여기서 호출
      localStorage.setItem("qaima_token", "mock-token");
      navigate("/ping");
    } catch (err: any) {
      setError(err.message ?? "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        background: "#0f172a",
        alignItems: "center",
        justifyContent: "center",
        padding: "16px",
      }}
    >
      <div
        style={{
          background: "white",
          width: "100%",
          maxWidth: "360px",
          borderRadius: "10px",
          padding: "24px",
          boxShadow: "0 12px 30px rgba(0,0,0,0.25)",
        }}
      >
        <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "4px" }}>
          QAIMA 로그인
        </h1>
        <p style={{ color: "#64748b", marginBottom: "20px", fontSize: "0.875rem" }}>
          팀 계정으로 접속해 주세요.
        </p>

        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          <div>
            <label style={{ display: "block", marginBottom: "4px", fontSize: "0.8rem" }}>이메일</label>
            <input
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              style={{ width: "100%", padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: "6px" }}
            />
          </div>
          <div>
            <label style={{ display: "block", marginBottom: "4px", fontSize: "0.8rem" }}>비밀번호</label>
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              style={{ width: "100%", padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: "6px" }}
            />
          </div>

          {error && <div style={{ color: "#b91c1c", fontSize: "0.8rem" }}>{error}</div>}

          <button
            type="submit"
            disabled={loading}
            style={{
              background: "#0f172a",
              color: "white",
              border: "none",
              borderRadius: "6px",
              padding: "8px 12px",
              cursor: "pointer",
            }}
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>
      </div>
    </div>
  );
}
