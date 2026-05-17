import { createContext, useContext } from "react";

/**
 * PDF 캡처 중에는 true. 스크롤 등장(IntersectionObserver) 기반 컴포넌트가
 * 이 값을 읽어, 스크롤 여부와 무관하게 즉시 최종 상태로 렌더하도록 한다.
 *
 * 사용: const forceReveal = usePdfExportReveal();
 *       const shown = animated || forceReveal;
 */
export const PdfExportContext = createContext(false);

export function usePdfExportReveal(): boolean {
  return useContext(PdfExportContext);
}
