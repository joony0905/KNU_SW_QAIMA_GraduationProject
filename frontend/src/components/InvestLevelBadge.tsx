// 분석 결과 보기 배너 등에서 현재 투자레벨을 표시하는 작은 칩.
// 보고서 용어 난이도가 이 등급에 맞춰 생성된다는 점을 사용자에게 안내한다.
import { TrendingUp } from "lucide-react";
import { useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useDictionary } from "./DictContext";
import { investLevelLabel } from "../utils/displayLabels";

type Props = {
  className?: string;
};

export default function InvestLevelBadge({ className }: Props) {
  const { investLevel, investLevelReady } = useDictionary();
  const { t, i18n } = useTranslation("common");
  const navigate = useNavigate();
  const [confirmOpen, setConfirmOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setConfirmOpen(true)}
        title={t("investLevelBadge.retakeTitle")}
        className={`inline-flex items-center gap-2.5 rounded-lg border border-accent/30 bg-accent-soft/70 px-3.5 py-2 text-left text-xs text-ink-2 transition-colors hover:bg-accent-soft sm:text-sm ${className ?? ""}`}
      >
        <span className="grid h-7 w-7 flex-shrink-0 place-items-center rounded-md bg-surface text-accent shadow-card">
          <TrendingUp size={16} aria-hidden="true" />
        </span>
        <span className="flex flex-col leading-tight">
          <span className="text-[11px] font-semibold text-accent sm:text-xs">{t("investLevelBadge.label")}</span>
          <span className="mt-0.5 text-sm font-bold text-ink sm:text-base">
            {investLevelReady ? investLevelLabel(investLevel, i18n.language) : t("investLevelBadge.loading")}
          </span>
        </span>
      </button>

      {confirmOpen && createPortal(
        <div className="qaima-modal-backdrop-in fixed inset-0 z-[100] flex items-center justify-center bg-black/45 px-4">
          <div className="qaima-modal-pop-in w-full max-w-sm rounded-2xl border border-line bg-surface p-5 shadow-pop">
            <h3 className="text-base font-bold text-ink">{t("investLevelBadge.modalTitle")}</h3>
            <p className="mt-2 text-sm leading-relaxed text-ink-3">
              {t("investLevelBadge.modalBody")}
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => navigate("/invest-level-survey")}
                className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
              >
                {t("button.confirm")}
              </button>
              <button
                type="button"
                onClick={() => setConfirmOpen(false)}
                className="rounded-lg border border-line bg-surface px-4 py-2 text-sm font-semibold text-ink-3 transition-colors hover:bg-bg-sunk"
              >
                {t("button.cancel")}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
