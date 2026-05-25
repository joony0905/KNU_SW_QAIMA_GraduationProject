// src/layout/Sidebar.tsx
import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { BarChart2, Globe, Briefcase, BookOpen, User, Menu } from "lucide-react";
import { useTranslation } from "react-i18next";
import { isLoggedIn } from "../utils/auth";
import { logout } from "../api/auth";
import { clearAccessToken } from "../api/tokenStore";
import { clearUser, getUser } from "../api/userStore";
import { clearTokenBalance } from "../api/billingStore";
import { useBilling } from "../contexts/BillingContext";

import introIcon from "../assets/qaima-final.png";

const navItems = [
  { path: "/feature/1", icon: BarChart2,  labelKey: "nav.deepAnalysis" },
  { path: "/feature/2", icon: Globe,      labelKey: "nav.externalFactors" },
  { path: "/feature/3", icon: Briefcase,  labelKey: "nav.portfolio" },
  { path: "/feature/4", icon: BookOpen,   labelKey: "nav.dictionary" },
] as const;

const ACCOUNT_ONLY_PREFIXES = ["/setting", "/billing"];

const isAccountOnlyPath = (pathname: string): boolean =>
  ACCOUNT_ONLY_PREFIXES.some((prefix) => pathname.startsWith(prefix));

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation("sidebar");
  const { openBilling } = useBilling();
  const [menuOpen, setMenuOpen] = useState(false);
  const [anchorTop, setAnchorTop] = useState<number | null>(null);
  const [loggedIn, setLoggedIn] = useState<boolean>(() => isLoggedIn());
  // 모바일(md 미만)에서 사이드바 자체의 슬라이드 인/아웃을 제어.
  const [mobileOpen, setMobileOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const user = loggedIn ? getUser() : null;

  useEffect(() => {
    setLoggedIn(isLoggedIn());
  }, [location.pathname]);

  // 페이지 이동 시 모바일 사이드바·계정 팝업 자동 닫기.
  useEffect(() => {
    setMobileOpen(false);
    setMenuOpen(false);
  }, [location.pathname]);

  // 모바일 사이드바가 열린 동안 본문 스크롤 잠금.
  useEffect(() => {
    if (!mobileOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [mobileOpen]);

  // ESC 로 모바일 사이드바 닫기.
  useEffect(() => {
    if (!mobileOpen) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMobileOpen(false);
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [mobileOpen]);

  const openAccountMenu = () => {
    if (!buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    setAnchorTop(rect.bottom);
    setMenuOpen(true);
  };

  const closeAccountMenu = () => setMenuOpen(false);

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
    if (menuOpen) closeAccountMenu();
    else openAccountMenu();
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

  const handleGoSetting = () => { closeAccountMenu(); navigate("/setting"); };
  const handleGoBilling = () => { closeAccountMenu(); openBilling(); };

  const handleLogout = async () => {
    closeAccountMenu();
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
      closeAccountMenu();
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeAccountMenu();
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKey);
    };
  }, [menuOpen]);

  return (
    <>
      {/* 모바일 전용 상단 고정 바 — 햄버거 포함, 스크롤에 무관하게 항상 상단 고정 */}
      <div className="md:hidden fixed top-0 left-0 right-0 h-12 z-30 bg-surface border-b border-line flex items-center px-3">
        <button
          type="button"
          onClick={() => setMobileOpen(true)}
          aria-label={t("openMenu")}
          className="w-10 h-10 grid place-items-center rounded-xl text-ink-2 hover:bg-bg-sunk transition-colors"
        >
          <Menu size={20} />
        </button>
      </div>

      {/* 모바일 사이드바 백드롭 — 바깥 여백 클릭 시 닫기 */}
      {mobileOpen && (
        <div
          className="md:hidden fixed inset-0 z-40 bg-ink/50"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* 사이드바 본체 — 데스크탑에선 항상 노출, 모바일에선 mobileOpen 토글로 슬라이드 */}
      <div
        className={`w-[84px] h-screen border-r border-line bg-surface
                    flex flex-col items-center py-6 gap-1.5 fixed left-0 top-0 z-50
                    transform transition-transform duration-200 ease-out
                    ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
                    md:translate-x-0`}
        role="navigation"
      >
        {/* 상단 로고 — introIcon, /main 이동 */}
        <NavLink
          to="/main"
          onClick={(e) => handleNavClick(e, "/main")}
          className="w-9 h-9 rounded-xl overflow-hidden flex items-center justify-center mb-2 flex-shrink-0"
          aria-label={t("home")}
        >
          <img src={introIcon} alt={t("home")} className="w-full h-full object-contain" />
        </NavLink>

        {/* 네비게이션 항목 */}
        {navItems.map(({ path, icon: Icon, labelKey }) => (
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
              {t(labelKey)}
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
              {loggedIn ? t("account.myInfo") : t("account.login")}
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
                {user?.email ?? t("account.unknown")}
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
                {t("account.myInfo")}
              </button>
              <button
                onClick={handleGoBilling}
                className="w-full text-left px-4 py-2 text-[13px] text-ink hover:bg-bg-sunk"
                role="menuitem"
              >
                {t("account.billing")}
              </button>
            </div>

            <div className="border-t border-line py-1">
              <button
                onClick={handleLogout}
                className="w-full text-left px-4 py-2 text-[13px] text-danger hover:bg-bg-sunk"
                role="menuitem"
              >
                {t("account.logout")}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
