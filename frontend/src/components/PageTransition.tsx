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

  // 기본은 경로(pathname) 기준 키 — 쿼리만 바뀌는 이동은 리마운트하지 않는다.
  // 사이드바에서 "현재 페이지"를 다시 누르면 location.state.__reload 가 갱신되어
  // 키가 바뀌고, 해당 페이지만 새로 마운트된다(= 새로고침 효과).
  const reloadNonce =
    (location.state as { __reload?: number } | null)?.__reload ?? "";

  return (
    <div key={`${location.pathname}:${reloadNonce}`} className="qaima-page-in">
      {children}
    </div>
  );
}
