import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { User, Lock, ShieldCheck } from "lucide-react";

const inputClass =
  "w-full px-4 py-3 rounded-lg bg-bg-sunk border border-line text-ink placeholder:text-ink-4 text-sm outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent transition-colors disabled:opacity-60 disabled:cursor-not-allowed";

const labelClass = "block text-sm font-medium text-ink-2 mb-1.5";

// 비밀번호 규칙 — 회원가입과 동일
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

  // 데모 환경: 기존 값으로 프리필 (실제 프로필 API 미연동)
  const [name, setName] = useState("홍길동");
  const [phone, setPhone] = useState("01012345678");
  const email = "honggildong123@naver.com"; // 아이디는 변경 불가

  // 비밀번호 입력란은 항상 비워둔 채 시작한다
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  // 저장하려면 본인 확인용 현재 비밀번호를 반드시 1회 입력해야 한다
  const [currentPassword, setCurrentPassword] = useState("");

  const [saving, setSaving] = useState(false);

  const handlePhoneChange = (v: string) =>
    setPhone(v.replace(/[^0-9]/g, ""));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      alert("이름을 입력해주세요.");
      return;
    }
    if (phone.length < 9) {
      alert("전화번호를 정확히 입력해주세요.");
      return;
    }

    const wantsPasswordChange =
      newPassword.length > 0 || newPasswordConfirm.length > 0;

    if (wantsPasswordChange) {
      const pwErr = validatePassword(newPassword);
      if (pwErr) {
        alert(pwErr);
        return;
      }
      if (newPassword !== newPasswordConfirm) {
        alert("새 비밀번호가 일치하지 않습니다.");
        return;
      }
    }

    // 타인이 함부로 변경하지 못하도록, 저장 시 현재 비밀번호를 1회 입력받는다
    if (!currentPassword) {
      alert("본인 확인을 위해 현재 비밀번호를 입력해주세요.");
      return;
    }

    setSaving(true);
    // 데모: 실제 저장 API 미연동 — 입력 검증 후 완료 처리
    setTimeout(() => {
      setSaving(false);
      alert(
        wantsPasswordChange
          ? "개인정보와 비밀번호가 변경되었습니다."
          : "개인정보가 변경되었습니다.",
      );
      navigate("/setting");
    }, 400);
  };

  return (
    <div className="min-h-screen bg-bg ml-[84px]">
      <form
        onSubmit={handleSubmit}
        className="qaima-stagger max-w-2xl mx-auto px-4 sm:px-6 py-5 sm:py-7 flex flex-col gap-5"
      >
        {/* 헤더 */}
        <header>
          <h1 className="text-2xl sm:text-3xl font-bold text-ink tracking-tighter">
            개인정보 수정
          </h1>
        </header>

        {/* 기본정보 */}
        <Card icon={User} title="기본정보" desc="이름과 연락처를 수정할 수 있습니다">
          <div>
            <label className={labelClass}>아이디(이메일)</label>
            <input className={inputClass} value={email} disabled />
            <p className="mt-1.5 text-xs text-ink-3">
              아이디로 사용되는 이메일은 변경할 수 없습니다.
            </p>
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

        {/* 비밀번호 변경 */}
        <Card
          icon={Lock}
          title="비밀번호 변경"
          desc="변경하지 않으려면 비워두세요"
        >
          <div>
            <label className={labelClass}>새 비밀번호</label>
            <input
              type="password"
              className={inputClass}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              maxLength={12}
              autoComplete="new-password"
              placeholder="영문, 숫자, 특수문자 포함 8~12자"
            />
          </div>
          <div>
            <label className={labelClass}>새 비밀번호 확인</label>
            <input
              type="password"
              className={inputClass}
              value={newPasswordConfirm}
              onChange={(e) => setNewPasswordConfirm(e.target.value)}
              maxLength={12}
              autoComplete="new-password"
              placeholder="새 비밀번호 다시 입력"
            />
          </div>
        </Card>

        {/* 본인 확인 */}
        <Card
          icon={ShieldCheck}
          title="본인 확인"
          desc="타인의 무단 변경을 막기 위해 저장 시 현재 비밀번호가 필요합니다"
        >
          <div>
            <label className={labelClass}>
              현재 비밀번호 <span className="text-danger">*</span>
            </label>
            <input
              type="password"
              className={inputClass}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              placeholder="현재 비밀번호를 입력하세요"
            />
          </div>
        </Card>

        {/* 액션 */}
        <div className="flex items-center justify-end">
          <button
            type="submit"
            disabled={saving}
            className="px-5 py-2.5 rounded-lg bg-accent text-white text-sm font-semibold tracking-tight hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {saving ? "저장 중..." : "변경 저장"}
          </button>
        </div>
      </form>
    </div>
  );
}
