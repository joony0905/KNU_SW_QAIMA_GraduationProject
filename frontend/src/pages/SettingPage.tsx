import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
  fetchWatchlist,
  deleteWatchlistItem,
} from "../api/watchlist";
import type { WatchlistItem } from "../types/watchlist";
import { getMyProfile, getMyRiskProfile, updateMyProfile, updateMyRiskProfile } from "../api/user";
import type { MyProfile } from "../api/user";
import { getApiErrorMessage } from "../utils/errorMessage";
import {
  Sun,
  Moon,
  Pencil,
  RotateCcw,
  X,
  ChevronDown,
  ChevronUp,
  User,
  TrendingUp,
  Star,
  Languages,
  LifeBuoy,
  BookOpen,
} from "lucide-react";
import { useTheme } from "../hooks/useTheme";
import { useDictionary } from "../components/DictContext";
import {
  INVEST_LEVELS,
  toInvestLevel,
  type InvestLevel,
} from "../utils/investLevel";
import {
  RISK_PROFILE_OPTIONS,
  riskProfileOptionForGamma,
  syncFeature3RiskDefaults,
  type RiskProfileLabel,
} from "../utils/riskProfile";

const formatPhone = (v: string): string => {
  const d = (v ?? "").replace(/[^0-9]/g, "");
  if (d.length === 11) return `${d.slice(0, 3)} ${d.slice(3, 7)} ${d.slice(7)}`;
  if (d.length === 10) return `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}`;
  return v || "-";
};

const formatBirthdate = (v: string): string =>
  /^\d{6}$/.test(v ?? "")
    ? `${v.slice(0, 2)}.${v.slice(2, 4)}.${v.slice(4, 6)}`
    : v || "-";

type CardProps = {
  icon: React.ElementType;
  title: string;
  desc?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
};

function SettingCard({ icon: Icon, title, desc, action, children }: CardProps) {
  return (
    <section className="w-full rounded-2xl border border-line bg-surface shadow-card">
      <div className="flex items-start justify-between gap-4 px-5 sm:px-6 pt-5 pb-4 border-b border-line">
        <div className="flex items-start gap-3">
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
        {action}
      </div>
      <div className="px-5 sm:px-6 py-5">{children}</div>
    </section>
  );
}

