// 분석 결과 보기 배너 등에서 현재 투자레벨을 표시하는 작은 칩.
// 보고서 용어 난이도가 이 등급에 맞춰 생성된다는 점을 사용자에게 안내한다.
import { TrendingUp } from "lucide-react";
import { useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { useDictionary } from "./DictContext";

type Props = {
  className?: string;
};

export default function InvestLevelBadge({ className }: Props) {
  const { investLevel } = useDictionary();
  const navigate = useNavigate();
  const [confirmOpen, setConfirmOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setConfirmOpen(true)}
        title="투자레벨 설문 다시하기"
        className={`inline-flex items-center gap-2.5 rounded-lg border border-accent/30 bg-accent-soft/70 px-3.5 py-2 text-left text-xs text-ink-2 transition-colors hover:bg-accent-soft sm:text-sm ${className ?? ""}`}
      >
        <span className="grid h-7 w-7 flex-shrink-0 place-items-center rounded-md bg-surface text-accent shadow-card">
          <TrendingUp size={16} aria-hidden="true" />
        </span>
        <span className="flex flex-col leading-tight">
          <span className="text-[11px] font-semibold text-accent sm:text-xs">투자레벨 기준 분석</span>
          <span className="mt-0.5 text-sm font-bold text-ink sm:text-base">{investLevel}</span>
        </span>
      </button>

      {confirmOpen && createPortal(
        <div className="qaima-modal-backdrop-in fixed inset-0 z-[100] flex items-center justify-center bg-black/45 px-4">
          <div className="qaima-modal-pop-in w-full max-w-sm rounded-2xl border border-line bg-surface p-5 shadow-pop">
            <h3 className="text-base font-bold text-ink">투자 레벨 설문을 할까요?</h3>
            <p className="mt-2 text-sm leading-relaxed text-ink-3">
              투자 레벨에 맞춰 분석 보고서의 설명 방식이 조정돼요.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => navigate("/invest-level-survey")}
                className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
              >
                예
              </button>
              <button
                type="button"
                onClick={() => setConfirmOpen(false)}
                className="rounded-lg border border-line bg-surface px-4 py-2 text-sm font-semibold text-ink-3 transition-colors hover:bg-bg-sunk"
              >
                아니오
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
