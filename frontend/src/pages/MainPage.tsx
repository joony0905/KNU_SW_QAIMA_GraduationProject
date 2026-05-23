// src/pages/MainPage.tsx
import { useNavigate } from "react-router-dom";
import { Sun, Moon } from "lucide-react";
import Reveal from "../components/Reveal";
import StockInputBox from "../components/StockInputBox";
import { isLoggedIn } from "../utils/auth";
import { useTheme } from "../hooks/useTheme";
import { logout } from "../api/auth";
import { clearAccessToken } from "../api/tokenStore";
import { clearUser } from "../api/userStore";
import { clearTokenBalance } from "../api/billingStore";

// ───────────────────────────────────────────────────────────────
// Hero AI 비주얼 — 6 streams convergence (Qaima 로고 자리 포함)
// ───────────────────────────────────────────────────────────────
function HeroVisual() {
  // tailwind 토큰 참조 (light/dark 자동 적응)
  // 색상은 dark/light 분기 없이 css 변수 기반으로 작성하기 어려워 하드코딩 + media query 대신
  // 동일 컬러 사용 — 시각적 임팩트는 light 기준으로 잡고 dark에서도 충분히 보임
  const accent = "rgb(var(--color-accent))";
  const accent2 = "rgb(var(--color-accent-ink))";
  const accent3 = "rgb(var(--color-accent) / 0.7)";
  const inkSoft = "rgb(var(--color-ink-4))";
  const surface = "rgb(var(--color-surface))";
  const rise = "rgb(var(--color-rise))";
  const fall = "rgb(var(--color-fall))";
  const gold = "rgb(var(--color-warn))";
  const teal = "rgb(var(--color-success))";

  const target: [number, number] = [340, 240];

  const streams = [
    { from: [110, 80] as [number, number], color: accent, label: "PRICE" },
    { from: [100, 150] as [number, number], color: rise, label: "EARNINGS" },
    { from: [96, 220] as [number, number], color: gold, label: "NEWS" },
    { from: [96, 290] as [number, number], color: teal, label: "MACRO" },
    { from: [100, 360] as [number, number], color: fall, label: "INDUSTRY" },
    { from: [110, 420] as [number, number], color: accent2, label: "VOLUME" },
  ];

  return (
    <div className="relative w-full max-w-[480px] aspect-square">
      <svg viewBox="0 0 480 480" width="100%" height="100%">
        <defs>
          <radialGradient id="hvB-bg" cx="0.7" cy="0.5" r="0.6">
            <stop offset="0%" stopColor={accent} stopOpacity="0.10" />
            <stop offset="100%" stopColor={accent} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="hvB-core" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0%" stopColor={accent2} stopOpacity="0.65" />
            <stop offset="60%" stopColor={accent2} stopOpacity="0.15" />
            <stop offset="100%" stopColor={accent2} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="hvB-core2" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0%" stopColor={accent3} stopOpacity="0.85" />
            <stop offset="100%" stopColor={accent3} stopOpacity="0" />
          </radialGradient>
          {streams.map((s, i) => (
            <linearGradient
              key={i}
              id={`hvB-s${i}`}
              gradientUnits="userSpaceOnUse"
              x1={s.from[0]}
              y1={s.from[1]}
              x2={target[0]}
              y2={target[1]}
            >
              <stop offset="0%" stopColor={s.color} stopOpacity="0.05" />
              <stop offset="40%" stopColor={s.color} stopOpacity="0.5" />
              <stop offset="100%" stopColor={s.color} stopOpacity="1" />
            </linearGradient>
          ))}
          <filter id="hvB-blur" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="2" />
          </filter>
        </defs>

        <circle cx="380" cy="240" r="280" fill="url(#hvB-bg)" />

        {Array.from({ length: 12 }).map((_, j) =>
          Array.from({ length: 14 }).map((_, i) => {
            const cx = 32 + i * 32;
            const cy = 40 + j * 32;
            const d = Math.hypot(cx - target[0], cy - target[1]);
            return (
              <circle
                key={`g-${i}-${j}`}
                cx={cx}
                cy={cy}
                r="0.9"
                fill={inkSoft}
                opacity={Math.max(0.06, 0.35 - d / 700)}
              />
            );
          })
        )}

        {streams.map((s, i) => (
          <g key={`lbl-${i}`}>
            <path
              d={`M ${s.from[0] - 18} ${s.from[1] + 6} l 4 -3 l 4 2 l 4 -4 l 4 1 l 4 -2`}
              fill="none"
              stroke={s.color}
              strokeWidth="1.1"
              strokeLinecap="round"
              opacity="0.5"
            />
            <circle cx={s.from[0]} cy={s.from[1]} r="9" fill="none" stroke={s.color} strokeWidth="1.2" opacity="0.35">
              <animate attributeName="r" from="6" to="14" dur={`${2 + i * 0.15}s`} repeatCount="indefinite" />
              <animate attributeName="opacity" from="0.55" to="0" dur={`${2 + i * 0.15}s`} repeatCount="indefinite" />
            </circle>
            <circle cx={s.from[0]} cy={s.from[1]} r="6" fill={s.color} />
            <circle cx={s.from[0]} cy={s.from[1]} r="2.2" fill={surface} />
            <text
              x={s.from[0] + 14}
              y={s.from[1] + 3}
              textAnchor="start"
              fontSize="10"
              fontWeight="600"
              fill="rgb(var(--color-ink))"
              fontFamily="JetBrains Mono, monospace"
              letterSpacing="0.05em"
            >
              {s.label}
            </text>
          </g>
        ))}

        {streams.map((s, idx) => {
          const [x1, y1] = s.from;
          const [x2, y2] = target;
          const cp1x = x1 + 130 + idx * 6;
          const cp1y = y1;
          const cp2x = x2 - 90;
          const cp2y = y2 + (y1 - y2) * 0.15;
          const d = `M ${x1} ${y1} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${x2} ${y2}`;
          return (
            <g key={`p-${idx}`}>
              <path d={d} fill="none" stroke={s.color} strokeWidth="6" opacity="0.18" filter="url(#hvB-blur)" />
              <path d={d} fill="none" stroke={`url(#hvB-s${idx})`} strokeWidth="2.4" strokeLinecap="round" />
            </g>
          );
        })}

        {streams.flatMap((s, i) =>
          [0, 0.5].map((delay, k) => {
            const path = `M ${s.from[0]} ${s.from[1]} C ${s.from[0] + 130 + i * 6} ${s.from[1]}, ${target[0] - 90} ${target[1] + (s.from[1] - target[1]) * 0.15}, ${target[0]} ${target[1]}`;
            return (
              <g key={`d-${i}-${k}`}>
                <circle r="4" fill={s.color} opacity="0.4" filter="url(#hvB-blur)">
                  <animateMotion dur={`${2.4 + i * 0.18}s`} begin={`${delay * 1.2}s`} repeatCount="indefinite" path={path} />
                </circle>
                <circle r="2.4" fill={s.color}>
                  <animateMotion dur={`${2.4 + i * 0.18}s`} begin={`${delay * 1.2}s`} repeatCount="indefinite" path={path} />
                </circle>
                <circle r="1" fill={surface}>
                  <animateMotion dur={`${2.4 + i * 0.18}s`} begin={`${delay * 1.2}s`} repeatCount="indefinite" path={path} />
                </circle>
              </g>
            );
          })
        )}

        <circle cx={target[0]} cy={target[1]} r="100" fill="url(#hvB-core)" />
        <circle cx={target[0]} cy={target[1]} r="60" fill="url(#hvB-core2)" />

        <g style={{ transformOrigin: `${target[0]}px ${target[1]}px` }}>
          <circle cx={target[0]} cy={target[1]} r="62" fill="none" stroke={accent} strokeWidth="1" strokeDasharray="3 6" opacity="0.5">
            <animateTransform
              attributeName="transform"
              type="rotate"
              from={`0 ${target[0]} ${target[1]}`}
              to={`360 ${target[0]} ${target[1]}`}
              dur="20s"
              repeatCount="indefinite"
            />
          </circle>
        </g>
        <g style={{ transformOrigin: `${target[0]}px ${target[1]}px` }}>
          <circle cx={target[0]} cy={target[1]} r="54" fill="none" stroke={accent3} strokeWidth="1" strokeDasharray="2 4" opacity="0.6">
            <animateTransform
              attributeName="transform"
              type="rotate"
              from={`360 ${target[0]} ${target[1]}`}
              to={`0 ${target[0]} ${target[1]}`}
              dur="14s"
              repeatCount="indefinite"
            />
          </circle>
        </g>

        {[0, 0.6, 1.2].map((delay, i) => (
          <circle key={`pulse-${i}`} cx={target[0]} cy={target[1]} r="44" fill="none" stroke={accent} strokeWidth="1.2" opacity="0">
            <animate attributeName="r" from="44" to="80" dur="2.4s" begin={`${delay}s`} repeatCount="indefinite" />
            <animate attributeName="opacity" from="0.55" to="0" dur="2.4s" begin={`${delay}s`} repeatCount="indefinite" />
          </circle>
        ))}

        {/* 코어 본체 — Qaima 로고 자리 */}
        <circle cx={target[0]} cy={target[1]} r="44" fill={surface} stroke={accent} strokeWidth="2" />
        <g transform={`translate(${target[0]} ${target[1]})`}>
          <circle r="28" fill="none" stroke={inkSoft} strokeWidth="1" strokeDasharray="2 3" opacity="0.45" />
          <text x="0" y="3" textAnchor="middle" fontSize="8" fill={inkSoft} fontFamily="JetBrains Mono, monospace" letterSpacing="0.1em" opacity="0.6">
            LOGO
          </text>
        </g>

        <text x={target[0]} y={target[1] - 56} textAnchor="middle" fontSize="9" fill={inkSoft} fontFamily="JetBrains Mono, monospace" letterSpacing="0.18em" fontWeight="600">
          QAIMA · AI ENGINE
        </text>

        {/* 우측 출력 */}
        <g transform={`translate(${target[0] + 60}, ${target[1] - 70})`}>
          {[0, 1, 2].map((i) => {
            const colors = [accent, accent2, accent3];
            return (
              <g key={i} transform={`translate(0, ${i * 50})`}>
                <line x1="-22" y1="14" x2="-2" y2="14" stroke={colors[i]} strokeWidth="1.5" opacity="0.5" />
                <circle cx="0" cy="14" r="3" fill={colors[i]} />
                <path
                  d={`M 6 ${20 - i * 2} Q 22 ${14 - i * 3} 38 ${10 - i * 2} T 70 ${4 - i * 2} T 100 ${-2 - i * 2}`}
                  fill="none"
                  stroke={colors[i]}
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  opacity={0.4 + (2 - i) * 0.3}
                />
                <circle cx="100" cy={-2 - i * 2} r="2.5" fill={colors[i]} />
              </g>
            );
          })}
          <text x="-22" y="-6" fontSize="9" fill={inkSoft} fontFamily="JetBrains Mono, monospace" letterSpacing="0.12em" fontWeight="600">
            → INSIGHT
          </text>
          <text x="106" y="14" fontSize="11" fontWeight="700" fill="rgb(var(--color-ink))" fontFamily="JetBrains Mono, monospace">+12.4%</text>
          <text x="106" y="64" fontSize="11" fontWeight="700" fill="rgb(var(--color-ink))" fontFamily="JetBrains Mono, monospace">A+</text>
          <text x="106" y="114" fontSize="11" fontWeight="700" fill="rgb(var(--color-ink))" fontFamily="JetBrains Mono, monospace">73</text>
          <text x="106" y="26" fontSize="8" fill={inkSoft}>예상 수익</text>
          <text x="106" y="76" fontSize="8" fill={inkSoft}>신호 강도</text>
          <text x="106" y="126" fontSize="8" fill={inkSoft}>위험도</text>
        </g>

        <g opacity="0.5">
          <circle cx="80" cy="450" r="1.5" fill={accent} />
          <circle cx="120" cy="460" r="1" fill={accent2} />
          <circle cx="100" cy="440" r="0.8" fill={accent3} />
          <circle cx="430" cy="50" r="1.2" fill={accent2} />
          <circle cx="450" cy="80" r="0.8" fill={accent} />
        </g>

        <text x="32" y="32" fontSize="9" fill={inkSoft} fontFamily="JetBrains Mono, monospace" letterSpacing="0.15em" fontWeight="600">
          6 STREAMS · LIVE
        </text>
        <circle cx="170" cy="29" r="3" fill={teal}>
          <animate attributeName="opacity" values="1;0.3;1" dur="1.4s" repeatCount="indefinite" />
        </circle>
      </svg>
    </div>
  );
}

