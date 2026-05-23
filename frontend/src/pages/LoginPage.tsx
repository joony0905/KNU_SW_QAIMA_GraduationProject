import { useEffect, useState } from "react";
import { useNavigate, Link, useSearchParams } from "react-router-dom";
import naverIcon from "../assets/navericon.png";
import googleIcon from "../assets/googleicon.png";
import { login, startOAuth2Login, type SocialProvider } from "../api/auth";
import { setAccessToken } from "../api/tokenStore";
import { setUser } from "../api/userStore";

const PROVIDER_LABEL: Record<SocialProvider, string> = {
  google: "Google",
  kakao: "카카오",
  naver: "NAVER",
};

const resolveOAuth2Error = (
  code: string | null,
  provider: string | null,
): string => {
  const providerLabel =
    provider && provider in PROVIDER_LABEL
      ? PROVIDER_LABEL[provider as SocialProvider]
      : "소셜";
  switch (code) {
    case "ACCOUNT_LINK_REQUIRED":
      return "이미 가입된 이메일입니다. 일반 로그인 후 설정에서 소셜 계정을 연결해주세요.";
    case "PROVIDER_EMAIL_NOT_VERIFIED":
      return `${providerLabel} 계정의 이메일이 인증되지 않았습니다.`;
    case "PROVIDER_PROFILE_INVALID":
      return `${providerLabel} 계정에서 필수 정보를 가져오지 못했습니다. 권한을 확인해주세요.`;
    case "ACCOUNT_INACTIVE":
      return "계정이 비활성 상태입니다.";
    case "SOCIAL_ACCOUNT_INVALID":
      return "연결된 계정 정보가 올바르지 않습니다.";
    default:
      return `${providerLabel} 로그인에 실패했습니다.`;
  }
};

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
        throw new Error("이메일과 비밀번호를 입력하세요.");
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
        setError("이메일 또는 비밀번호가 일치하지 않습니다.");
      } else if (status === 401) {
        setError("로그인 세션이 만료되었습니다. 다시 로그인해주세요.");
      } else if (status === 423) {
        setError("계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("로그인 중 오류가 발생했습니다.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-4 ml-[84px]">
      <div className="w-full max-w-[440px] bg-surface border border-line rounded-2xl shadow-card p-10 flex flex-col gap-7">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">
            로그인
          </h1>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <input
            name="email"
            type="email"
            placeholder="이메일"
            value={form.email}
            onChange={handleChange}
            className={inputClass}
          />
          <input
            name="password"
            type="password"
            placeholder="비밀번호"
            value={form.password}
            onChange={handleChange}
            className={inputClass}
          />

          {error && <p className="text-xs text-danger">{error}</p>}

          <div className="flex justify-between items-center text-xs text-ink-3 mt-1">
            <Link to="/signup" className="hover:text-ink transition-colors">
              회원가입
            </Link>
            <Link to="/find-account" className="hover:text-ink transition-colors">
              아이디/비밀번호 찾기
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>

        {/* 구분선 */}
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-line"></div>
          </div>
          <div className="relative flex justify-center">
            <span className="bg-surface px-3 text-[11px] text-ink-3 tracking-tight uppercase">
              or continue with
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
            <img src={naverIcon} alt="NAVER 로그인" className="w-9 h-9 rounded-full" />
            <span className="text-[11px] font-medium text-ink-2">NAVER</span>
          </button>

          <button
            type="button"
            onClick={() => startOAuth2Login("google")}
            className="flex flex-col items-center gap-2 py-3 rounded-lg border border-line bg-surface hover:bg-bg-sunk transition-colors"
          >
            <img src={googleIcon} alt="Google 로그인" className="w-9 h-9 rounded-full" />
            <span className="text-[11px] font-medium text-ink-2">Google</span>
          </button>
        </div>
      </div>
    </div>
  );
}
