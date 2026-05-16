// src/components/PageTransition.tsx
// 라우트가 바뀔 때마다 key가 갱신되어 페이드/슬라이드 애니메이션이 재생된다.
// 페이지가 "전환된다"는 시각적 신호를 주기 위한 래퍼.
import type { ReactNode } from "react";
import { useLocation } from "react-router-dom";

type Props = {
  children: ReactNode;
};

export default function PageTransition({ children }: Props) {
  const location = useLocation();

  return (
    <div key={location.pathname} className="qaima-page-in">
      {children}
    </div>
  );
}
