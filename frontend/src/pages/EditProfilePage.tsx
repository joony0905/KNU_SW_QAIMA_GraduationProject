import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { User } from "lucide-react";
import { getMyProfile, updateMyProfile } from "../api/user";
import { getApiErrorMessage } from "../utils/errorMessage";
import { setUser } from "../api/userStore";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors disabled:opacity-60 disabled:cursor-not-allowed";

const labelClass = "block text-sm font-medium text-ink-2 mb-1.5";

// 생년월일은 앞 6자리(YYMMDD)만 화면에 표기한다.
const formatBirthdate = (v: string): string => {
  if (/^\d{6,7}$/.test(v)) return `${v.slice(0, 2)}.${v.slice(2, 4)}.${v.slice(4, 6)}`;
  return v || "-";
};

const formatGender = (gender: string, birthdate: string): string => {
  if (gender === "male") return "남성";
  if (gender === "female") return "여성";
  const digit = birthdate.replace(/[^0-9]/g, "").charAt(6);
  if (digit === "1" || digit === "3") return "남성";
  if (digit === "2" || digit === "4") return "여성";
  return "-";
};

function Card({
  icon: Icon,
  title,
  desc,
  children,
}: {
  icon: React.ElementType;
  title: string;
  desc?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="w-full rounded-2xl border border-line bg-surface shadow-card">
      <div className="flex items-start gap-3 px-5 sm:px-6 pt-5 pb-4 border-b border-line">
        <div className="shrink-0 w-9 h-9 grid place-items-center rounded-xl bg-accent-soft text-accent">
          <Icon size={17} />
        </div>
        <div>
          <h2 className="text-base font-bold text-ink tracking-tight">
            {title}
          </h2>
          {desc && <p className="mt-0.5 text-xs text-ink-3">{desc}</p>}
        </div>
      </div>
      <div className="px-5 sm:px-6 py-5 flex flex-col gap-4">{children}</div>
    </section>
  );
}

export default function EditProfilePage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState(""); // 아이디 = 이메일, 변경 불가
  const [birthdate, setBirthdate] = useState(""); // 변경 불가
  const [gender, setGender] = useState(""); // 변경 불가

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 초기 변경 감지를 위해 원본 값을 보관
  const [initial, setInitial] = useState({ name: "", phone: "" });

  useEffect(() => {
    let alive = true;
    getMyProfile()
      .then((p) => {
        if (!alive) return;
        setName(p.name ?? "");
        setPhone(p.phone ?? "");
        setEmail(p.email ?? "");
        setBirthdate(p.birthdate ?? "");
        setGender(p.gender ?? "");
        setInitial({ name: p.name ?? "", phone: p.phone ?? "" });
      })
      .catch((e) => {
        if (alive) setLoadError(getApiErrorMessage(e, "내 정보를 불러오지 못했습니다."));
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const handlePhoneChange = (v: string) => setPhone(v.replace(/[^0-9]/g, ""));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);

    const trimmedName = name.trim();
    if (!trimmedName) {
      setSaveError("이름을 입력해주세요.");
      return;
    }
    if (phone && phone.length < 9) {
      setSaveError("전화번호를 정확히 입력해주세요.");
      return;
    }

    // 바뀐 필드만 전송 (백엔드는 보낸 필드만 반영)
    const payload: { name?: string; phone?: string } = {};
    if (trimmedName !== initial.name) payload.name = trimmedName;
    if (phone !== initial.phone) payload.phone = phone;

    if (Object.keys(payload).length === 0) {
      navigate("/setting");
      return;
    }

    setSaving(true);
    try {
      const updated = await updateMyProfile(payload);
      // 헤더/설정 화면이 참조하는 사용자 캐시도 갱신
      setUser({ email: updated.email, name: updated.name });
      navigate("/setting");
    } catch (err) {
      // phone 중복 시 백엔드: 400 "이미 사용 중인 전화번호입니다."
      setSaveError(getApiErrorMessage(err, "개인정보 수정에 실패했습니다."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg ml-[84px]">
      <form
        onSubmit={handleSubmit}
        className="qaima-stagger max-w-2xl mx-auto px-4 sm:px-6 py-5 sm:py-7 flex flex-col gap-5"
      >
        <header>
          <h1 className="text-2xl sm:text-3xl font-bold text-ink tracking-tighter">
            개인정보 수정
          </h1>
        </header>

        {loading ? (
          <p className="text-sm text-ink-3 py-10 text-center animate-pulse">
            내 정보를 불러오는 중...
          </p>
        ) : loadError ? (
          <p className="text-sm text-danger py-10 text-center">{loadError}</p>
        ) : (
          <>
            <Card
              icon={User}
              title="기본정보"
              desc="이름과 연락처를 수정할 수 있습니다"
            >
              <div>
                <label className={labelClass}>아이디(이메일)</label>
                <input className={inputClass} value={email} disabled />
                <p className="mt-1.5 text-xs text-ink-3">
                  아이디로 사용되는 이메일은 변경할 수 없습니다.
                </p>
              </div>
              <div>
                <label className={labelClass}>생년월일</label>
                <input
                  className={inputClass}
                  value={formatBirthdate(birthdate)}
                  disabled
                />
              </div>
              <div>
                <label className={labelClass}>성별</label>
                <input
                  className={inputClass}
                  value={formatGender(gender, birthdate)}
                  disabled
                />
              </div>
              <div>
                <label className={labelClass}>이름</label>
                <input
                  className={inputClass}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="이름"
                />
              </div>
              <div>
                <label className={labelClass}>전화번호</label>
                <input
                  className={inputClass}
                  value={phone}
                  onChange={(e) => handlePhoneChange(e.target.value)}
                  inputMode="numeric"
                  placeholder="전화번호 ( - 제외)"
                />
              </div>
            </Card>

            {saveError && (
              <p className="text-sm text-danger">{saveError}</p>
            )}

            <div className="flex items-center justify-end">
              <button
                type="submit"
                disabled={saving}
                className="px-5 py-2.5 rounded-lg bg-accent text-white text-sm font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
              >
                {saving ? "저장 중..." : "변경 저장"}
              </button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
