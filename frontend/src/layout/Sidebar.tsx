// src/components/Sidebar.tsx
import { NavLink, useNavigate } from "react-router-dom";
import { isLoggedIn } from "../utils/auth";

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

export default function Sidebar() {
  const navigate = useNavigate();

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
        onClick={() => {
          if (isLoggedIn()) {
            navigate("/setting");
          } else {
            navigate("/login");
          }
        }}
        className="flex flex-col items-center gap-[4px] p-[4px] w-[56px] rounded-[12px] hover:bg-[#D7D7D7]"
      >
        <div className="w-[28px] h-[28px] flex items-center justify-center">
          <img
            src={mypageIcon}
            alt="내정보"
            className="w-full h-full object-contain"
          />
        </div>
        <div className="text-black text-center text-[11px] whitespace-nowrap font-normal">
          {isLoggedIn() ? "내정보" : "로그인"}
        </div>
      </button>
    </div>
  );
}