function SettingSelect<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: readonly T[];
  onChange: (next: T) => void;
}) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number; width: number } | null>(null);
  const ref = useRef<HTMLDivElement | null>(null);
  const popRef = useRef<HTMLDivElement | null>(null);

  // 트리거 좌표 계산 (transform 스태킹 컨텍스트에 가리지 않게 portal + fixed)
  const updateCoords = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setCoords({ top: rect.bottom + 4, left: rect.left, width: rect.width });
  }, []);

  useEffect(() => {
    if (!open) return;
    updateCoords();
    window.addEventListener("scroll", updateCoords, true);
    window.addEventListener("resize", updateCoords);
    return () => {
      window.removeEventListener("scroll", updateCoords, true);
      window.removeEventListener("resize", updateCoords);
    };
  }, [open, updateCoords]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        !ref.current?.contains(target) &&
        !popRef.current?.contains(target)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  return (
    <div ref={ref} className="relative w-full sm:w-56">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={`inline-flex items-center justify-between gap-2 w-full pl-3.5 pr-3 py-2.5 rounded-lg bg-bg-sunk border text-sm text-ink transition-colors ${
          open ? "border-accent" : "border-line"
        }`}
      >
        <span className="truncate">{value}</span>
        {open ? (
          <ChevronUp size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />
        ) : (
          <ChevronDown size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />
        )}
      </button>
      {open && coords &&
        createPortal(
          <div
            ref={popRef}
            style={{
              position: "fixed",
              top: coords.top,
              left: coords.left,
              width: coords.width,
            }}
            className="z-[200] bg-surface border border-line rounded-lg shadow-pop overflow-hidden"
          >
            {options.map((opt) => (
              <button
                key={opt}
                type="button"
                onClick={() => {
                  onChange(opt);
                  setOpen(false);
                }}
                className={`w-full text-left px-3.5 py-2.5 text-sm transition-colors ${
                  opt === value
                    ? "bg-accent-soft text-accent font-semibold"
                    : "text-ink hover:bg-bg-sunk"
                }`}
              >
                {opt}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </div>
  );
}

export default function SettingPage() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const {
    glossaryHover,
    setGlossaryHover,
    investLevel,
    setInvestLevel,
  } = useDictionary();

  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [savingPref, setSavingPref] = useState(false);
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [wlLoading, setWlLoading] = useState(true);
  const [wlError, setWlError] = useState<string | null>(null);
  const [isEditingWatchlist, setIsEditingWatchlist] = useState(false);
  const [language, setLanguage] = useState<"한국어" | "English">("한국어");
  const [riskProfileLabel, setRiskProfileLabel] = useState<RiskProfileLabel | "미설정">("미설정");
  const [riskProfileError, setRiskProfileError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setWlLoading(true);
    setWlError(null);
    fetchWatchlist()
      .then((items) => {
        if (alive) setWatchlist(items);
      })
      .catch(() => {
        if (alive) setWlError("관심종목을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (alive) setWlLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    Promise.all([getMyProfile(), getMyRiskProfile()])
      .then(([p, riskProfile]) => {
        if (!alive) return;
        setProfile(p);
        setInvestLevel(toInvestLevel(p.experience));
        setGlossaryHover(p.glossaryHover);
        const option = riskProfileOptionForGamma(riskProfile.defaultRiskGamma);
        setRiskProfileLabel(option?.label ?? "미설정");
      })
      .catch((e) => {
        if (alive)
          setProfileError(getApiErrorMessage(e, "내 정보를 불러오지 못했습니다."));
      });
    return () => {
      alive = false;
    };
  }, [setGlossaryHover, setInvestLevel]);

  // 투자레벨 = 백엔드 experience(자유 문자열). 선택 즉시 저장.
  const handleInvestLevelChange = async (next: InvestLevel) => {
    const prev = investLevel;
    setInvestLevel(next);
    try {
      await updateMyProfile({ experience: next });
    } catch (e) {
      setInvestLevel(prev);
      alert(getApiErrorMessage(e, "투자레벨 저장에 실패했습니다."));
    }
  };

  const handleRiskProfileChange = async (next: RiskProfileLabel | "미설정") => {
    if (next === "미설정") return;
    const option = RISK_PROFILE_OPTIONS.find((item) => item.label === next);
    if (!option) return;
    const prev = riskProfileLabel;
    setRiskProfileLabel(next);
    setRiskProfileError(null);
    try {
      await updateMyRiskProfile({ defaultRiskGamma: option.gamma });
      syncFeature3RiskDefaults(option.gamma);
    } catch (e) {
      setRiskProfileLabel(prev);
      setRiskProfileError(getApiErrorMessage(e, "투자성향 저장에 실패했습니다."));
    }
  };

  // 용어 hover 설명 표시 여부 = 백엔드 glossaryHover. 토글 즉시 저장 + 앱 전역 반영.
  const handleToggleGlossaryHover = async () => {
    if (savingPref) return;
    const next = !glossaryHover;
    setGlossaryHover(next);
    setSavingPref(true);
    try {
      await updateMyProfile({ glossaryHover: next });
    } catch (e) {
      setGlossaryHover(!next);
      alert(getApiErrorMessage(e, "환경설정 저장에 실패했습니다."));
    } finally {
      setSavingPref(false);
    }
  };

  const handleDeleteWatchlistItem = async (item: WatchlistItem) => {
    const prev = watchlist;
    // 낙관적 제거 후 실패 시 롤백
    setWatchlist((list) =>
      list.filter((w) => w.watchlistItemId !== item.watchlistItemId),
    );
    try {
      await deleteWatchlistItem(item.watchlistItemId);
    } catch {
      setWatchlist(prev);
      alert("관심종목 삭제에 실패했습니다.");
    }
  };

  const labelOf = (item: WatchlistItem) =>
    item.stockName ?? (item.stockId != null ? `#${item.stockId}` : "종목");

  const basicInfo: [string, string][] = [
    ["이름", profile?.name || "-"],
    ["아이디(이메일)", profile?.email || "-"],
    ["전화번호", profile ? formatPhone(profile.phone) : "-"],
    ["생년월일", profile ? formatBirthdate(profile.birthdate) : "-"],
  ];

  const ghostBtn =
    "inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-line bg-surface text-sm font-medium text-ink-2 hover:bg-bg-sunk transition-colors";

  return (
    <div className="min-h-screen bg-bg ml-[84px]">
      <div className="qaima-stagger max-w-3xl mx-auto px-4 sm:px-6 py-5 sm:py-7 flex flex-col gap-5">
        {/* 헤더 */}
        <header className="flex items-end justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Account · Settings
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              내정보
            </h1>
          </div>
          <button
            onClick={toggle}
            aria-label={theme === "dark" ? "라이트 모드" : "다크 모드"}
            className="w-9 h-9 grid place-items-center rounded-xl bg-surface border border-line text-ink-2 shadow-card hover:bg-bg-sunk transition-colors"
          >
            {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </header>

        {/* 기본정보 */}
        <SettingCard
          icon={User}
          title="기본정보"
          desc="계정에 등록된 정보입니다"
          action={
            <button
              onClick={() => navigate("/setting/edit")}
              className={ghostBtn}
            >
              <Pencil size={14} />
              개인정보 수정
            </button>
          }
        >
          {profileError && (
            <p className="text-sm text-danger mb-3">{profileError}</p>
          )}
          <dl className="divide-y divide-line">
            {basicInfo.map(([label, value]) => (
              <div
                key={label}
                className="flex items-center justify-between gap-4 py-2.5 first:pt-0 last:pb-0"
              >
                <dt className="text-sm text-ink-3">{label}</dt>
                <dd className="text-sm font-medium text-ink text-right">
                  {value}
                </dd>
              </div>
            ))}
          </dl>
        </SettingCard>

        {/* 투자레벨 */}
        <SettingCard
          icon={TrendingUp}
          title="투자레벨"
          desc="투자 설문 결과로 자동 설정되며, 직접 변경할 수 있습니다"
          action={
            <button
              onClick={() => navigate("/invest-level-survey")}
              className={ghostBtn}
            >
              <RotateCcw size={14} />
              설문 다시하기
            </button>
          }
        >
          <SettingSelect
            value={investLevel}
            options={INVEST_LEVELS}
            onChange={handleInvestLevelChange}
          />
        </SettingCard>

        {/* 투자성향 */}
        <SettingCard
          icon={TrendingUp}
          title="투자성향"
          desc="투자 설문 결과로 자동 설정되며, 직접 변경할 수 있습니다"
          action={
            <button onClick={() => navigate("/survey")} className={ghostBtn}>
              <RotateCcw size={14} />
              설문 다시하기
            </button>
          }
        >
          <div className="flex flex-col gap-2">
            <SettingSelect
              value={riskProfileLabel}
              options={["미설정", ...RISK_PROFILE_OPTIONS.map((option) => option.label)] as const}
              onChange={handleRiskProfileChange}
            />
            {riskProfileLabel !== "미설정" && (
              <p className="text-xs text-ink-3">
                {RISK_PROFILE_OPTIONS.find((option) => option.label === riskProfileLabel)?.description}
              </p>
            )}
            {riskProfileError && (
              <p className="text-sm text-danger">{riskProfileError}</p>
            )}
          </div>
        </SettingCard>

        {/* 용어 설명 hover */}
        <SettingCard
          icon={BookOpen}
          title="용어 설명 미리보기"
          desc="분석 화면의 용어에 마우스를 올리면 클릭 없이 설명을 보여줍니다"
        >
          <button
            type="button"
            onClick={handleToggleGlossaryHover}
            disabled={savingPref}
            aria-pressed={glossaryHover}
            className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors disabled:opacity-50 ${
              glossaryHover ? "bg-accent" : "bg-line-strong"
            }`}
          >
            <span
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                glossaryHover ? "translate-x-6" : "translate-x-1"
              }`}
            />
          </button>
          <span className="ml-3 text-sm text-ink-2 align-middle">
            {glossaryHover ? "켜짐" : "꺼짐"}
          </span>
        </SettingCard>

        {/* 관심종목 */}
        <SettingCard
          icon={Star}
          title="관심종목"
          desc={wlLoading ? "불러오는 중..." : `총 ${watchlist.length}개`}
          action={
            watchlist.length > 0 ? (
              <button
                onClick={() => setIsEditingWatchlist((prev) => !prev)}
                className={ghostBtn}
              >
                {isEditingWatchlist ? "완료" : "선택삭제"}
              </button>
            ) : undefined
          }
        >
          {wlLoading ? (
            <p className="text-sm text-ink-3 text-center py-6">
              관심종목을 불러오는 중입니다...
            </p>
          ) : wlError ? (
            <p className="text-sm text-danger text-center py-6">{wlError}</p>
          ) : watchlist.length === 0 ? (
            <p className="text-sm text-ink-3 text-center py-6">
              관심 종목이 없습니다.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {watchlist.map((item) => (
                <span
                  key={item.watchlistItemId}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium ${
                    isEditingWatchlist
                      ? "bg-danger/10 text-danger"
                      : "bg-bg-sunk text-ink"
                  }`}
                >
                  {labelOf(item)}
                  {isEditingWatchlist && (
                    <button
                      onClick={() => handleDeleteWatchlistItem(item)}
                      className="hover:opacity-70"
                      aria-label={`${labelOf(item)} 삭제`}
                    >
                      <X size={13} />
                    </button>
                  )}
                </span>
              ))}
            </div>
          )}
        </SettingCard>

        {/* 언어 */}
        <SettingCard
          icon={Languages}
          title="언어 / Language"
          desc="서비스 표시 언어를 선택합니다"
        >
          <SettingSelect
            value={language}
            options={["한국어", "English"] as const}
            onChange={setLanguage}
          />
        </SettingCard>

        {/* 버그제보 */}
        <SettingCard
          icon={LifeBuoy}
          title="버그제보 / 문의"
          desc="이용 중 불편한 점을 알려주세요"
        >
          <a
            href="mailto:9aima@gmail.com"
            className="text-sm font-medium text-accent hover:underline"
          >
            9aima@gmail.com
          </a>
        </SettingCard>
      </div>
    </div>
  );
}