// ───────────────────────────────────────────────────────────────
// Section primitives
// ───────────────────────────────────────────────────────────────
function SectionHead({ eyebrow, title, sub }: { eyebrow: string; title: string; sub?: string }) {
  return (
    <div className="max-w-[720px]">
      <div className="text-xs font-mono font-semibold tracking-[0.14em] text-accent-ink mb-3.5">{eyebrow}</div>
      <h2 className="text-4xl font-bold tracking-tighter leading-tight text-ink m-0">{title}</h2>
      {sub && <p className="text-base leading-relaxed text-ink-2 mt-4 max-w-[600px]">{sub}</p>}
    </div>
  );
}

// ───────────────────────────────────────────────────────────────
// Feature card glyphs
// ───────────────────────────────────────────────────────────────
function FeatureGlyph({ kind }: { kind: "candle" | "radar" | "pie" }) {
  const accent = "rgb(var(--color-accent))";
  const inkSoft = "rgb(var(--color-ink-4))";
  const rise = "rgb(var(--color-rise))";
  const fall = "rgb(var(--color-fall))";
  const surface = "rgb(var(--color-surface))";

  if (kind === "candle") {
    const candles = [
      { x: 30, t: 60, b: 90, up: false }, { x: 50, t: 50, b: 85, up: true },
      { x: 70, t: 40, b: 75, up: true }, { x: 90, t: 55, b: 100, up: false },
      { x: 110, t: 30, b: 70, up: true }, { x: 130, t: 35, b: 65, up: true },
      { x: 150, t: 45, b: 80, up: false }, { x: 170, t: 25, b: 60, up: true },
      { x: 190, t: 20, b: 50, up: true }, { x: 210, t: 30, b: 55, up: false },
    ];
    return (
      <svg width="100%" height="100%" viewBox="0 0 240 140">
        {[40, 70, 100].map((y) => (
          <line key={y} x1="20" x2="220" y1={y} y2={y} stroke={inkSoft} strokeDasharray="2 4" strokeWidth="0.5" opacity="0.4" />
        ))}
        {candles.map((c, i) => (
          <g key={i}>
            <line x1={c.x} x2={c.x} y1={c.t - 6} y2={c.b + 6} stroke={c.up ? rise : fall} strokeWidth="1" />
            <rect x={c.x - 4} y={c.t} width="8" height={c.b - c.t} fill={c.up ? rise : fall} />
          </g>
        ))}
        <path d="M 30 80 Q 100 60 150 50 T 220 30" fill="none" stroke={accent} strokeWidth="1.8" strokeDasharray="3 3" />
      </svg>
    );
  }
  if (kind === "radar") {
    return (
      <svg width="100%" height="100%" viewBox="0 0 240 140">
        {[20, 38, 56].map((r) => (
          <circle key={r} cx="120" cy="70" r={r} fill="none" stroke={inkSoft} strokeDasharray="2 3" strokeWidth="0.5" opacity="0.5" />
        ))}
        {[[70, 50], [60, 80], [180, 45], [195, 90], [110, 30], [165, 105], [85, 110]].map(([x, y], i) => (
          <line key={i} x1="120" y1="70" x2={x} y2={y} stroke={accent} strokeWidth="0.8" opacity="0.5" />
        ))}
        {[
          { x: 70, y: 50, r: 4 }, { x: 60, y: 80, r: 3 }, { x: 180, y: 45, r: 5 },
          { x: 195, y: 90, r: 4 }, { x: 110, y: 30, r: 3 }, { x: 165, y: 105, r: 4 },
          { x: 85, y: 110, r: 3 },
        ].map((n, i) => (<circle key={i} cx={n.x} cy={n.y} r={n.r} fill={accent} opacity="0.7" />))}
        <circle cx="120" cy="70" r="9" fill={accent} />
        <circle cx="120" cy="70" r="4" fill={surface} />
        <text x="120" y="22" textAnchor="middle" fontSize="9" fill={inkSoft} fontFamily="JetBrains Mono, monospace">NEWS · RATES · INDUSTRY</text>
      </svg>
    );
  }
  // pie
  const slices = [
    { from: 0, to: 0.35, color: accent },
    { from: 0.35, to: 0.55, color: rise },
    { from: 0.55, to: 0.75, color: fall },
    { from: 0.75, to: 1, color: inkSoft },
  ];
  const arc = (from: number, to: number, r: number) => {
    const a1 = (from * 2 - 0.5) * Math.PI;
    const a2 = (to * 2 - 0.5) * Math.PI;
    const x1 = 80 + r * Math.cos(a1);
    const y1 = 70 + r * Math.sin(a1);
    const x2 = 80 + r * Math.cos(a2);
    const y2 = 70 + r * Math.sin(a2);
    const large = to - from > 0.5 ? 1 : 0;
    return `M 80 70 L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`;
  };
  return (
    <svg width="100%" height="100%" viewBox="0 0 240 140">
      {slices.map((s, i) => (<path key={i} d={arc(s.from, s.to, 42)} fill={s.color} opacity="0.85" />))}
      <circle cx="80" cy="70" r="22" fill={surface} />
      <text x="80" y="68" textAnchor="middle" fontSize="11" fontWeight="700" fill="rgb(var(--color-ink))" fontFamily="JetBrains Mono, monospace">62%</text>
      <text x="80" y="80" textAnchor="middle" fontSize="7" fill={inkSoft} fontFamily="JetBrains Mono, monospace">국내</text>
      <g transform="translate(160, 90)">
        <path d="M -32 0 A 32 32 0 0 1 32 0" fill="none" stroke="rgb(var(--color-bg-sunk))" strokeWidth="8" strokeLinecap="round" />
        <path d="M -32 0 A 32 32 0 0 1 16 -27.7" fill="none" stroke={rise} strokeWidth="8" strokeLinecap="round" />
        <text x="0" y="-4" textAnchor="middle" fontSize="14" fontWeight="700" fill="rgb(var(--color-ink))" fontFamily="JetBrains Mono, monospace">73</text>
        <text x="0" y="14" textAnchor="middle" fontSize="8" fill={inkSoft}>위험도</text>
      </g>
    </svg>
  );
}

