import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Link } from "react-router-dom";
import kakaoIcon from "../assets/kakaoicon.png";
import naverIcon from "../assets/navericon.png";
import googleIcon from "../assets/googleicon.png";

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
    <div className="min-h-screen flex items-center justify-center  bg-white p-4">
      <div className="w-full max-w-[420px] bg-white shadow-xl py-20 px-12 flex flex-col items-center gap-8">
        <form
          onSubmit={handleSubmit}
          className="w-full max-w-[362px] flex flex-col gap-10"
        >
          <div className="flex flex-col gap-1">
            <div className="flex flex-col gap-1">
              <h1 className="text-[24px] font-semibold text-black leading-normal">
                로그인
              </h1>

              <div className="flex flex-col gap-2.5">
                <div className="w-full px-5 py-[11px] rounded-[10px] bg-[#F0F0F0] flex items-center">
                  <input
                    name="email"
                    type="email"
                    placeholder="이메일"
                    value={form.email}
                    onChange={handleChange}
                    className="w-full bg-transparent text-[16px] font-normal text-[#828282] placeholder:text-[#828282] focus:outline-none focus:text-black"
                  />
                </div>

                <div className="w-full px-5 py-[11px] rounded-[10px] bg-[#F0F0F0] flex items-center">
                  <input
                    name="password"
                    type="password"
                    placeholder="비밀번호"
                    value={form.password}
                    onChange={handleChange}
                    className="w-full bg-transparent text-[16px] font-normal text-[#828282] placeholder:text-[#828282] focus:outline-none focus:text-black"
                  />
                </div>
              </div>
            </div>

            <div className="flex justify-between items-center w-full">
              <Link
                to="/signup"
                className="text-[16px] font-normal text-black leading-normal hover:underline"
              >
                회원가입
              </Link>
              <Link
                to="/find-account"
                className="text-[16px] font-normal text-black leading-normal hover:underline"
              >
                아이디/비밀번호 찾기
              </Link>
            </div>
          </div>

          {error && (
            <p className="text-sm text-red-600">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-[13px] px-[132px] rounded-[10px] bg-[#0E588D] flex justify-center items-center hover:bg-[#0c4a73] transition-colors disabled:opacity-60"
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
            <span className="text-[14px] font-normal text-black text-center leading-normal whitespace-pre-line">
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
            <span className="text-[14px] font-normal text-black text-center leading-normal whitespace-pre-line w-[46px]">
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
            <span className="text-[14px] font-normal text-black text-center leading-normal whitespace-pre-line">
              Google{"\n"}로그인
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
