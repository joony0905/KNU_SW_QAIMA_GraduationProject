import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronDown } from "lucide-react";
import { completeSocialProfile, getMyProfile } from "../api/user";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors";

const COUNTRY_OPTIONS = ["대한민국", "미국", "일본", "중국", "영국", "프랑스", "독일", "싱가포르", "홍콩", "캐나다", "호주", "기타"];
const numericControlKeys = new Set(["Backspace", "Delete", "Tab", "ArrowLeft", "ArrowRight", "Home", "End"]);

export default function SocialProfileCompletePage() {
  const navigate = useNavigate();
  const countryRef = useRef<HTMLDivElement | null>(null);
  const [form, setForm] = useState({ name: "", phone: "", birthdate: "", birthdateSecond: "", country: "" });
  const [showCountryDropdown, setShowCountryDropdown] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getMyProfile()
      .then((profile) => {
        if (profile.status !== "profile_required") {
          navigate("/feature/1", { replace: true });
          return;
        }
        setForm((prev) => ({ ...prev, name: profile.name ?? "" }));
      })
      .catch(() => navigate("/login", { replace: true }));
  }, [navigate]);

  useEffect(() => {
    if (!showCountryDropdown) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (!countryRef.current || countryRef.current.contains(e.target as Node)) return;
      setShowCountryDropdown(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showCountryDropdown]);

  const handleChange = (field: keyof typeof form, value: string) => {
    if (field === "phone") value = value.replace(/[^0-9]/g, "");
    if (field === "birthdate") value = value.replace(/[^0-9]/g, "").slice(0, 6);
    if (field === "birthdateSecond") value = value.replace(/[^0-9]/g, "").slice(0, 1);
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleNumericKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.ctrlKey || e.metaKey || numericControlKeys.has(e.key)) return;
    if (!/^[0-9]$/.test(e.key)) e.preventDefault();
  };

  const validateBirthdate = () => {
    if (!/^\d{6}$/.test(form.birthdate)) return false;
    if (!/^[1-4]$/.test(form.birthdateSecond)) return false;
    const mm = Number(form.birthdate.slice(2, 4));
    const dd = Number(form.birthdate.slice(4, 6));
    if (mm < 1 || mm > 12) return false;
    const yy = Number(form.birthdate.slice(0, 2));
    const maxDay = [31, yy % 4 === 0 ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mm - 1];
    return dd >= 1 && dd <= maxDay;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.phone || !form.country || !validateBirthdate()) {
      alert("추가정보를 모두 올바르게 입력해 주세요.");
      return;
    }

    setLoading(true);
    try {
      await completeSocialProfile({
        name: form.name.trim(),
        phone: form.phone,
        birthdate: form.birthdate + form.birthdateSecond,
        country: form.country,
      });
      navigate("/feature/1", { replace: true });
    } catch (err: unknown) {
      if (err instanceof Error) alert("추가정보 저장 실패: " + err.message);
      else alert("추가정보 저장 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg p-4 ml-[84px]">
      <div className="w-full max-w-[460px] bg-surface border border-line rounded-2xl shadow-card p-10 flex flex-col gap-7">
        <div>
          <h1 className="text-3xl font-bold text-ink tracking-tighter">추가정보 입력</h1>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <input
            type="text"
            placeholder="이름"
            value={form.name}
            onChange={(e) => handleChange("name", e.target.value)}
            className={inputClass}
          />
          <input
            type="tel"
            placeholder="전화번호 ( - 제외)"
            value={form.phone}
            onChange={(e) => handleChange("phone", e.target.value)}
            inputMode="numeric"
            className={inputClass}
          />
          <div>
            <div className="flex gap-2 items-stretch">
              <input
                type="text"
                placeholder="생년월일"
                value={form.birthdate}
                onChange={(e) => handleChange("birthdate", e.target.value)}
                onKeyDown={handleNumericKeyDown}
                maxLength={6}
                inputMode="numeric"
                pattern="[0-9]*"
                className={inputClass + " min-w-0 flex-[1_1_0%]"}
              />
              <input
                type="text"
                placeholder="1"
                value={form.birthdateSecond}
                onChange={(e) => handleChange("birthdateSecond", e.target.value)}
                onKeyDown={handleNumericKeyDown}
                maxLength={1}
                inputMode="numeric"
                pattern="[0-9]*"
                className={inputClass + " !w-[52px] flex-none text-center"}
              />
              <div className="flex items-center text-ink-4 text-sm font-mono tabular tracking-widest select-none">······</div>
            </div>
          </div>
          <div ref={countryRef} className="relative">
            <button
              type="button"
              onClick={() => setShowCountryDropdown((prev) => !prev)}
              className="w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line flex items-center justify-between text-sm hover:bg-surface-2 transition-colors"
            >
              <span className={form.country ? "text-ink" : "text-ink-4"}>{form.country || "국적 선택"}</span>
              <ChevronDown size={16} className="text-ink-3 pointer-events-none" />
            </button>
            {showCountryDropdown && (
              <div className="absolute top-full left-0 w-full mt-1.5 bg-surface border border-line rounded-lg shadow-pop z-10 max-h-[220px] overflow-y-auto">
                {COUNTRY_OPTIONS.map((country) => (
                  <button
                    key={country}
                    type="button"
                    onClick={() => {
                      handleChange("country", country);
                      setShowCountryDropdown(false);
                    }}
                    className={`w-full px-4 py-2.5 text-left text-sm hover:bg-bg-sunk first:rounded-t-lg last:rounded-b-lg ${
                      form.country === country ? "bg-accent-soft text-accent font-semibold" : "text-ink"
                    }`}
                  >
                    {country}
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-3 rounded-lg bg-accent text-white text-base font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loading ? "저장 중..." : "가입 완료"}
          </button>
        </form>
      </div>
    </div>
  );
}