// ───────────────────────────────────────────────────────────────
// Workflow icon
// ───────────────────────────────────────────────────────────────
function WorkflowIcon({ kind }: { kind: "search" | "ai" | "doc" }) {
  if (kind === "search") return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" />
    </svg>
  );
  if (kind === "ai") return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2L9 9l-7 1 5 5-1 7 6-3 6 3-1-7 5-5-7-1z" />
    </svg>
  );
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6M9 13h6M9 17h4" />
    </svg>
  );
}

// ───────────────────────────────────────────────────────────────
// MainPage
// ───────────────────────────────────────────────────────────────
export default function MainPage() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const loggedIn = isLoggedIn();

  const handleLogin = () => {
    // 로그인 후 메인으로 복귀(미지정 시 LoginPage 기본값 /feature/1).
    sessionStorage.setItem("qaima_redirect", "/main");
    navigate("/login");
  };

  // 사이드바 로그아웃과 동일한 정리 절차. 끝나면 __reload nonce 로 메인을
  // 강제 리마운트해 비로그인 상태가 화면에 반영되게 한다.
  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // 서버 로그아웃 실패해도 클라이언트 상태는 정리한다.
    } finally {
      clearAccessToken();
      clearUser();
      clearTokenBalance();
      navigate("/main", { replace: true, state: { __reload: Date.now() } });
    }
  };

  // StockInputBox가 종목코드(또는 입력값)를 넘겨주면 심층분석으로 이동
  const goAnalyze = (value: string) => {
    const q = value.trim();
    navigate(q ? `/feature/1?q=${encodeURIComponent(q)}` : "/feature/1");
  };

  const features = [
    {
      tag: "FEATURE 01", title: "심층분석", en: "Deep Equity Analysis",
      desc: "단일 종목의 가격 흐름과 재무지표를 AI가 한 번에 정리합니다. 매수/매도 시점, 밸류에이션 평가, 주요 리스크까지.",
      visual: "candle" as const,
      bullets: ["실시간 캔들차트", "재무지표 자동 해석", "LLM 보고서 생성"],
      to: "/feature/1",
    },
    {
      tag: "FEATURE 02", title: "외부요인", en: "Macro & Context",
      desc: "산업 지수, 금리, 뉴스, 공매도, 유사 종목 — 한 종목을 둘러싼 외부 환경을 모두 모아 분석합니다.",
      visual: "radar" as const,
      bullets: ["산업 유사 종목군", "기준금리 시계열", "실시간 뉴스 큐레이션"],
      to: "/feature/2",
    },
    {
      tag: "FEATURE 03", title: "포트폴리오", en: "Portfolio Health",
      desc: "보유 자산을 입력하면 위험·분산·효율을 진단합니다. 투자 성향에 맞춘 리밸런싱까지.",
      visual: "pie" as const,
      bullets: ["위험 수준 진단", "분산 구조 분석", "성향 기반 추천"],
      to: "/feature/3",
    },
  ];

  const steps = [
    { n: "01", title: "검색", desc: "종목명·코드·티커 무엇이든. 한국·미국 시장을 모두 지원합니다.", icon: "search" as const },
    { n: "02", title: "AI 분석", desc: "Gemini · GPT 기반 LLM이 가격·재무·뉴스·산업·금리 데이터를 동시에 읽습니다.", icon: "ai" as const },
    { n: "03", title: "인사이트", desc: "단순 수치가 아닌 \"왜 그런가\"의 해석. PDF로 저장해 가져갈 수 있습니다.", icon: "doc" as const },
  ];

  const sources = [
    { label: "실시간 시세", source: "한국투자증권 KIS API", note: "코스피 · 코스닥 · NYSE · NASDAQ" },
    { label: "재무 데이터", source: "DART · SEC EDGAR", note: "분기/연간 재무제표, 공시 원본" },
    { label: "거시 지표", source: "한국은행 · FRED", note: "기준금리, 환율, 채권 수익률" },
    { label: "뉴스 & 공시", source: "주요 미디어 · 거래소 공시", note: "공매도 · 외국인 매매 동향 포함" },
  ];

  return (
    <div className="min-h-screen bg-bg text-ink ml-[84px]">
      {/* HERO */}
      <section className="relative pt-16 sm:pt-20 pb-14 overflow-hidden">
        {/* 우상단 — 로그인/로그아웃 + 다크모드 토글. 사이드바에만 있으면
            처음 온 사용자가 찾기 어려워 메인 상단에도 노출한다. */}
        <div className="absolute top-6 right-8 sm:right-12 lg:right-16 z-10 flex items-center gap-2.5">
          <button
            onClick={loggedIn ? handleLogout : handleLogin}
            className="h-9 px-3.5 inline-flex items-center rounded-xl bg-surface
                       border border-line text-ink-2 text-sm font-semibold
                       shadow-card hover:bg-bg-sunk transition-colors"
          >
            {loggedIn ? "로그아웃" : "로그인"}
          </button>
          <div className="relative group">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? "라이트모드로 변경" : "다크모드로 변경"}
              className="w-9 h-9 grid place-items-center rounded-xl bg-surface
                         border border-line text-ink-2 shadow-card
                         hover:bg-bg-sunk transition-colors"
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <span
              className="pointer-events-none absolute top-full right-0 mt-2
                         whitespace-nowrap rounded-lg bg-ink text-bg text-xs font-medium
                         px-2.5 py-1.5 shadow-pop opacity-0 group-hover:opacity-100
                         transition-opacity"
            >
              {theme === "dark" ? "라이트모드로 변경" : "다크모드로 변경"}
            </span>
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-[1.15fr_1fr] gap-12 items-start max-w-[1400px] mx-auto px-8 sm:px-12 lg:px-16">
          {/* 좌측 */}
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-accent-soft text-accent-ink text-xs font-mono font-semibold tracking-wider mb-6">
              <span className="w-1.5 h-1.5 rounded-full bg-accent" />
              POWERED BY AI
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-[56px] leading-[1.1] tracking-tighter font-bold text-ink m-0">
              시장을 읽는 일,<br />
              <span className="text-accent">AI에게 맡기세요.</span>
            </h1>

            <p className="text-base sm:text-[17px] leading-relaxed text-ink-2 mt-5 mb-9 max-w-[480px]">
              가격, 재무, 산업, 뉴스, 금리까지 — 흩어진 데이터를 하나의 분석 보고서로 정리합니다. 종목 하나만 입력하면 됩니다.
            </p>

            {/* 검색박스 — 심층분석과 동일한 자동완성 검색 컴포넌트 */}
            <div className="max-w-[520px]">
              <StockInputBox placeholder="삼성전자, 005930, NVDA…" onSearch={goAnalyze} showInterest={false} enableRecent={false} />
            </div>

            {/* 인기 종목 */}
            <div className="flex flex-wrap items-center gap-2 mt-4 text-xs text-ink-3">
              <span className="font-mono tracking-wider">지금 많이 검색돼요</span>
              {["삼성전자", "SK하이닉스", "NVDA", "현대차"].map((s) => (
                <button
                  key={s}
                  onClick={() => navigate(`/feature/1?q=${encodeURIComponent(s)}`)}
                  className="px-2.5 py-1 rounded-full bg-bg-sunk text-ink-2 text-xs font-medium hover:bg-line transition-colors"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>

          {/* 우측 — AI 비주얼 */}
          <div className="hidden lg:flex items-start justify-end overflow-hidden">
            <HeroVisual />
          </div>
        </div>
      </section>

      {/* FEATURES */}
      <Reveal>
      <section className="py-20 bg-bg-alt">
        <div className="max-w-[1400px] mx-auto px-8 sm:px-12 lg:px-16">
          <SectionHead
            eyebrow="WHAT QAIMA DOES"
            title="3가지 시선으로, 한 종목을 본다"
            sub="가격만 본다고 시장이 보이지 않습니다. Qaima는 종목·환경·포트폴리오 세 축에서 동시에 분석합니다."
          />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mt-12">
            {features.map((f, i) => (
              <button
                key={i}
                onClick={() => navigate(f.to)}
                className="text-left bg-surface rounded-[20px] p-7 border border-line flex flex-col gap-5 min-h-[460px] hover:shadow-card transition-shadow cursor-pointer"
              >
                <div className="text-[11px] font-mono tracking-widest text-ink-3 font-semibold">{f.tag}</div>
                <div className="h-[140px] bg-bg-sunk rounded-2xl grid place-items-center overflow-hidden">
                  <FeatureGlyph kind={f.visual} />
                </div>
                <div>
                  <div className="flex items-baseline gap-2.5 mb-2">
                    <h3 className="text-[22px] m-0 text-ink font-bold tracking-tight">{f.title}</h3>
                    <span className="text-[11px] text-ink-4 font-mono tracking-wider">{f.en}</span>
                  </div>
                  <p className="text-sm leading-relaxed text-ink-2 m-0">{f.desc}</p>
                </div>
                <div className="flex flex-col gap-1.5 mt-auto pt-4 border-t border-dashed border-line">
                  {f.bullets.map((b, k) => (
                    <div key={k} className="flex items-center gap-2 text-[13px] text-ink-2">
                      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <circle cx="7" cy="7" r="6" stroke="rgb(var(--color-accent))" strokeWidth="1.2" />
                        <path d="M4.5 7 L6 8.5 L9.5 5" stroke="rgb(var(--color-accent))" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" fill="none" />
                      </svg>
                      {b}
                    </div>
                  ))}
                </div>
              </button>
            ))}
          </div>
        </div>
      </section>
      </Reveal>

      {/* WORKFLOW */}
      <Reveal>
      <section className="py-20">
        <div className="px-8 sm:px-12 lg:px-16 max-w-[1400px] mx-auto">
          <SectionHead
            eyebrow="HOW IT WORKS"
            title="검색 한 번, 분석 30초"
            sub="복잡한 설정 없이 — 종목명만 입력하면 흩어진 데이터를 모아 AI가 보고서를 작성합니다."
          />
          <div className="relative grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
            <div
              className="hidden md:block absolute top-14 left-[16.66%] right-[16.66%] h-0.5 z-0"
              style={{
                backgroundImage: `linear-gradient(to right, rgb(var(--color-line)) 50%, transparent 50%)`,
                backgroundSize: "12px 2px",
              }}
            />
            {steps.map((s, i) => (
              <div key={i} className="relative z-[1] bg-surface rounded-[18px] px-6 py-7 border border-line flex flex-col gap-3.5">
                <div className="flex items-center justify-between">
                  <div className="w-12 h-12 rounded-2xl bg-accent-soft text-accent-ink grid place-items-center">
                    <WorkflowIcon kind={s.icon} />
                  </div>
                  <div className="font-mono text-xs text-ink-4 font-semibold">STEP {s.n}</div>
                </div>
                <div>
                  <h4 className="text-xl m-0 mb-2 text-ink font-bold tracking-tight">{s.title}</h4>
                  <p className="text-sm leading-relaxed text-ink-2 m-0">{s.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
      </Reveal>

      {/* DATA SOURCES */}
      <Reveal>
      <section className="py-20 bg-bg-alt">
        <div className="max-w-[1400px] mx-auto px-8 sm:px-12 lg:px-16">
          <SectionHead
            eyebrow="DATA YOU CAN TRUST"
            title="공식 출처에서, 실시간으로"
            sub="블로그 요약이 아닙니다. 거래소·중앙은행·공시 시스템에서 직접 가져온 1차 데이터를 분석합니다."
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-12">
            {sources.map((s, i) => (
              <div key={i} className="bg-surface rounded-[14px] px-5 py-[22px] border border-line flex flex-col gap-2.5">
                <div className="text-[11px] font-mono tracking-wider text-accent-ink font-semibold">{s.label.toUpperCase()}</div>
                <div className="text-base font-semibold text-ink tracking-tight">{s.source}</div>
                <div className="text-xs text-ink-3 leading-relaxed">{s.note}</div>
              </div>
            ))}
          </div>
        </div>
      </section>
      </Reveal>

      {/* FINAL CTA */}
      <Reveal>
      <section className="py-20 pb-24">
        <div className="max-w-[1400px] mx-auto px-8 sm:px-12 lg:px-16">
          <div
            className="rounded-3xl px-10 sm:px-14 py-14 sm:py-16 relative overflow-hidden"
            style={{
              background: "linear-gradient(135deg, rgb(var(--color-accent-ink)) 0%, rgb(var(--color-accent)) 100%)",
            }}
          >
            <svg width="280" height="280" viewBox="0 0 280 280" className="absolute -right-10 -bottom-14 opacity-[0.18]">
              {Array.from({ length: 10 }).map((_, r) =>
                Array.from({ length: 10 }).map((_, c) => (
                  <circle key={`${r}-${c}`} cx={c * 28 + 14} cy={r * 28 + 14} r="1.5" fill="#ffffff" />
                ))
              )}
            </svg>

            <div className="relative z-[1] max-w-[720px]">
              <div className="text-xs font-mono tracking-widest text-white/70 font-semibold mb-4">
                START FREE · 신용카드 불필요
              </div>
              <h2 className="text-3xl sm:text-4xl lg:text-[44px] m-0 text-white font-bold tracking-tighter leading-tight">
                <span className="block">지금 분석을 시작하세요.</span>
                <span className="block mt-3">가입하면 토큰 5개를 드립니다.</span>
              </h2>
              <p className="text-base text-white/80 mt-5 mb-9 max-w-[540px] leading-relaxed">
                첫 분석은 무료입니다. 토큰을 다 쓰면 그때 결제하세요. 강제 유료화·자동 결제는 없습니다.
              </p>
              <div className="flex flex-wrap gap-3">
                <button
                  onClick={() => navigate("/signup")}
                  className="bg-white text-accent-ink border-none rounded-xl px-7 py-3.5 text-[15px] font-semibold cursor-pointer flex items-center gap-2 hover:opacity-90 transition-opacity"
                >
                  무료로 시작하기
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M5 12h14M13 5l7 7-7 7" />
                  </svg>
                </button>
                {!isLoggedIn() && (
                  <button
                    onClick={() => {
                      // 메인에서 로그인하면 로그인 후 메인으로 돌아오게 한다
                      // (지정 안 하면 LoginPage 기본값이 /feature/1).
                      sessionStorage.setItem("qaima_redirect", "/main");
                      navigate("/login");
                    }}
                    className="bg-transparent text-white border border-white/30 rounded-xl px-6 py-3.5 text-[15px] font-medium cursor-pointer hover:bg-white/10 transition-colors"
                  >
                    로그인
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </section>
      </Reveal>
    </div>
  );
}
