// src/components/ScrollToTop.tsx
// 라우트(경로)가 바뀔 때마다 스크롤을 맨 위로 되돌린다.
// SPA 라 문서가 새로고침되지 않아, 이전 페이지에서 내린 스크롤 위치가
// 다음 페이지에 그대로 남는 문제를 막는다. (렌더링 결과 없음)
import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
}
