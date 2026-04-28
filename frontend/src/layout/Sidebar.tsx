// src/components/Sidebar.tsx
import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { isLoggedIn } from "../utils/auth";
import { logout } from "../api/auth";
import { clearAccessToken } from "../api/tokenStore";
import { clearUser, getUser } from "../api/userStore";
import { useBilling } from "../contexts/BillingContext";

import introIcon from "../assets/intro.png";
import analysisIcon from "../assets/analysis.png";
import externalIcon from "../assets/external.png";
import portfolioIcon from "../assets/portfolio.png";
import dictionaryIcon from "../assets/dictionary.png";
import mypageIcon from "../assets/mypage.png";

const navItems = [
  { path: "/main", icon: introIcon, label: "소개" },
  { path: "/feature/1", icon: analysisIcon, label: "심층분석" },
  { path: "/feature/2", icon: externalIcon, label: "외부요인" },
  { path: "/feature/3", icon: portfolioIcon, label: "포트폴리오" },
  { path: "/feature/4", icon: dictionaryIcon, label: "사전" },
];

// 로그아웃 시 현재 페이지에 머물 수 없는(=계정 전용) 경로.
// 이 경로에서 로그아웃하면 소개 페이지로 이동한다.
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

  // 라우트가 바뀔 때마다 로그인 상태를 재확인한다.
  // (로그인 페이지에서 돌아왔을 때 등)
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
      navigate("/login");
      return;
    }
    if (menuOpen) {
      closeMenu();
    } else {
      openMenu();
    }
  };

  const handleGoSetting = () => {
    closeMenu();
    navigate("/setting");
  };

  const handleGoBilling = () => {
    closeMenu();
    openBilling();
  };

  const handleLogout = async () => {
    closeMenu();
    try {
      await logout();
    } catch {
      // 서버 로그아웃 실패해도 클라이언트 상태는 정리한다.
    } finally {
      clearAccessToken();
      clearUser();
      setLoggedIn(false);
      if (isAccountOnlyPath(location.pathname)) {
        navigate("/main");
      }
      // 그 외에는 현재 페이지에 그대로 머무른다.
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
    <div className="w-[60px] h-screen border-r-2 border-[#C6C6C6] bg-[#FDFDFD] flex flex-col items-center justify-start gap-[20px] py-6 fixed left-0 top-0 z-50">
      {navItems.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className="flex flex-col items-center gap-[4px] p-[4px] w-[56px] rounded-[12px] hover:bg-[#D7D7D7]"
        >
          <div className="w-[28px] h-[28px] flex items-center justify-center">
            <img
              src={item.icon}
              alt={item.label}
              className="w-full h-full object-contain"
            />
          </div>
          <div className="text-black text-center text-[11px] whitespace-nowrap font-normal">
            {item.label}
          </div>
        </NavLink>
      ))}

      {/* 내정보(로그아웃) 버튼 */}
      <button
        ref={buttonRef}
        onClick={handleAccountClick}
        className="mt-auto flex flex-col items-center gap-[4px] p-[4px] w-[56px] rounded-[12px] hover:bg-[#D7D7D7]"
      >
        <div className="w-[28px] h-[28px] flex items-center justify-center">
          <img
            src={mypageIcon}
            alt="내정보"
            className="w-full h-full object-contain"
          />
        </div>
        <div className="text-black text-center text-[11px] whitespace-nowrap font-normal">
          {loggedIn ? "내정보" : "로그인"}
        </div>
      </button>

      {menuOpen && loggedIn && anchorTop !== null && (
        <div
          ref={menuRef}
          role="menu"
          style={{ bottom: `calc(100vh - ${anchorTop}px)` }}
          className="fixed left-[68px] z-[60] w-[240px] bg-white border border-stone-300 rounded-[12px] shadow-lg py-2 flex flex-col"
        >
          <div className="px-4 py-2.5 border-b border-stone-200">
            <p className="text-[13px] font-semibold text-black truncate">
              {user?.email ?? "알 수 없음"}
            </p>
            {user?.name && (
              <p className="text-[11px] text-zinc-500 truncate">{user.name}</p>
            )}
          </div>

          <div className="py-1">
            <button
              onClick={handleGoSetting}
              className="w-full text-left px-4 py-2 text-[13px] text-zinc-800 hover:bg-zinc-100"
              role="menuitem"
            >
              내정보
            </button>
            <button
              onClick={handleGoBilling}
              className="w-full text-left px-4 py-2 text-[13px] text-zinc-800 hover:bg-zinc-100"
              role="menuitem"
            >
              요금제 / 토큰 결제
            </button>
          </div>

          <div className="border-t border-stone-200 py-1">
            <button
              onClick={handleLogout}
              className="w-full text-left px-4 py-2 text-[13px] text-red-600 hover:bg-zinc-100"
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
