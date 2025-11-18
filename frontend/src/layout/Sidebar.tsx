import { NavLink, useNavigate } from "react-router-dom";

export default function Sidebar() {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem("qaima_token");
    navigate("/login");
  };

  return (
    <div className="overflow-hidden text-xs text-center bg-white max-w-24 text-neutral-800">
      <div className="flex flex-col items-start px-4 pt-28 pb-96 w-full bg-gray-50 border border-solid border-zinc-200">
        <NavLink
          to="/ping"
          className="flex flex-col justify-center items-start p-5 whitespace-nowrap bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>소개</div>
        </NavLink>
        <NavLink
          to="/feature/1"
          className="flex flex-col justify-center px-2.5 py-5 mt-6 bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>심층 분석</div>
        </NavLink>
        <NavLink
          to="/feature/2"
          className="flex flex-col justify-center px-2.5 py-5 mt-6 bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>외부 요인</div>
        </NavLink>
        <NavLink
          to="/feature/3"
          className="flex flex-col justify-center px-1.5 py-5 mt-5 whitespace-nowrap bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>포트폴리오</div>
        </NavLink>
        <NavLink
          to="/feature/4"
          className="flex flex-col justify-center px-6 py-5 mt-5 whitespace-nowrap bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>사전</div>
        </NavLink>
        <button
          onClick={handleLogout}
          className="flex flex-col justify-center px-4 py-5 mt-5 mb-0 whitespace-nowrap bg-white rounded-lg border border-solid border-zinc-200 w-full"
        >
          <div>내정보</div>
        </button>
      </div>
    </div>
  );
}
