import { useState } from "react";
import { useDictionary } from "./DictContext";

interface DictTermProps {
  term: string;
  children: React.ReactNode;
}

export default function DictTerm({ term, children }: DictTermProps) {
  const { terms, ready } = useDictionary();
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
            hovered ? "font-bold bg-sky-100 rounded px-0.5" : ""
          }`}
        >
          {children}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            setModalOpen(true);
          }}
          className="relative -top-1 w-3.5 h-3.5 rounded-full bg-zinc-300 hover:bg-sky-400 text-[9px] font-bold text-white flex items-center justify-center flex-shrink-0 transition-colors"
          title={entry.term}
        >
          ?
        </button>
      </span>

      {modalOpen && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40"
          onClick={() => setModalOpen(false)}
        >
          <div
            className="bg-white rounded-2xl shadow-xl max-w-lg w-[90%] max-h-[70vh] flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-200">
              <h2 className="text-lg font-semibold text-sky-600">{entry.term}</h2>
              <button
                onClick={() => setModalOpen(false)}
                className="text-zinc-400 hover:text-zinc-700 text-xl leading-none"
              >
                &times;
              </button>
            </div>

            <div className="px-5 py-4 overflow-y-auto flex-1">
              <p className="text-sm text-zinc-700 leading-relaxed whitespace-pre-wrap">
                {entry.description}
              </p>
              {entry.source && (
                <p className="mt-3 text-xs text-zinc-400">출처: {entry.source}</p>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
