import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { requestEmailVerification, confirmEmailVerification, signup } from "../api/auth";

export default function SignupPage() {
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

  const navigate = useNavigate();

  const [showCountryDropdown, setShowCountryDropdown] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);
  const [verificationLoading, setVerificationLoading] = useState(false);
  const [signupLoading, setSignupLoading] = useState(false);

  const handleInputChange = (field: string, value: string) => {
    if (field === "phone") {
      value = value.replace(/[^0-9]/g, "");
    }
    setFormData(prev => ({ ...prev, [field]: value }));
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
      if (err instanceof Error) {
        alert("인증코드 발송 실패: " + err.message);
      } else {
        alert("인증코드 발송 중 오류가 발생했습니다.");
      }
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
      if (err instanceof Error) {
        alert("인증 실패: " + err.message);
      } else {
        alert("인증 중 오류가 발생했습니다.");
      }
    } finally {
      setVerificationLoading(false);
    }
  };

  const inputClass =
    "w-full px-5 py-3 rounded-[10px] bg-[#F0F0F0] text-black placeholder:text-[#828282] text-sm outline-none focus:ring-2 focus:ring-[#0E588D]/30";

  const [errors, setErrors] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    birthdate: "",
  });

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

  const validatePasswordConfirm = (password: string, confirm: string) => {
    return password === confirm ? "" : "비밀번호가 일치하지 않습니다.";
  };

  const validateBirthdate = (date: string, genderDigit: string) => {
  // 기본 자리수 검증
  if (!/^[0-9]{6}$/.test(date)) {
    return "올바른 생년월일을 기입해주십시오.";
  }
  if (!/^[0-9]$/.test(genderDigit)) {
    return "올바른 성별 자릿수를 기입해주십시오.";
  }

  const yy = parseInt(date.slice(0, 2), 10);
  const mm = parseInt(date.slice(2, 4), 10);
  const dd = parseInt(date.slice(4, 6), 10);

  // 월 범위 체크
  if (mm < 1 || mm > 12) {
    return "올바른 생년월일을 기입해주십시오.";
  }

  // 각 월별 최대 일수 계산 (윤년 간단 처리)
  const isLeap = (yy % 4 === 0); // 간단 버전: 4의 배수면 윤년
  const daysInMonth = [31, isLeap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  const maxDay = daysInMonth[mm - 1];

  if (dd < 1 || dd > maxDay) {
    return "올바른 생년월일을 기입해주십시오.";
  }

  // 성별 코드 허용 값 체크 (예: 1~4)
  const gd = parseInt(genderDigit, 10);
  if (gd < 1 || gd > 4) {
    return "올바른 성별 자릿수를 기입해주십시오.";
  }

  return "";
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const newErrors = {
      email: validateEmail(formData.email),
      password: validatePassword(formData.password),
      passwordConfirm: validatePasswordConfirm(
        formData.password,
        formData.passwordConfirm
      ),
      birthdate: validateBirthdate(
        formData.birthdate,
        formData.birthdateSecond
      ),
    };

    const requiredOrder: Array<keyof typeof formData> = [
      "email",
      "verificationCode",
      "password",
      "passwordConfirm",
      "name",
      "phone",
      "birthdate",
      "country",
    ];

    for (const field of requiredOrder) {
      if (!formData[field]) {
        alert(`${labelFor(field)}란을 기입해주십시오.`);
        return;
      }
    }

    if (
      newErrors.email ||
      newErrors.password ||
      newErrors.passwordConfirm ||
      newErrors.birthdate
    ) {
      setErrors(newErrors);
      return;
    }

    setErrors(newErrors);

    if (!emailVerified) {
      alert("이메일 인증을 완료해주세요.");
      return;
    }

    setSignupLoading(true);
    try {
      await signup({
        email: formData.email,
        password: formData.password,
        name: formData.name,
        birthdate: formData.birthdate + formData.birthdateSecond,
        phone: formData.phone,
      });
      alert("회원가입이 완료되었습니다. 로그인해주세요.");
      navigate("/login");
    } catch (err: unknown) {
      const axiosErr = err as any;
      const serverMessage = axiosErr?.response?.data?.errors?.[0]?.message;
      const status = axiosErr?.response?.status;

      if (serverMessage) {
        alert("회원가입 실패: " + serverMessage);
      } else if (status === 409) {
        alert("이미 가입된 이메일입니다.");
      } else if (status === 400) {
        alert("입력 정보를 다시 확인해주세요.");
      } else if (err instanceof Error) {
        alert("회원가입 실패: " + err.message);
      } else {
        alert("회원가입 중 오류가 발생했습니다.");
      }
    } finally {
      setSignupLoading(false);
    }
  };

  const labelFor = (field: keyof typeof formData) => {
    switch (field) {
      case "email":
        return "이메일";
      case "verificationCode":
        return "인증번호";
      case "password":
        return "비밀번호";
      case "passwordConfirm":
        return "비밀번호확인";
      case "name":
        return "이름";
      case "phone":
        return "연락처";
      case "birthdate":
        return "생년월일";
      case "country":
        return "국적";
      default:
        return "";
    }
  };

  const handleBlur = (field: keyof typeof formData) => {
  setErrors(prev => {
    const next = { ...prev };

    if (field === "email") {
      next.email = validateEmail(formData.email);
    } else if (field === "password") {
      next.password = validatePassword(formData.password);
    } else if (field === "passwordConfirm") {
      next.passwordConfirm = validatePasswordConfirm(
        formData.password,
        formData.passwordConfirm
      );
    } else if (field === "birthdate") {
      next.birthdate = validateBirthdate(
        formData.birthdate,
        formData.birthdateSecond
      );
    }

    return next;
    });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#f1f5f9] p-4">
      <div className="w-full max-w-[420px] bg-white shadow-xl py-10 px-8 flex flex-col gap-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <h1 className="text-[24px] font-semibold text-black mb-1">회원가입</h1>

          {/* 이메일 */}
          <div>
            <input
              type="email"
              placeholder="이메일"
              value={formData.email}
              onChange={e => handleInputChange("email", e.target.value)}
              className={inputClass}
              onBlur={() => handleBlur("email")}
            />
            {errors.email && (
              <p className="mt-1 text-xs text-red-600">{errors.email}</p>
            )}
          </div>

          {/* 인증번호 + 버튼 */}
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="인증번호입력"
              value={formData.verificationCode}
              onChange={e => handleInputChange("verificationCode", e.target.value)}
              className={inputClass + " flex-1"}
              disabled={emailVerified}
            />
            {!emailVerified ? (
              <button
                type="button"
                onClick={verificationSent ? handleConfirmVerification : handleVerification}
                disabled={verificationLoading}
                className="px-4 py-3 rounded-[10px] bg-[#0E588D] text-white text-sm font-medium whitespace-nowrap hover:bg-[#0c4a73] transition-colors disabled:opacity-60"
              >
                {verificationLoading
                  ? "처리 중..."
                  : verificationSent
                  ? "인증확인"
                  : "인증받기"}
              </button>
            ) : (
              <div className="px-4 py-3 rounded-[10px] bg-green-100 text-green-700 text-sm font-medium whitespace-nowrap flex items-center">
                ✓ 인증완료
              </div>
            )}
          </div>
          {verificationSent && !emailVerified && (
            <p className="text-xs text-blue-600 mt-1">
              이메일로 발송된 인증코드를 입력 후 '인증확인' 버튼을 눌러주세요.
            </p>
          )}

          {/* 비밀번호 */}
          <div>
            <input
              type="password"
              placeholder="비밀번호(영문,숫자,특수문자 포함 8~12자)"
              value={formData.password}
              onChange={e => handleInputChange("password", e.target.value)}
              className={inputClass}
              maxLength={12}
              onBlur={() => handleBlur("password")}
            />
            {errors.password && (
              <p className="mt-1 text-xs text-red-600">{errors.password}</p>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div>
            <input
              type="password"
              placeholder="비밀번호확인"
              value={formData.passwordConfirm}
              onChange={e =>
                handleInputChange("passwordConfirm", e.target.value)
              }
              maxLength={12}
              className={inputClass}
              onBlur={() => handleBlur("passwordConfirm")}
            />
            {errors.passwordConfirm && (
              <p className="mt-1 text-xs text-red-600">
                {errors.passwordConfirm}
              </p>
            )}
          </div>

          {/* 이름 */}
          <input
            type="text"
            placeholder="이름"
            value={formData.name}
            onChange={e => handleInputChange("name", e.target.value)}
            className={inputClass}
          />

          {/* 전화번호 */}
          <input
            type="tel"
            placeholder="전화번호( - 제외)"
            value={formData.phone}
            onChange={e => handleInputChange("phone", e.target.value)}
            className={inputClass}
          />

          {/* 생년월일 + 성별 한 자리 */}
          <div>
            <div className="flex gap-2">
              <div className="flex-1">
                <input
                  type="text"
                  placeholder="생년월일(6자리)"
                  value={formData.birthdate}
                  onChange={e =>
                    handleInputChange("birthdate", e.target.value)
                  }
                  maxLength={6}
                  className={inputClass}
                  onBlur={() => handleBlur("birthdate")}
                />
              </div>
              <div className="w-[40px]">
                <input
                  type="text"
                  value={formData.birthdateSecond}
                  onChange={e =>
                    handleInputChange("birthdateSecond", e.target.value)
                  }
                  onBlur={() => handleBlur("birthdate")}
                  maxLength={1}
                  className="w-full h-full px-2 py-3 rounded-[10px] bg-[#F0F0F0] text-center text-black text-sm outline-none focus:ring-2 focus:ring-[#0E588D]/30"
                />
              </div>
              <div className="flex items-center justify-center text-sm text-black">
                * * * * * *
              </div>
            </div>
            {errors.birthdate && (
              <p className="mt-1 text-xs text-red-600">{errors.birthdate}</p>
            )}
          </div>

          {/* 국적 선택 */}
          <div className="flex items-center gap-4">
            <span className="min-w-[40px] text-sm font-medium text-black">
              국적
            </span>
            <div className="relative flex-1">
              <button
                type="button"
                onClick={() => setShowCountryDropdown(prev => !prev)}
                className="w-full h-[44px] flex items-center justify-between border border-black bg-white rounded-[6px] px-4 text-sm hover:bg-gray-50 transition-colors"
              >
                <span className="text-black">
                  {formData.country || "나라 선택"}
                </span>
                <span className="text-black text-xs">▼</span>
              </button>
              {showCountryDropdown && (
                <div className="absolute top-full left-0 w-full mt-1 bg-white border border-black shadow-lg z-10 max-h-[200px] overflow-y-auto text-sm">
                  {[
                    "대한민국",
                    "미국",
                    "일본",
                    "중국",
                    "영국",
                    "프랑스",
                    "독일",
                  ].map(country => (
                    <button
                      key={country}
                      type="button"
                      onClick={() => {
                        handleInputChange("country", country);
                        setShowCountryDropdown(false);
                      }}
                      className="w-full px-4 py-2 text-left hover:bg-gray-100"
                    >
                      {country}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          <button
            type="submit"
            disabled={signupLoading}
            className="w-full mt-2 py-3 rounded-[10px] bg-[#0E588D] text-white text-lg font-medium hover:bg-[#0c4a73] transition-colors disabled:opacity-60"
          >
            {signupLoading ? "가입 중..." : "회원가입"}
          </button>
        </form>
      </div>
    </div>
  );
}
