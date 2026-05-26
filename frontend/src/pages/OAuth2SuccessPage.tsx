import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { bootstrapAccessToken } from "../api/tokenStore";

const API_BASE_URL = "/api/v1";

export default function OAuth2SuccessPage() {
  const navigate = useNavigate();
  const { t } = useTranslation("oauthPage");
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
          throw new Error(t("callback.noToken"));
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
        setError(t("callback.failed"));
        setTimeout(() => navigate("/login", { replace: true }), 1800);
      }
    };
    handle();
    // t 는 의존성에 넣지 않음 — 언어 변경 시 다시 부트스트랩하면 안 됨
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate, searchParams]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg md:ml-[84px]">
      <div className="bg-surface border border-line rounded-2xl shadow-card px-10 py-8 text-center">
        {error ? (
          <p className="text-sm text-danger">{error}</p>
        ) : (
          <>
            <p className="text-sm font-medium text-ink animate-pulse">
              {t("callback.loading")}
            </p>
            <p className="mt-1 text-xs text-ink-3">{t("callback.wait")}</p>
          </>
        )}
      </div>
    </div>
  );
}
