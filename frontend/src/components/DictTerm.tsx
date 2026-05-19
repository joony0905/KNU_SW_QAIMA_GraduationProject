import { useState } from "react";
import { createPortal } from "react-dom";
import { useDictionary } from "./DictContext";

interface DictTermProps {
  term: string;
  children: React.ReactNode;
}

export default function DictTerm({ term, children }: DictTermProps) {
  const { terms, ready, glossaryHover } = useDictionary();
  const [hovered, setHovered] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  const entry = ready ? terms.get(term.toLowerCase()) : undefined;

  if (!entry) {
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
          {children}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            setModalOpen(true);
          }}
          className="w-3.5 h-3.5 rounded-full bg-ink-4 hover:bg-accent text-[9px] font-bold text-white flex items-center justify-center flex-shrink-0 transition-colors"
          title={entry.term}
        >
          ?
        </button>

        {/* glossaryHover 환경설정이 켜져 있으면 클릭 없이 hover 만으로 설명을 보여준다 */}
        {glossaryHover && hovered && (
          <span className="absolute left-0 top-full z-[9998] mt-1.5 w-72 max-w-[80vw] rounded-xl bg-surface border border-line shadow-pop px-4 py-3 text-left cursor-default">
            <span className="block text-sm font-semibold text-accent mb-1">
              {entry.term}
            </span>
            <span className="block text-xs text-ink-2 leading-relaxed max-h-32 overflow-hidden whitespace-pre-wrap">
              {entry.description}
            </span>
            <span className="block mt-1.5 text-[11px] text-ink-4">
              ? 클릭 시 전체 설명
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
              <h2 className="text-lg font-semibold text-accent">{entry.term}</h2>
              <button
                onClick={() => setModalOpen(false)}
                className="text-ink-4 hover:text-ink-2 text-xl leading-none"
              >
                &times;
              </button>
            </div>

            <div className="px-5 py-4 overflow-y-auto flex-1">
              <p className="text-sm text-ink-2 leading-relaxed whitespace-pre-wrap">
                {entry.description}
              </p>
              {entry.source && (
                <p className="mt-3 text-xs text-ink-4">출처: {entry.source}</p>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
