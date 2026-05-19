// src/layout/Sidebar.tsx
import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { BarChart2, Globe, Briefcase, BookOpen, User } from "lucide-react";
import { isLoggedIn } from "../utils/auth";
import { logout } from "../api/auth";
import { clearAccessToken } from "../api/tokenStore";
import { clearUser, getUser } from "../api/userStore";
import { clearTokenBalance } from "../api/billingStore";
import { useBilling } from "../contexts/BillingContext";

import introIcon from "../assets/qaima-final.png";

const navItems = [
  { path: "/feature/1", icon: BarChart2,  label: "심층분석" },
  { path: "/feature/2", icon: Globe,      label: "외부요인" },
  { path: "/feature/3", icon: Briefcase,  label: "포트폴리오" },
  { path: "/feature/4", icon: BookOpen,   label: "사전" },
];

const ACCOUNT_ONLY_PREFIXES = ["/setting", "/billing"];

const isAccountOnlyPath = (pathname: string): boolean =>
  ACCOUNT_ONLY_PREFIXES.some((prefix) => pathname.startsWith(prefix));

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { openBilling } = useBilling();
  const [menuOpen, setMenuOpen] = useState(false);
  const [anchorTop, setAnchorTop] = useState<number | null>(null);
  const [loggedIn, setLoggedIn] = useState<boolean>(() => isLoggedIn());
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const user = loggedIn ? getUser() : null;

  useEffect(() => {
    setLoggedIn(isLoggedIn());
  }, [location.pathname]);

  const openMenu = () => {
    if (!buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    setAnchorTop(rect.bottom);
    setMenuOpen(true);
  };

  const closeMenu = () => setMenuOpen(false);

  const handleAccountClick = () => {
    if (!loggedIn) {
      // 로그인 후 원래 보던 페이지로 복귀(미지정 시 기본값 /feature/1).
      sessionStorage.setItem(
        "qaima_redirect",
        location.pathname + location.search,
      );
      navigate("/login");
      return;
    }
    if (menuOpen) closeMenu();
    else openMenu();
  };

  // 이미 그 페이지에 있을 때 사이드바의 같은 항목을 누르면 React Router 가
  // no-op 이라 화면이 그대로다. 같은 경로면 state 에 nonce 를 실어 강제
  // 재네비게이트 → PageTransition 키가 바뀌어 해당 페이지가 새로 마운트된다.
  const handleNavClick = (
    e: React.MouseEvent<HTMLAnchorElement>,
    path: string,
  ) => {
    if (location.pathname === path) {
      e.preventDefault();
      navigate(path, { replace: true, state: { __reload: Date.now() } });
    }
  };

  const handleGoSetting = () => { closeMenu(); navigate("/setting"); };
  const handleGoBilling = () => { closeMenu(); openBilling(); };

  const handleLogout = async () => {
    closeMenu();
    try {
      await logout();
    } catch {
      // 서버 로그아웃 실패해도 클라이언트 상태는 정리한다.
    } finally {
      clearAccessToken();
      clearUser();
      clearTokenBalance();
      setLoggedIn(false);
      // 계정 전용 페이지면 메인으로, 그 외엔 현재 페이지를 그대로 두되
      // 둘 다 __reload nonce 로 강제 리마운트 → 비로그인 상태가 화면에 반영됨
      // (예: 메인의 "로그인" 버튼 다시 노출, 토큰 잔액 배지 갱신 등).
      const target = isAccountOnlyPath(location.pathname)
        ? "/main"
        : location.pathname;
      navigate(target, { replace: true, state: { __reload: Date.now() } });
    }
  };

  useEffect(() => {
    if (!menuOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (menuRef.current?.contains(target)) return;
      if (buttonRef.current?.contains(target)) return;
      closeMenu();
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeMenu();
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKey);
    };
  }, [menuOpen]);

  return (
    <div className="w-[84px] h-screen border-r border-line bg-surface flex flex-col items-center py-6 gap-1.5 fixed left-0 top-0 z-50">
      {/* 상단 로고 — introIcon, /main 이동 */}
      <NavLink
        to="/main"
        onClick={(e) => handleNavClick(e, "/main")}
        className="w-9 h-9 rounded-xl overflow-hidden flex items-center justify-center mb-2 flex-shrink-0"
        aria-label="홈"
      >
        <img src={introIcon} alt="홈" className="w-full h-full object-contain" />
      </NavLink>

      {/* 네비게이션 항목 */}
      {navItems.map(({ path, icon: Icon, label }) => (
        <NavLink
          key={path}
          to={path}
          onClick={(e) => handleNavClick(e, path)}
          className={({ isActive }) =>
            `flex flex-col items-center gap-1 py-2.5 px-1 w-16 rounded-xl transition-colors ${
              isActive
                ? "bg-accent-soft text-accent"
                : "text-ink-3 hover:bg-bg-sunk"
            }`
          }
        >
          <Icon size={20} strokeWidth={1.8} />
          <span className="text-[10px] font-medium whitespace-nowrap leading-tight">
            {label}
          </span>
        </NavLink>
      ))}

      {/* 하단 계정 버튼 */}
      <div className="mt-auto">
        <button
          ref={buttonRef}
          onClick={handleAccountClick}
          className="flex flex-col items-center gap-1 py-2.5 px-1 w-16 rounded-xl hover:bg-bg-sunk transition-colors text-ink-3"
        >
          <User size={20} strokeWidth={1.8} />
          <span className="text-[10px] font-medium whitespace-nowrap leading-tight">
            {loggedIn ? "내정보" : "로그인"}
          </span>
        </button>
      </div>

      {/* 계정 팝업 메뉴 */}
      {menuOpen && loggedIn && anchorTop !== null && (
        <div
          ref={menuRef}
          role="menu"
          style={{ bottom: `calc(100vh - ${anchorTop}px)` }}
          className="fixed left-[92px] z-[60] w-[240px] bg-surface border border-line rounded-xl shadow-pop py-2 flex flex-col"
        >
          <div className="px-4 py-2.5 border-b border-line">
            <p className="text-[13px] font-semibold text-ink truncate">
              {user?.email ?? "알 수 없음"}
            </p>
            {user?.name && (
              <p className="text-[11px] text-ink-3 truncate">{user.name}</p>
            )}
          </div>

          <div className="py-1">
            <button
              onClick={handleGoSetting}
              className="w-full text-left px-4 py-2 text-[13px] text-ink hover:bg-bg-sunk"
              role="menuitem"
            >
              내정보
            </button>
            <button
              onClick={handleGoBilling}
              className="w-full text-left px-4 py-2 text-[13px] text-ink hover:bg-bg-sunk"
              role="menuitem"
            >
              요금제 / 토큰 결제
            </button>
          </div>

          <div className="border-t border-line py-1">
            <button
              onClick={handleLogout}
              className="w-full text-left px-4 py-2 text-[13px] text-danger hover:bg-bg-sunk"
              role="menuitem"
            >
              로그아웃
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
