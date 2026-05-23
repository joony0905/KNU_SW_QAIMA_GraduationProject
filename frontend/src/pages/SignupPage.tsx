import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronDown } from "lucide-react";
import { requestEmailVerification, confirmEmailVerification, signup } from "../api/auth";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

const inlineActionBtn =
  "px-4 py-3 rounded-lg bg-accent text-white text-sm font-semibold whitespace-nowrap hover:opacity-90 disabled:opacity-50 transition-opacity tracking-tight";

const inlineSuccessBadge =
  "px-4 py-3 rounded-lg bg-success/15 text-success border border-success/30 text-sm font-semibold whitespace-nowrap flex items-center";

const COUNTRY_OPTIONS = ["대한민국", "미국", "일본", "중국", "영국", "프랑스", "독일","싱가포르", "홍콩", "캐나다", "호주", "기타"];
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
    return ok ? "" : "이메일 형식이 올바르지 않습니다.";
  };

  const validatePassword = (value: string) => {
    if (value.length < 8) return "비밀번호가 너무 짧습니다.";
    if (value.length > 12) return "비밀번호는 최대 12자리입니다.";
    const hasNumber = /[0-9]/.test(value);
    const hasLetter = /[a-zA-Z]/.test(value);
    const hasSpecial = /[^0-9a-zA-Z]/.test(value);
    if (!hasNumber || !hasLetter || !hasSpecial) {
      return "비밀번호에는 숫자, 영어, 특수문자가 모두 하나씩 포함되어야 합니다.";
    }
    return "";
  };

  const validatePasswordConfirm = (password: string, confirm: string) =>
    password === confirm ? "" : "비밀번호가 일치하지 않습니다.";

  const validateBirthdate = (date: string, genderDigit: string) => {
    if (!/^[0-9]{6}$/.test(date)) return "올바른 생년월일을 기입해주십시오.";
    if (!/^[0-9]$/.test(genderDigit)) return "올바른 성별 자릿수를 기입해주십시오.";

    const yy = parseInt(date.slice(0, 2), 10);
    const mm = parseInt(date.slice(2, 4), 10);
    const dd = parseInt(date.slice(4, 6), 10);

    if (mm < 1 || mm > 12) return "올바른 생년월일을 기입해주십시오.";

    const isLeap = yy % 4 === 0;
    const daysInMonth = [31, isLeap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    const maxDay = daysInMonth[mm - 1];

    if (dd < 1 || dd > maxDay) return "올바른 생년월일을 기입해주십시오.";

    const gd = parseInt(genderDigit, 10);
    if (gd < 1 || gd > 4) return "올바른 성별 자릿수를 기입해주십시오.";

    return "";
  };

  const handleVerification = async () => {
    if (!formData.email) {
      alert("이메일을 먼저 입력해주세요.");
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
      alert("인증코드가 발송되었습니다. 이메일을 확인해주세요.");
    } catch (err: unknown) {
      if (err instanceof Error) alert("인증코드 발송 실패: " + err.message);
      else alert("인증코드 발송 중 오류가 발생했습니다.");
    } finally {
      setVerificationLoading(false);
    }
  };

  const handleConfirmVerification = async () => {
    if (!formData.verificationCode) {
      alert("인증코드를 입력해주세요.");
      return;
    }
    setVerificationLoading(true);
    try {
      await confirmEmailVerification(formData.email, formData.verificationCode);
      setEmailVerified(true);
      alert("이메일 인증이 완료되었습니다.");
    } catch (err: unknown) {
      if (err instanceof Error) alert("인증 실패: " + err.message);
      else alert("인증 중 오류가 발생했습니다.");
    } finally {
      setVerificationLoading(false);
    }
  };

  const labelFor = (field: keyof typeof formData) => {
    switch (field) {
      case "email": return "이메일";
      case "verificationCode": return "인증번호";
      case "password": return "비밀번호";
      case "passwordConfirm": return "비밀번호확인";
      case "name": return "이름";
      case "phone": return "연락처";
      case "birthdate": return "생년월일";
      case "birthdateSecond": return "주민등록번호 뒷자리";
      case "country": return "국적";
      default: return "";
    }
  };

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
        alert(`${labelFor(field)}란을 기입해주십시오.`);
        return;
      }
    }

    if (newErrors.email || newErrors.password || newErrors.passwordConfirm || newErrors.birthdate) {
      setErrors(newErrors);
      return;
    }
    setErrors(newErrors);

    if (!emailVerified) {
      alert("이메일 인증을 완료해주세요.");
      return;
    }

    if (!privacyAgreed) {
      alert("개인정보 수집 및 이용에 동의해야 회원가입이 가능합니다.");
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
      alert("회원가입이 완료되었습니다. 로그인해주세요.");
      navigate("/login");
    } catch (err: unknown) {
      const axiosErr = err as any;
      const serverMessage = axiosErr?.response?.data?.errors?.[0]?.message;
      const status = axiosErr?.response?.status;

      if (serverMessage) alert("회원가입 실패: " + serverMessage);
      else if (status === 409) alert("이미 가입된 이메일입니다.");
      else if (status === 400) alert("입력 정보를 다시 확인해주세요.");
      else if (err instanceof Error) alert("회원가입 실패: " + err.message);
      else alert("회원가입 중 오류가 발생했습니다.");
    } finally {
      setSignupLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-4 ml-[84px]">
      <div className="w-full max-w-[460px] bg-surface border border-line rounded-2xl shadow-card p-10 flex flex-col gap-7">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">
            회원가입
          </h1>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          {/* 이메일 + 인증받기/재요청 */}
          <div>
            <div className="flex gap-2">
              <input
                type="email"
                placeholder="이메일"
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
                    ? "처리 중..."
                    : verificationSent
                    ? "재요청"
                    : "인증받기"}
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
                placeholder="인증번호"
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
                  인증확인
                </button>
              ) : (
                <div className={inlineSuccessBadge}>✓ 인증완료</div>
              )}
            </div>
            {verificationSent && !emailVerified && (
              <p className="mt-1.5 text-xs text-accent">
                이메일로 발송된 인증코드를 입력 후 '인증확인' 버튼을 눌러주세요.
              </p>
            )}
          </div>

          {/* 비밀번호 */}
          <div>
            <input
              type="password"
              placeholder="비밀번호 (영문, 숫자, 특수문자 포함 8~12자)"
              value={formData.password}
              onChange={(e) => handleInputChange("password", e.target.value)}
              onBlur={() => handleBlur("password")}
              maxLength={12}
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
              placeholder="비밀번호 확인"
              value={formData.passwordConfirm}
              onChange={(e) => handleInputChange("passwordConfirm", e.target.value)}
              onBlur={() => handleBlur("passwordConfirm")}
              maxLength={12}
              className={inputClass}
            />
            {errors.passwordConfirm && (
              <p className="mt-1.5 text-xs text-danger">{errors.passwordConfirm}</p>
            )}
          </div>

          {/* 이름 */}
          <input
            type="text"
            placeholder="이름"
            value={formData.name}
            onChange={(e) => handleInputChange("name", e.target.value)}
            className={inputClass}
          />

          {/* 전화번호 */}
          <input
            type="tel"
            placeholder="전화번호 ( - 제외)"
            value={formData.phone}
            onChange={(e) => handleInputChange("phone", e.target.value)}
            className={inputClass}
          />

          {/* 생년월일 + 성별 한 자리 */}
          <div>
            <div className="flex gap-2 items-stretch">
              <input
                type="text"
                placeholder="생년월일"
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
                placeholder="1"
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
                {formData.country || "국적 선택"}
              </span>
              <ChevronDown size={16} className="text-ink-3 pointer-events-none" />
            </button>
            {showCountryDropdown && (
              <div className="absolute top-full left-0 w-full mt-1.5 bg-surface border border-line rounded-lg shadow-pop z-10 max-h-[220px] overflow-y-auto">
                {COUNTRY_OPTIONS.map((country) => (
                  <button
                    key={country}
                    type="button"
                    onClick={() => {
                      handleInputChange("country", country);
                      setShowCountryDropdown(false);
                    }}
                    className={`w-full px-4 py-2.5 text-left text-sm hover:bg-bg-sunk first:rounded-t-lg last:rounded-b-lg ${
                      formData.country === country
                        ? "bg-accent-soft text-accent font-semibold"
                        : "text-ink"
                    }`}
                  >
                    {country}
                  </button>
                ))}
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
                  개인정보 수집 및 이용에 동의합니다.
                </label>
                <button
                  type="button"
                  onClick={() => setShowPrivacyDetail((prev) => !prev)}
                  className="ml-2 text-xs font-medium text-accent hover:underline"
                >
                  자세히 보기
                </button>
              </div>
            </div>
            {showPrivacyDetail && (
              <div className="mt-3 rounded-md border border-line bg-surface p-3 text-xs leading-5 text-ink-2">
                <p className="font-semibold text-ink">개인정보 수집 및 이용 안내</p>
                <p className="mt-2">
                  QAIMA는 회원가입, 본인 확인, 서비스 제공, 이용 통계 분석 및 맞춤형 서비스 개선을 위해 개인정보를 수집·이용합니다.
                </p>
                <p className="mt-2">
                  수집 항목: 이메일, 비밀번호, 이름, 전화번호, 생년월일, 성별, 국적, 접속 IP, 브라우저 정보, 서비스 이용 기록
                </p>
                <p className="mt-2">
                  보유 및 이용 기간: 회원 탈퇴 시까지 보관하며, 관계 법령에 따라 보관이 필요한 정보는 해당 기간 동안 보관할 수 있습니다.
                </p>
                <p className="mt-2">
                  동의를 거부할 수 있으나, 필수 개인정보 수집 및 이용에 동의하지 않을 경우 회원가입이 제한됩니다.
                </p>
              </div>
            )}
          </div>

          {/* 가입 버튼 */}
          <button
            type="submit"
            disabled={signupLoading}
            className="w-full mt-3 py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {signupLoading ? "가입 중..." : "회원가입"}
          </button>

          <p className="text-xs text-ink-3 text-center mt-1">
            이미 계정이 있으신가요?{" "}
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="text-accent font-medium hover:underline"
            >
              로그인
            </button>
          </p>
        </form>
      </div>
    </div>
  );
}
