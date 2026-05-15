import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { isLoggedIn } from "../utils/auth";

type Props = {
  children: ReactNode;
};

// 미인증 사용자가 보호된 라우트에 접근하면, 자식 컴포넌트가 아예 마운트되지 않도록
// 렌더 단계에서 즉시 /login 으로 이동시킨다. 401 응답 후 강제 reload 되는 것보다
// 깔끔하다.
export default function RequireAuth({ children }: Props) {
  const location = useLocation();

  if (!isLoggedIn()) {
    sessionStorage.setItem(
      "qaima_redirect",
      location.pathname + location.search,
    );
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
