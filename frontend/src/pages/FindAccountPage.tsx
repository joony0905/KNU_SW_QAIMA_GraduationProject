import { useState } from "react";
import { Link } from "react-router-dom";
import { requestPasswordReset } from "../api/auth";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

const primaryBtn =
  "w-full py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity";

type ResultState =
  | { type: "idle" }
  | { type: "success"; message: string }
  | { type: "error"; message: string };

const maskEmail = (email: string): string => {
  const [local, domain] = email.split("@");
  if (!local || !domain) return email;
  if (local.length <= 2) return `${local[0]}*@${domain}`;
  const visible = local.slice(0, 2);
  const masked = "*".repeat(Math.min(local.length - 2, 4));
  return `${visible}${masked}@${domain}`;
};

export default function FindAccountPage() {
  // 아이디 찾기 (목업)
  const [findIdForm, setFindIdForm] = useState({ name: "", birthdate: "" });
  const [findIdLoading, setFindIdLoading] = useState(false);
  const [findIdResult, setFindIdResult] = useState<ResultState>({ type: "idle" });

  // 비밀번호 재설정 (백엔드 연결)
  const [resetEmail, setResetEmail] = useState("");
  const [resetLoading, setResetLoading] = useState(false);
  const [resetResult, setResetResult] = useState<ResultState>({ type: "idle" });

  const handleFindId = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!findIdForm.name.trim()) {
      setFindIdResult({ type: "error", message: "이름을 입력해주세요." });
      return;
    }
    if (!/^\d{6}$/.test(findIdForm.birthdate)) {
      setFindIdResult({ type: "error", message: "생년월일 6자리(YYMMDD)를 입력해주세요." });
      return;
    }

    setFindIdLoading(true);
    setFindIdResult({ type: "idle" });

    // 목업: 0.6초 지연 후 마스킹된 이메일 반환
    await new Promise((r) => setTimeout(r, 600));
    const mockEmail = `${findIdForm.name.trim().toLowerCase().slice(0, 2)}user@example.com`;
    setFindIdResult({
      type: "success",
      message: maskEmail(mockEmail),
    });
    setFindIdLoading(false);
  };

  const handleRequestReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetEmail.trim()) {
      setResetResult({ type: "error", message: "이메일을 입력해주세요." });
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetEmail)) {
      setResetResult({ type: "error", message: "이메일 형식이 올바르지 않습니다." });
      return;
    }

    setResetLoading(true);
    setResetResult({ type: "idle" });

    try {
      await requestPasswordReset(resetEmail.trim());
      setResetResult({
        type: "success",
        message:
          "입력하신 이메일이 가입돼 있다면 비밀번호 재설정 링크가 발송됩니다. 메일함을 확인해주세요. (링크 유효시간: 30분)",
      });
    } catch (err: unknown) {
      const axiosErr = err as any;
      const status = axiosErr?.response?.status;
      const message = axiosErr?.response?.data?.errors?.[0]?.message;

      if (status === 429) {
        setResetResult({
          type: "error",
          message: "요청이 너무 잦습니다. 잠시 후 다시 시도해주세요.",
        });
      } else if (message) {
        setResetResult({ type: "error", message });
      } else if (err instanceof Error) {
        setResetResult({ type: "error", message: err.message });
      } else {
        setResetResult({
          type: "error",
          message: "요청 처리 중 오류가 발생했습니다.",
        });
      }
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg ml-[84px] py-12 px-4">
      <div className="max-w-5xl mx-auto flex flex-col gap-8">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">
            계정 찾기
          </h1>
          <p className="mt-1.5 text-sm text-ink-3">
            아이디를 잊었거나 비밀번호를 분실하셨나요?
          </p>
        </div>

        {/* 두 카드 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* 아이디 찾기 */}
          <section className="bg-surface border border-line rounded-2xl shadow-card p-8 flex flex-col gap-5">
            <div>
              <div className="text-[11px] font-semibold text-accent tracking-tight">
                FIND ID
              </div>
              <h2 className="mt-1 text-xl font-bold text-ink tracking-tight">
                아이디 찾기
              </h2>
              <p className="mt-1 text-xs text-ink-3">
                가입 시 입력한 이름과 생년월일로 조회합니다.
              </p>
            </div>

            <form onSubmit={handleFindId} className="flex flex-col gap-3">
              <input
                type="text"
                placeholder="이름"
                value={findIdForm.name}
                onChange={(e) =>
                  setFindIdForm((prev) => ({ ...prev, name: e.target.value }))
                }
                className={inputClass}
              />
              <input
                type="text"
                inputMode="numeric"
                placeholder="생년월일 (6자리)"
                value={findIdForm.birthdate}
                onChange={(e) =>
                  setFindIdForm((prev) => ({
                    ...prev,
                    birthdate: e.target.value.replace(/\D/g, ""),
                  }))
                }
                maxLength={6}
                className={inputClass}
              />

              <button
                type="submit"
                disabled={findIdLoading}
                className={primaryBtn + " mt-2"}
              >
                {findIdLoading ? "조회 중..." : "아이디 찾기"}
              </button>
            </form>

            {findIdResult.type === "success" && (
              <div className="rounded-lg bg-success/10 border border-success/30 px-4 py-3 text-sm">
                <p className="text-ink-3 text-xs mb-1">회원님의 아이디</p>
                <p className="font-mono font-semibold text-ink">
                  {findIdResult.message}
                </p>
                <p className="mt-2 text-[11px] text-ink-4">
                  ※ 현재 목업 응답입니다 (백엔드 미구현)
                </p>
              </div>
            )}
            {findIdResult.type === "error" && (
              <p className="text-xs text-danger">{findIdResult.message}</p>
            )}
          </section>

          {/* 비밀번호 재설정 */}
          <section className="bg-surface border border-line rounded-2xl shadow-card p-8 flex flex-col gap-5">
            <div>
              <div className="text-[11px] font-semibold text-accent tracking-tight">
                RESET PASSWORD
              </div>
              <h2 className="mt-1 text-xl font-bold text-ink tracking-tight">
                비밀번호 재설정
              </h2>
              <p className="mt-1 text-xs text-ink-3">
                가입한 이메일로 비밀번호 재설정 링크를 보내드립니다.
              </p>
            </div>

            <form onSubmit={handleRequestReset} className="flex flex-col gap-3">
              <input
                type="email"
                placeholder="이메일"
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                className={inputClass}
              />

              <button
                type="submit"
                disabled={resetLoading}
                className={primaryBtn + " mt-2"}
              >
                {resetLoading ? "전송 중..." : "재설정 링크 받기"}
              </button>
            </form>

            {resetResult.type === "success" && (
              <div className="rounded-lg bg-accent-soft border border-accent/30 px-4 py-3 text-sm text-accent">
                {resetResult.message}
              </div>
            )}
            {resetResult.type === "error" && (
              <p className="text-xs text-danger">{resetResult.message}</p>
            )}
          </section>
        </div>

        {/* 하단 */}
        <p className="text-center text-xs text-ink-3">
          <Link to="/login" className="hover:text-ink transition-colors">
            ← 로그인으로 돌아가기
          </Link>
        </p>
      </div>
    </div>
  );
}
