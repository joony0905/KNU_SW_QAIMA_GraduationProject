// 분석 결과 보기 배너 등에서 현재 투자레벨을 표시하는 작은 칩.
// 보고서 용어 난이도가 이 등급에 맞춰 생성된다는 점을 사용자에게 안내한다.
import { TrendingUp } from "lucide-react";
import { Link } from "react-router-dom";
import { useDictionary } from "./DictContext";

type Props = {
  className?: string;
};

export default function InvestLevelBadge({ className }: Props) {
  const { investLevel } = useDictionary();
  return (
    <Link
      to="/invest-level-survey"
      title="투자레벨 설문 다시하기"
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface/80 border border-line text-[11px] sm:text-xs text-ink-2 hover:bg-surface transition-colors ${className ?? ""}`}
    >
      <TrendingUp size={12} className="text-accent" />
      <span>
        투자레벨{" "}
        <span className="font-semibold text-ink">{investLevel}</span> 기준으로 분석
      </span>
    </Link>
  );
}
