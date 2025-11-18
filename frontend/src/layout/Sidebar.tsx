import { NavLink, useNavigate } from "react-router-dom";
import {
  Search,
  Globe,
  FileBarChart,
  BookA,
  CircleUserRound,
} from "lucide-react";

const navItems = [
  { path: "/ping", icon: null, label: "소개" },
  { path: "/feature/1", icon: Search, label: "심층분석" },
  { path: "/feature/2", icon: Globe, label: "외부요인" },
  { path: "/feature/3", icon: FileBarChart, label: "포트폴리오" },
  { path: "/feature/4", icon: BookA, label: "사전" },
];

export default function Sidebar() {
  const navigate = useNavigate();

  return (
    <div className="w-[90px] h-screen border-r-2 border-[#C6C6C6] bg-[#FDFDFD] flex flex-col items-center justify-center gap-[30px] py-8 fixed left-0 top-0 z-50">
      {navItems.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className="flex flex-col items-center gap-[5px] p-[5px] w-[84px] rounded-[15px] hover:bg-[#D7D7D7]"
        >
          <div className="w-[50px] h-[50px] bg-gray-100 rounded-full flex items-center justify-center">
            {item.icon ? (
              <item.icon className="w-[30px] h-[30px] stroke-[2]" />
            ) : (
              <span className="text-xs font-bold">LOGO</span>
            )}
          </div>
          <div className="text-black text-center text-[14px] font-normal">{item.label}</div>
        </NavLink>
      ))}
      {/* 내정보(로그아웃)은 버튼 처리 */}
      <button
        onClick={() => {
          localStorage.removeItem("qaima_token");
          navigate("/login");
        }}
        className="flex flex-col items-center gap-[5px] p-[5px] w-[84px] rounded-[15px] hover:bg-[#D7D7D7]"
      >
        <div className="w-[50px] h-[50px] bg-gray-100 rounded-full flex items-center justify-center">
          <CircleUserRound className="w-[30px] h-[30px] stroke-[2]" />
        </div>
        <div className="text-black text-center text-[16px] font-normal">내정보</div>
      </button>
    </div>
  );
}