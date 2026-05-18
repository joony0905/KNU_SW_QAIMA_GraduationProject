import { useEffect, useRef, useState, type ReactNode } from "react";

interface RevealProps {
  children: ReactNode;
  className?: string;
  /** 등장 지연 (ms) — 같은 화면 내 순차 등장용 */
  delay?: number;
}

/**
 * 자식을 감싸고, 뷰포트에 들어오는 순간 한 번만 등장 애니메이션을 트리거한다.
 * 스타일은 index.css의 .qaima-reveal / .is-visible 가 담당.
 */
export default function Reveal({ children, className = "", delay = 0 }: RevealProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      // threshold 0 + 하단 -15% 마진: 섹션 높이와 무관하게 뷰포트 하단부에 닿으면 트리거
      { threshold: 0, rootMargin: "0px 0px -15% 0px" },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      className={`qaima-reveal ${visible ? "is-visible" : ""} ${className}`.trim()}
      style={delay ? { transitionDelay: `${delay}ms` } : undefined}
    >
      {children}
    </div>
  );
}
