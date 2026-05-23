import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { bootstrapAccessToken } from "../api/tokenStore";

const API_BASE_URL = "http://localhost:8080/api/v1";

export default function OAuth2SuccessPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState("");

  useEffect(() => {
    const status = searchParams.get("status");
    if (status === "error") {
      const code = searchParams.get("code") ?? "OAUTH2_AUTHENTICATION_FAILED";
      const provider = searchParams.get("provider") ?? "social";
      navigate(`/login?status=error&provider=${provider}&code=${code}`, {
        replace: true,
      });
      return;
    }

    const handle = async () => {
      try {
        const ok = await bootstrapAccessToken(API_BASE_URL);
        if (!ok) {
          throw new Error("인증 정보를 받아오지 못했습니다.");
        }
        if (searchParams.get("profileRequired") === "true") {
          navigate("/signup/social-complete", { replace: true });
          return;
        }
        const redirect =
          sessionStorage.getItem("qaima_redirect") || "/feature/1";
        sessionStorage.removeItem("qaima_redirect");
        navigate(redirect, { replace: true });
      } catch {
        setError("로그인 처리에 실패했습니다. 다시 시도해주세요.");
        setTimeout(() => navigate("/login", { replace: true }), 1800);
      }
    };
    handle();
  }, [navigate, searchParams]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg ml-[84px]">
      <div className="bg-surface border border-line rounded-2xl shadow-card px-10 py-8 text-center">
        {error ? (
          <p className="text-sm text-danger">{error}</p>
        ) : (
          <>
            <p className="text-sm font-medium text-ink animate-pulse">
              로그인 처리 중...
            </p>
            <p className="mt-1 text-xs text-ink-3">잠시만 기다려주세요.</p>
          </>
        )}
      </div>
    </div>
  );
}
