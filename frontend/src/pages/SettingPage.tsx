import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
  fetchWatchlist,
  deleteWatchlistItem,
  DEFAULT_WATCHLIST_ID,
} from "../api/watchlist";
import type { WatchlistItem } from "../types/watchlist";
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
} from "lucide-react";
import { useTheme } from "../hooks/useTheme";

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

  const [investLevel, setInvestLevel] = useState<
    "초급자" | "중급자" | "고급자"
  >("초급자");
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([]);
  const [wlLoading, setWlLoading] = useState(true);
  const [wlError, setWlError] = useState<string | null>(null);
  const [isEditingWatchlist, setIsEditingWatchlist] = useState(false);
  const [language, setLanguage] = useState<"한국어" | "English">("한국어");

  useEffect(() => {
    let alive = true;
    setWlLoading(true);
    setWlError(null);
    fetchWatchlist(DEFAULT_WATCHLIST_ID)
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
    ["이름", "홍길동"],
    ["아이디(이메일)", "honggildong123@naver.com"],
    ["전화번호", "010 1234 5678"],
    ["생년월일", "1999년 99월 99일"],
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
        >
          <SettingSelect
            value={investLevel}
            options={["초급자", "중급자", "고급자"] as const}
            onChange={setInvestLevel}
          />
        </SettingCard>

        {/* 투자성향 */}
        <SettingCard
          icon={TrendingUp}
          title="투자성향"
          desc="투자 설문 결과로 자동 설정되며, 설문을 다시 하면 변경됩니다"
          action={
            <button onClick={() => navigate("/survey")} className={ghostBtn}>
              <RotateCcw size={14} />
              설문 다시하기
            </button>
          }
        >
          <div className="inline-flex items-center px-3.5 py-2 rounded-lg bg-accent-soft text-accent text-sm font-semibold">
            Aggressive · 수익 우선, 손실 감수
          </div>
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
