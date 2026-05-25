import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { User } from "lucide-react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation("editProfilePage");

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [birthdate, setBirthdate] = useState("");
  const [gender, setGender] = useState("");

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [initial, setInitial] = useState({ name: "", phone: "" });

  const formatGender = (g: string, bd: string): string => {
    if (g === "male") return t("gender.male");
    if (g === "female") return t("gender.female");
    const digit = bd.replace(/[^0-9]/g, "").charAt(6);
    if (digit === "1" || digit === "3") return t("gender.male");
    if (digit === "2" || digit === "4") return t("gender.female");
    return "-";
  };

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
        if (alive) setLoadError(getApiErrorMessage(e, t("errors.loadFailed")));
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [t]);

  const handlePhoneChange = (v: string) => setPhone(v.replace(/[^0-9]/g, ""));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);

    const trimmedName = name.trim();
    if (!trimmedName) {
      setSaveError(t("errors.nameRequired"));
      return;
    }
    if (phone && phone.length < 9) {
      setSaveError(t("errors.phoneInvalid"));
      return;
    }

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
      setUser({ email: updated.email, name: updated.name });
      navigate("/setting");
    } catch (err) {
      setSaveError(getApiErrorMessage(err, t("errors.saveFailed")));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      <form
        onSubmit={handleSubmit}
        className="qaima-stagger max-w-2xl mx-auto px-4 sm:px-6 py-5 sm:py-7 flex flex-col gap-5"
      >
        <header>
          <h1 className="text-2xl sm:text-3xl font-bold text-ink tracking-tighter">
            {t("title")}
          </h1>
        </header>

        {loading ? (
          <p className="text-sm text-ink-3 py-10 text-center animate-pulse">
            {t("loading")}
          </p>
        ) : loadError ? (
          <p className="text-sm text-danger py-10 text-center">{loadError}</p>
        ) : (
          <>
            <Card
              icon={User}
              title={t("card.title")}
              desc={t("card.desc")}
            >
              <div>
                <label className={labelClass}>{t("labels.email")}</label>
                <input className={inputClass} value={email} disabled />
                <p className="mt-1.5 text-xs text-ink-3">
                  {t("emailHint")}
                </p>
              </div>
              <div>
                <label className={labelClass}>{t("labels.birthdate")}</label>
                <input
                  className={inputClass}
                  value={formatBirthdate(birthdate)}
                  disabled
                />
              </div>
              <div>
                <label className={labelClass}>{t("labels.gender")}</label>
                <input
                  className={inputClass}
                  value={formatGender(gender, birthdate)}
                  disabled
                />
              </div>
              <div>
                <label className={labelClass}>{t("labels.name")}</label>
                <input
                  className={inputClass}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t("placeholders.name")}
                />
              </div>
              <div>
                <label className={labelClass}>{t("labels.phone")}</label>
                <input
                  className={inputClass}
                  value={phone}
                  onChange={(e) => handlePhoneChange(e.target.value)}
                  inputMode="numeric"
                  placeholder={t("placeholders.phone")}
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
                {saving ? t("buttons.saving") : t("buttons.save")}
              </button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
