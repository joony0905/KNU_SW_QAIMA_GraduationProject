import { useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { useDictionary } from "./DictContext";
import { useDictTermOwner } from "./DictSeenScope";
import {
  dictionaryDescription,
  dictionaryTermLabel,
  isEnglishLanguage,
} from "../utils/dictionaryDisplay";

interface DictTermProps {
  term: string;
  children: React.ReactNode;
}

export default function DictTerm({ term, children }: DictTermProps) {
  const { i18n } = useTranslation();
  const { terms, ready, glossaryHover } = useDictionary();
  const [hovered, setHovered] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const isOwner = useDictTermOwner(term);

  const entry = ready ? terms.get(term.toLowerCase()) : undefined;
  const description = dictionaryDescription(entry, i18n.language);
  const isEnglish = isEnglishLanguage(i18n.language);
  const label = dictionaryTermLabel(entry, i18n.language);
  const englishTermLabel = entry?.termEn?.trim() || null;
  const displayLabel = isEnglish && englishTermLabel ? englishTermLabel : children;
  const headingLabel =
    isEnglish && englishTermLabel
      ? englishTermLabel
      : typeof children === "string"
        ? children
        : label;

  if (!entry) {
    return <>{children}</>;
  }

  // 한 화면에 같은 용어가 여러 번 등장하면 첫 번째 instance 만 ? 툴팁을 표시한다.
  if (!isOwner) {
    return <>{children}</>;
  }

  return (
    <>
      <span
        className="inline-flex items-start gap-0.5 relative"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <span
          className={`transition-colors ${
            hovered ? "font-bold bg-accent-soft rounded px-0.5" : ""
          }`}
        >
          {displayLabel}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            setModalOpen(true);
          }}
          className="w-3.5 h-3.5 rounded-full bg-ink-4 hover:bg-accent text-[9px] font-bold text-white flex items-center justify-center flex-shrink-0 transition-colors"
          title={headingLabel}
        >
          ?
        </button>

        {/* glossaryHover 환경설정이 켜져 있으면 클릭 없이 hover 만으로 설명을 보여준다 */}
        {glossaryHover && hovered && (
          <span className="absolute left-0 top-full z-[9998] mt-1.5 w-72 max-w-[80vw] rounded-xl bg-surface border border-line shadow-pop px-4 py-3 text-left cursor-default">
            <span className="block text-sm font-semibold text-accent mb-1">
              {headingLabel}
            </span>
            <span className="block text-xs text-ink-2 leading-relaxed max-h-32 overflow-hidden whitespace-pre-wrap">
              {description}
            </span>
            <span className="block mt-1.5 text-[11px] text-ink-4">
              {isEnglish ? "Click ? for full description" : "? 클릭 시 전체 설명"}
            </span>
          </span>
        )}
      </span>

      {modalOpen && createPortal(
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40"
          onClick={() => setModalOpen(false)}
        >
          <div
            className="bg-surface rounded-2xl shadow-xl max-w-lg w-[90%] max-h-[70vh] flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-4 border-b border-line">
              <h2 className="text-lg font-semibold text-accent">{headingLabel}</h2>
              <button
                onClick={() => setModalOpen(false)}
                className="text-ink-4 hover:text-ink-2 text-xl leading-none"
              >
                &times;
              </button>
            </div>

            <div className="px-5 py-4 overflow-y-auto flex-1">
              <p className="text-sm text-ink-2 leading-relaxed whitespace-pre-wrap">
                {description}
              </p>
              {entry.source && (
                <p className="mt-3 text-xs text-ink-4">
                  {isEnglish ? "Source" : "출처"}: {entry.source}
                </p>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
