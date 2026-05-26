import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronDown } from "lucide-react";
import { requestEmailVerification, confirmEmailVerification, signup } from "../api/auth";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

const inlineActionBtn =
  "px-4 py-3 rounded-lg bg-accent text-white text-sm font-semibold whitespace-nowrap hover:opacity-90 disabled:opacity-50 transition-opacity tracking-tight";

const inlineSuccessBadge =
  "px-4 py-3 rounded-lg bg-success/15 text-success border border-success/30 text-sm font-semibold whitespace-nowrap flex items-center";

// 백엔드는 국적 코드를 그대로 한국어 라벨로 저장하므로 키 → 한국어 라벨로 보낸다.
// 표시 라벨만 다국어화하고, 제출 값은 항상 한국어를 유지한다.
const COUNTRY_KEYS = ["kr", "us", "jp", "cn", "gb", "fr", "de", "sg", "hk", "ca", "au", "other"] as const;
const COUNTRY_SUBMIT_VALUE: Record<(typeof COUNTRY_KEYS)[number], string> = {
  kr: "대한민국",
  us: "미국",
  jp: "일본",
  cn: "중국",
  gb: "영국",
  fr: "프랑스",
  de: "독일",
  sg: "싱가포르",
  hk: "홍콩",
  ca: "캐나다",
  au: "호주",
  other: "기타",
};

const numericControlKeys = new Set([
  "Backspace",
  "Delete",
  "Tab",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
]);

