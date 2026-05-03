import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Link } from "react-router-dom";
import kakaoIcon from "../assets/kakaoicon.png";
import naverIcon from "../assets/navericon.png";
import googleIcon from "../assets/googleicon.png";
import { login } from "../api/auth";
import { setAccessToken } from "../api/tokenStore";
import { setUser } from "../api/userStore";

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

      // 1) 지금은 서버가 없으니까, 아래 실제 호출은 잠시 막아두고
      // const data = await login(form.email, form.password);

      const data = await login({ email: form.email, password: form.password });

      setAccessToken(data.accessToken);
      setUser({ email: data.email, name: data.name });

      const redirect = sessionStorage.getItem("qaima_redirect") || "/feature/1";
      sessionStorage.removeItem("qaima_redirect");
      navigate(redirect);
    } catch (err: unknown) {
      // axios 에러인 경우 서버 응답 메시지 우선 표시
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
    <div className="min-h-screen flex items-center justify-center bg-bg p-4">
      <div className="w-full max-w-[420px] bg-surface shadow-xl py-20 px-12 flex flex-col items-center gap-8 rounded-2xl">
        <form
          onSubmit={handleSubmit}
          className="w-full max-w-[362px] flex flex-col gap-10"
        >
          <div className="flex flex-col gap-1">
            <div className="flex flex-col gap-1">
              <h1 className="text-[24px] font-semibold text-ink leading-normal">
                로그인
              </h1>

              <div className="flex flex-col gap-2.5">
                <div className="w-full px-5 py-[11px] rounded-[10px] bg-bg-sunk flex items-center">
                  <input
                    name="email"
                    type="email"
                    placeholder="이메일"
                    value={form.email}
                    onChange={handleChange}
                    className="w-full bg-transparent text-[16px] font-normal text-ink-3 placeholder:text-ink-4 focus:outline-none focus:text-ink"
                  />
                </div>

                <div className="w-full px-5 py-[11px] rounded-[10px] bg-bg-sunk flex items-center">
                  <input
                    name="password"
                    type="password"
                    placeholder="비밀번호"
                    value={form.password}
                    onChange={handleChange}
                    className="w-full bg-transparent text-[16px] font-normal text-ink-3 placeholder:text-ink-4 focus:outline-none focus:text-ink"
                  />
                </div>
              </div>
            </div>

            <div className="flex justify-between items-center w-full">
              <Link
                to="/signup"
                className="text-[16px] font-normal text-ink leading-normal hover:underline"
              >
                회원가입
              </Link>
              <Link
                to="/find-account"
                className="text-[16px] font-normal text-ink leading-normal hover:underline"
              >
                아이디/비밀번호 찾기
              </Link>
            </div>
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-[13px] px-[132px] rounded-[10px] bg-accent flex justify-center items-center hover:opacity-90 transition-opacity disabled:opacity-60"
          >
            <span className="text-[20px] font-medium text-white leading-normal">
              {loading ? "로그인 중..." : "로그인"}
            </span>
          </button>
        </form>
        <div className="flex items-center gap-8 sm:gap-16">
          {/* Kakao Login */}
          <button className="flex flex-col items-center gap-2.5 w-[60px] hover:opacity-80 transition-opacity">
            <img
              src={kakaoIcon}
              alt="카카오 로그인"
              className="w-[60px] h-[60px] rounded-full"
            />
            <span className="text-[14px] font-normal text-ink text-center leading-normal whitespace-pre-line">
              카카오{"\n"}로그인
            </span>
          </button>

          {/* Naver Login */}
          <button className="flex flex-col items-center gap-2.5 w-[60px] hover:opacity-80 transition-opacity">
            <img
              src={naverIcon}
              alt="NAVER 로그인"
              className="w-[60px] h-[60px] rounded-full"
            />
            <span className="text-[14px] font-normal text-ink text-center leading-normal whitespace-pre-line w-[46px]">
              NAVER{"\n"}로그인
            </span>
          </button>

          {/* Google Login */}
          <button className="flex flex-col items-center gap-2.5 w-[55px] hover:opacity-80 transition-opacity">
            <img
              src={googleIcon}
              alt="Google 로그인"
              className="w-[55px] h-[55px] rounded-full"
            />
            <span className="text-[14px] font-normal text-ink text-center leading-normal whitespace-pre-line">
              Google{"\n"}로그인
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
