import { useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { findLoginId, requestPasswordReset } from "../api/auth";
import { getApiErrorMessage } from "../utils/errorMessage";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

const primaryBtn =
  "w-full py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity";

type ResultState =
  | { type: "idle" }
  | { type: "success"; message: string }
  | { type: "error"; message: string };

export default function FindAccountPage() {
  const { t } = useTranslation("findAccountPage");

  const [findIdForm, setFindIdForm] = useState({
    name: "",
    birthdate: "",
    phone: "",
  });
  const [findIdLoading, setFindIdLoading] = useState(false);
  const [findIdResult, setFindIdResult] = useState<ResultState>({ type: "idle" });

  const [resetEmail, setResetEmail] = useState("");
  const [resetLoading, setResetLoading] = useState(false);
  const [resetResult, setResetResult] = useState<ResultState>({ type: "idle" });

  const handleFindId = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!findIdForm.name.trim()) {
      setFindIdResult({ type: "error", message: t("findId.errors.noName") });
      return;
    }
    if (!/^\d{6}$/.test(findIdForm.birthdate)) {
      setFindIdResult({ type: "error", message: t("findId.errors.invalidBirthdate") });
      return;
    }

    setFindIdLoading(true);
    setFindIdResult({ type: "idle" });

    try {
      const phone = findIdForm.phone.trim();
      const { maskedEmail } = await findLoginId({
        name: findIdForm.name.trim(),
        birthdate: findIdForm.birthdate,
        ...(phone ? { phone } : {}),
      });
      setFindIdResult({ type: "success", message: maskedEmail });
    } catch (err) {
      // 미존재/동명이인 중복 등은 백엔드가 한글 메시지를 errors[0].message 로 내려준다.
      setFindIdResult({
        type: "error",
        message: getApiErrorMessage(err, t("findId.errors.notFound")),
      });
    } finally {
      setFindIdLoading(false);
    }
  };

  const handleRequestReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetEmail.trim()) {
      setResetResult({ type: "error", message: t("reset.errors.noEmail") });
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetEmail)) {
      setResetResult({ type: "error", message: t("reset.errors.invalidEmail") });
      return;
    }

    setResetLoading(true);
    setResetResult({ type: "idle" });

    try {
      await requestPasswordReset(resetEmail.trim());
      setResetResult({ type: "success", message: t("reset.successMessage") });
    } catch (err: unknown) {
      const axiosErr = err as any;
      const status = axiosErr?.response?.status;
      const message = axiosErr?.response?.data?.errors?.[0]?.message;

      if (status === 429) {
        setResetResult({ type: "error", message: t("reset.errors.rateLimited") });
      } else if (message) {
        setResetResult({ type: "error", message });
      } else if (err instanceof Error) {
        setResetResult({ type: "error", message: err.message });
      } else {
        setResetResult({ type: "error", message: t("reset.errors.unknown") });
      }
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg md:ml-[84px] py-12 px-4">
      <div className="max-w-5xl mx-auto flex flex-col gap-8">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">
            {t("title")}
          </h1>
          <p className="mt-1.5 text-sm text-ink-3">{t("subtitle")}</p>
        </div>

        {/* 두 카드 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* 아이디 찾기 */}
          <section className="bg-surface border border-line rounded-2xl shadow-card p-8 flex flex-col gap-5">
            <div>
              <div className="text-[11px] font-semibold text-accent tracking-tight">
                {t("findId.eyebrow")}
              </div>
              <h2 className="mt-1 text-xl font-bold text-ink tracking-tight">
                {t("findId.title")}
              </h2>
              <p className="mt-1 text-xs text-ink-3">{t("findId.description")}</p>
            </div>

            <form onSubmit={handleFindId} className="flex flex-col gap-3">
              <input
                type="text"
                placeholder={t("findId.fields.name")}
                value={findIdForm.name}
                onChange={(e) =>
                  setFindIdForm((prev) => ({ ...prev, name: e.target.value }))
                }
                className={inputClass}
              />
              <input
                type="text"
                inputMode="numeric"
                placeholder={t("findId.fields.birthdate")}
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
              <input
                type="text"
                inputMode="numeric"
                placeholder={t("findId.fields.phone")}
                value={findIdForm.phone}
                onChange={(e) =>
                  setFindIdForm((prev) => ({
                    ...prev,
                    phone: e.target.value.replace(/[^\d-]/g, ""),
                  }))
                }
                maxLength={13}
                className={inputClass}
              />

              <button
                type="submit"
                disabled={findIdLoading}
                className={primaryBtn + " mt-2"}
              >
                {findIdLoading ? t("findId.buttons.submitting") : t("findId.buttons.submit")}
              </button>
            </form>

            {findIdResult.type === "success" && (
              <div className="rounded-lg bg-success/10 border border-success/30 px-4 py-3 text-sm">
                <p className="text-ink-3 text-xs mb-1">{t("findId.result.label")}</p>
                <p className="font-mono font-semibold text-ink">{findIdResult.message}</p>
                <p className="mt-2 text-[11px] text-ink-4">{t("findId.result.maskNote")}</p>
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
                {t("reset.eyebrow")}
              </div>
              <h2 className="mt-1 text-xl font-bold text-ink tracking-tight">
                {t("reset.title")}
              </h2>
              <p className="mt-1 text-xs text-ink-3">{t("reset.description")}</p>
            </div>

            <form onSubmit={handleRequestReset} className="flex flex-col gap-3">
              <input
                type="email"
                placeholder={t("reset.fields.email")}
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                className={inputClass}
              />

              <button
                type="submit"
                disabled={resetLoading}
                className={primaryBtn + " mt-2"}
              >
                {resetLoading ? t("reset.buttons.submitting") : t("reset.buttons.submit")}
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
            {t("backToLogin")}
          </Link>
        </p>
      </div>
    </div>
  );
}