export default function SignupPage() {
  const navigate = useNavigate();
  const { t } = useTranslation(["signupPage", "oauthPage"]);

  const [formData, setFormData] = useState({
    email: "",
    verificationCode: "",
    password: "",
    passwordConfirm: "",
    name: "",
    phone: "",
    birthdate: "",
    birthdateSecond: "",
    country: "",
  });

  const [showCountryDropdown, setShowCountryDropdown] = useState(false);
  const countryRef = useRef<HTMLDivElement | null>(null);
  const [emailVerified, setEmailVerified] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);
  const [verificationLoading, setVerificationLoading] = useState(false);
  const [signupLoading, setSignupLoading] = useState(false);
  const [privacyAgreed, setPrivacyAgreed] = useState(false);
  const [showPrivacyDetail, setShowPrivacyDetail] = useState(false);

  const [errors, setErrors] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    birthdate: "",
  });

  useEffect(() => {
    if (!showCountryDropdown) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (!countryRef.current) return;
      if (countryRef.current.contains(e.target as Node)) return;
      setShowCountryDropdown(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showCountryDropdown]);

  const handleInputChange = (field: string, value: string) => {
    if (field === "phone") {
      value = value.replace(/[^0-9]/g, "");
    } else if (field === "birthdate") {
      value = value.replace(/[^0-9]/g, "").slice(0, 6);
    } else if (field === "birthdateSecond") {
      value = value.replace(/[^0-9]/g, "").slice(0, 1);
    }
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleNumericKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.ctrlKey || e.metaKey || numericControlKeys.has(e.key)) return;
    if (!/^[0-9]$/.test(e.key)) e.preventDefault();
  };

  const validateEmail = (value: string) => {
    const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    return ok ? "" : t("validation.emailFormat");
  };

  const validatePassword = (value: string) => {
    if (value.length < 8) return t("validation.passwordShort");
    if (value.length > 20) return t("validation.passwordLong");
    const hasNumber = /[0-9]/.test(value);
    const hasLetter = /[a-zA-Z]/.test(value);
    const hasSpecial = /[^0-9a-zA-Z]/.test(value);
    if (!hasNumber || !hasLetter || !hasSpecial) {
      return t("validation.passwordComposition");
    }
    return "";
  };

  const validatePasswordConfirm = (password: string, confirm: string) =>
    password === confirm ? "" : t("validation.passwordMismatch");

  const validateBirthdate = (date: string, genderDigit: string) => {
    if (!/^[0-9]{6}$/.test(date)) return t("validation.birthdateInvalid");
    if (!/^[0-9]$/.test(genderDigit)) return t("validation.genderDigitInvalid");

    const yy = parseInt(date.slice(0, 2), 10);
    const mm = parseInt(date.slice(2, 4), 10);
    const dd = parseInt(date.slice(4, 6), 10);

    if (mm < 1 || mm > 12) return t("validation.birthdateInvalid");

    const isLeap = yy % 4 === 0;
    const daysInMonth = [31, isLeap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    const maxDay = daysInMonth[mm - 1];

    if (dd < 1 || dd > maxDay) return t("validation.birthdateInvalid");

    const gd = parseInt(genderDigit, 10);
    if (gd < 1 || gd > 4) return t("validation.genderDigitInvalid");

    return "";
  };

  const handleVerification = async () => {
    if (!formData.email) {
      alert(t("alerts.emailFirst"));
      return;
    }
    const emailError = validateEmail(formData.email);
    if (emailError) {
      alert(emailError);
      return;
    }
    setVerificationLoading(true);
    try {
      await requestEmailVerification(formData.email);
      setVerificationSent(true);
      alert(t("alerts.codeSent"));
    } catch (err: unknown) {
      if (err instanceof Error) alert(t("alerts.codeSendFailed", { message: err.message }));
      else alert(t("alerts.codeSendUnknown"));
    } finally {
      setVerificationLoading(false);
    }
  };

  const handleConfirmVerification = async () => {
    if (!formData.verificationCode) {
      alert(t("alerts.codeEmpty"));
      return;
    }
    setVerificationLoading(true);
    try {
      await confirmEmailVerification(formData.email, formData.verificationCode);
      setEmailVerified(true);
      alert(t("alerts.verifiedOk"));
    } catch (err: unknown) {
      if (err instanceof Error) alert(t("alerts.verifyFailed", { message: err.message }));
      else alert(t("alerts.verifyUnknown"));
    } finally {
      setVerificationLoading(false);
    }
  };

  const labelFor = (field: keyof typeof formData): string => t(`labels.${field}`);

  const handleBlur = (field: keyof typeof formData) => {
    setErrors((prev) => {
      const next = { ...prev };
      if (field === "email") next.email = validateEmail(formData.email);
      else if (field === "password") next.password = validatePassword(formData.password);
      else if (field === "passwordConfirm")
        next.passwordConfirm = validatePasswordConfirm(formData.password, formData.passwordConfirm);
      else if (field === "birthdate")
        next.birthdate = validateBirthdate(formData.birthdate, formData.birthdateSecond);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const newErrors = {
      email: validateEmail(formData.email),
      password: validatePassword(formData.password),
      passwordConfirm: validatePasswordConfirm(formData.password, formData.passwordConfirm),
      birthdate: validateBirthdate(formData.birthdate, formData.birthdateSecond),
    };

    const requiredOrder: Array<keyof typeof formData> = [
      "email", "verificationCode", "password", "passwordConfirm",
      "name", "phone", "birthdate", "birthdateSecond", "country",
    ];

    for (const field of requiredOrder) {
      if (!formData[field]) {
        alert(t("alerts.fieldRequired", { label: labelFor(field) }));
        return;
      }
    }

    if (newErrors.email || newErrors.password || newErrors.passwordConfirm || newErrors.birthdate) {
      setErrors(newErrors);
      return;
    }
    setErrors(newErrors);

    if (!emailVerified) {
      alert(t("alerts.emailNotVerified"));
      return;
    }

    if (!privacyAgreed) {
      alert(t("alerts.privacyRequired"));
      return;
    }

    setSignupLoading(true);
    try {
      await signup({
        email: formData.email,
        password: formData.password,
        verificationCode: formData.verificationCode,
        name: formData.name,
        birthdate: formData.birthdate + formData.birthdateSecond,
        phone: formData.phone,
        country: formData.country,
      });
      alert(t("alerts.signupDone"));
      navigate("/login");
    } catch (err: unknown) {
      const axiosErr = err as any;
      const serverMessage = axiosErr?.response?.data?.errors?.[0]?.message;
      const status = axiosErr?.response?.status;

      if (serverMessage) alert(t("alerts.signupFailedMessage", { message: serverMessage }));
      else if (status === 409) alert(t("alerts.signupConflict"));
      else if (status === 400) alert(t("alerts.signupBadRequest"));
      else if (err instanceof Error) alert(t("alerts.signupFailedMessage", { message: err.message }));
      else alert(t("alerts.signupUnknown"));
    } finally {
      setSignupLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-4 md:ml-[84px]">
      <div className="w-full max-w-[460px] bg-surface border border-line rounded-2xl shadow-card p-10 flex flex-col gap-7">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">{t("title")}</h1>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          {/* 이메일 + 인증받기/재요청 */}
          <div>
            <div className="flex gap-2">
              <input
                type="email"
                placeholder={t("fields.email")}
                value={formData.email}
                onChange={(e) => handleInputChange("email", e.target.value)}
                onBlur={() => handleBlur("email")}
                className={inputClass + " flex-1"}
              />
              {!emailVerified && (
                <button
                  type="button"
                  onClick={handleVerification}
                  disabled={verificationLoading}
                  className={inlineActionBtn}
                >
                  {verificationLoading
                    ? t("buttons.processing")
                    : verificationSent
                    ? t("buttons.resendCode")
                    : t("buttons.requestCode")}
                </button>
              )}
            </div>
            {errors.email && (
              <p className="mt-1.5 text-xs text-danger">{errors.email}</p>
            )}
          </div>

          {/* 인증번호 + 인증확인 */}
          <div>
            <div className="flex gap-2 items-start">
              <input
                type="text"
                inputMode="numeric"
                placeholder={t("fields.verificationCode")}
                value={formData.verificationCode}
                onChange={(e) => handleInputChange("verificationCode", e.target.value)}
                maxLength={6}
                disabled={emailVerified}
                className={inputClass + " w-[160px] disabled:opacity-60"}
              />
              {!emailVerified ? (
                <button
                  type="button"
                  onClick={handleConfirmVerification}
                  disabled={verificationLoading || !verificationSent}
                  className={inlineActionBtn}
                >
                  {t("buttons.confirmCode")}
                </button>
              ) : (
                <div className={inlineSuccessBadge}>{t("buttons.verified")}</div>
              )}
            </div>
            {verificationSent && !emailVerified && (
              <p className="mt-1.5 text-xs text-accent">{t("codeHint")}</p>
            )}
          </div>

          {/* 비밀번호 */}
          <div>
            <input
              type="password"
              placeholder={t("fields.password")}
              value={formData.password}
              onChange={(e) => handleInputChange("password", e.target.value)}
              onBlur={() => handleBlur("password")}
              maxLength={20}
              className={inputClass}
            />
            {errors.password && (
              <p className="mt-1.5 text-xs text-danger">{errors.password}</p>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div>
            <input
              type="password"
              placeholder={t("fields.passwordConfirm")}
              value={formData.passwordConfirm}
              onChange={(e) => handleInputChange("passwordConfirm", e.target.value)}
              onBlur={() => handleBlur("passwordConfirm")}
              maxLength={20}
              className={inputClass}
            />
            {errors.passwordConfirm && (
              <p className="mt-1.5 text-xs text-danger">{errors.passwordConfirm}</p>
            )}
          </div>

          {/* 이름 */}
          <input
            type="text"
            placeholder={t("fields.name")}
            value={formData.name}
            onChange={(e) => handleInputChange("name", e.target.value)}
            className={inputClass}
          />

          {/* 전화번호 */}
          <input
            type="tel"
            placeholder={t("fields.phone")}
            value={formData.phone}
            onChange={(e) => handleInputChange("phone", e.target.value)}
            className={inputClass}
          />

          {/* 생년월일 + 성별 한 자리 */}
          <div>
            <div className="flex gap-2 items-stretch">
              <input
                type="text"
                placeholder={t("fields.birthdate")}
                value={formData.birthdate}
                onChange={(e) => handleInputChange("birthdate", e.target.value)}
                onKeyDown={handleNumericKeyDown}
                onBlur={() => handleBlur("birthdate")}
                maxLength={6}
                inputMode="numeric"
                pattern="[0-9]*"
                className={inputClass + " min-w-0 flex-[1_1_0%]"}
              />
              <input
                type="text"
                placeholder={t("fields.birthdateSecond")}
                value={formData.birthdateSecond}
                onChange={(e) => handleInputChange("birthdateSecond", e.target.value)}
                onKeyDown={handleNumericKeyDown}
                onBlur={() => handleBlur("birthdate")}
                maxLength={1}
                inputMode="numeric"
                pattern="[0-9]*"
                className={inputClass + " !w-[52px] flex-none text-center"}
              />
              <div className="flex items-center text-ink-4 text-sm font-mono tabular tracking-widest select-none">
                ······
              </div>
            </div>
            {errors.birthdate && (
              <p className="mt-1.5 text-xs text-danger">{errors.birthdate}</p>
            )}
          </div>

          {/* 국적 */}
          <div ref={countryRef} className="relative">
            <button
              type="button"
              onClick={() => setShowCountryDropdown((prev) => !prev)}
              className="w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line flex items-center justify-between text-sm hover:bg-surface-2 transition-colors"
            >
              <span className={formData.country ? "text-ink" : "text-ink-4"}>
                {formData.country
                  ? t(`oauthPage:socialComplete.countries.${
                      (Object.keys(COUNTRY_SUBMIT_VALUE) as Array<keyof typeof COUNTRY_SUBMIT_VALUE>)
                        .find((k) => COUNTRY_SUBMIT_VALUE[k] === formData.country) ?? "other"
                    }`)
                  : t("fields.country")}
              </span>
              <ChevronDown size={16} className="text-ink-3 pointer-events-none" />
            </button>
            {showCountryDropdown && (
              <div className="absolute top-full left-0 w-full mt-1.5 bg-surface border border-line rounded-lg shadow-pop z-10 max-h-[220px] overflow-y-auto">
                {COUNTRY_KEYS.map((key) => {
                  const submit = COUNTRY_SUBMIT_VALUE[key];
                  const isActive = formData.country === submit;
                  return (
                    <button
                      key={key}
                      type="button"
                      onClick={() => {
                        handleInputChange("country", submit);
                        setShowCountryDropdown(false);
                      }}
                      className={`w-full px-4 py-2.5 text-left text-sm hover:bg-bg-sunk first:rounded-t-lg last:rounded-b-lg ${
                        isActive
                          ? "bg-accent-soft text-accent font-semibold"
                          : "text-ink"
                      }`}
                    >
                      {t(`oauthPage:socialComplete.countries.${key}`)}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* 개인정보 수집 및 이용 동의 */}
          <div className="rounded-lg border border-line bg-bg-sunk px-4 py-3">
            <div className="flex items-start gap-2">
              <input
                id="privacy-agreement"
                type="checkbox"
                checked={privacyAgreed}
                onChange={(e) => setPrivacyAgreed(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-line accent-accent"
              />
              <div className="flex-1">
                <label htmlFor="privacy-agreement" className="text-sm text-ink">
                  {t("privacy.agree")}
                </label>
                <button
                  type="button"
                  onClick={() => setShowPrivacyDetail((prev) => !prev)}
                  className="ml-2 text-xs font-medium text-accent hover:underline"
                >
                  {t("privacy.details")}
                </button>
              </div>
            </div>
            {showPrivacyDetail && (
              <div className="mt-3 rounded-md border border-line bg-surface p-3 text-xs leading-5 text-ink-2">
                <p className="font-semibold text-ink">{t("privacy.panelTitle")}</p>
                <p className="mt-2">{t("privacy.panelP1")}</p>
                <p className="mt-2">{t("privacy.panelP2")}</p>
                <p className="mt-2">{t("privacy.panelP3")}</p>
                <p className="mt-2">{t("privacy.panelP4")}</p>
              </div>
            )}
          </div>

          {/* 가입 버튼 */}
          <button
            type="submit"
            disabled={signupLoading}
            className="w-full mt-3 py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {signupLoading ? t("buttons.submitting") : t("buttons.submit")}
          </button>

          <p className="text-xs text-ink-3 text-center mt-1">
            {t("loginLink.intro")}{" "}
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="text-accent font-medium hover:underline"
            >
              {t("loginLink.action")}
            </button>
          </p>
        </form>
      </div>
    </div>
  );
}
