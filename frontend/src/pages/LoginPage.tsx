import { useEffect, useState } from "react";
import { useNavigate, Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import naverIcon from "../assets/navericon.png";
import googleIcon from "../assets/googleicon.png";
import { login, startOAuth2Login, type SocialProvider } from "../api/auth";
import { setAccessToken } from "../api/tokenStore";
import { setUser } from "../api/userStore";

const PROVIDER_LABEL_EN: Record<SocialProvider, string> = {
  google: "Google",
  kakao: "Kakao",
  naver: "NAVER",
};

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

export default function LoginPage() {
  const navigate = useNavigate();
  const { t } = useTranslation("loginPage");
  const [searchParams, setSearchParams] = useSearchParams();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const resolveProviderLabel = (provider: string | null): string => {
    if (provider === "kakao") return t("oauth2.providerKakao");
    if (provider && provider in PROVIDER_LABEL_EN) {
      return PROVIDER_LABEL_EN[provider as SocialProvider];
    }
    return t("oauth2.providerFallback");
  };

  const resolveOAuth2Error = (code: string | null, provider: string | null): string => {
    const providerLabel = resolveProviderLabel(provider);
    switch (code) {
      case "ACCOUNT_LINK_REQUIRED":
        return t("oauth2.accountLinkRequired");
      case "PROVIDER_EMAIL_NOT_VERIFIED":
        return t("oauth2.providerEmailNotVerified", { provider: providerLabel });
      case "PROVIDER_PROFILE_INVALID":
        return t("oauth2.providerProfileInvalid", { provider: providerLabel });
      case "ACCOUNT_INACTIVE":
        return t("oauth2.accountInactive");
      case "SOCIAL_ACCOUNT_INVALID":
        return t("oauth2.socialAccountInvalid");
      default:
        return t("oauth2.loginFailed", { provider: providerLabel });
    }
  };

  useEffect(() => {
    const status = searchParams.get("status");
    if (status === "error") {
      const code = searchParams.get("code");
      const provider = searchParams.get("provider");
      setError(resolveOAuth2Error(code, provider));
      // URL 파라미터 정리 — 새로고침 시 에러가 다시 떠오르지 않도록.
      const next = new URLSearchParams(searchParams);
      next.delete("status");
      next.delete("code");
      next.delete("provider");
      setSearchParams(next, { replace: true });
    }
    // resolveOAuth2Error 는 t 에 의존하지만 언어 변경마다 다시 평가할 필요는 없음
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, setSearchParams]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      if (!form.email || !form.password) {
        throw new Error(t("errors.empty"));
      }

      const data = await login({ email: form.email, password: form.password });

      setAccessToken(data.accessToken);
      setUser({ email: data.email, name: data.name });

      const redirect = sessionStorage.getItem("qaima_redirect") || "/feature/1";
      sessionStorage.removeItem("qaima_redirect");
      navigate(redirect);
    } catch (err: unknown) {
      const axiosErr = err as any;
      const serverMessage = axiosErr?.response?.data?.errors?.[0]?.message;
      const status = axiosErr?.response?.status;

      if (serverMessage) {
        setError(serverMessage);
      } else if (status === 400) {
        setError(t("errors.invalid"));
      } else if (status === 401) {
        setError(t("errors.expired"));
      } else if (status === 423) {
        setError(t("errors.locked"));
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(t("errors.unknown"));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-4 md:ml-[84px]">
      <div className="w-full max-w-[440px] bg-surface border border-line rounded-2xl shadow-card p-10 flex flex-col gap-7">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">
            {t("title")}
          </h1>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <input
            name="email"
            type="email"
            placeholder={t("fields.email")}
            value={form.email}
            onChange={handleChange}
            className={inputClass}
          />
          <input
            name="password"
            type="password"
            placeholder={t("fields.password")}
            value={form.password}
            onChange={handleChange}
            className={inputClass}
          />

          {error && <p className="text-xs text-danger">{error}</p>}

          <div className="flex justify-between items-center text-xs text-ink-3 mt-1">
            <Link to="/signup" className="hover:text-ink transition-colors">
              {t("links.signup")}
            </Link>
            <Link to="/find-account" className="hover:text-ink transition-colors">
              {t("links.findAccount")}
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loading ? t("buttons.submitting") : t("buttons.submit")}
          </button>
        </form>

        {/* 구분선 */}
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-line"></div>
          </div>
          <div className="relative flex justify-center">
            <span className="bg-surface px-3 text-[11px] text-ink-3 tracking-tight uppercase">
              {t("or")}
            </span>
          </div>
        </div>

        {/* 소셜 로그인 */}
        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => startOAuth2Login("naver")}
            className="flex flex-col items-center gap-2 py-3 rounded-lg border border-line bg-surface hover:bg-bg-sunk transition-colors"
          >
            <img src={naverIcon} alt={t("buttons.naver")} className="w-9 h-9 rounded-full" />
            <span className="text-[11px] font-medium text-ink-2">NAVER</span>
          </button>

          <button
            type="button"
            onClick={() => startOAuth2Login("google")}
            className="flex flex-col items-center gap-2 py-3 rounded-lg border border-line bg-surface hover:bg-bg-sunk transition-colors"
          >
            <img src={googleIcon} alt={t("buttons.google")} className="w-9 h-9 rounded-full" />
            <span className="text-[11px] font-medium text-ink-2">Google</span>
          </button>
        </div>
      </div>
    </div>
  );
}
