import { createContext, useContext, useEffect, useState } from "react";
import type { DictionaryTermDto } from "../types/dictionary";
import { fetchDictionaryTerms } from "../api/dictionary";

interface DictContextValue {
  terms: Map<string, DictionaryTermDto>;
  ready: boolean;
}

const DictCtx = createContext<DictContextValue>({ terms: new Map(), ready: false });

export function DictProvider({ children }: { children: React.ReactNode }) {
  const [terms, setTerms] = useState<Map<string, DictionaryTermDto>>(new Map());
  const [ready, setReady] = useState(false);

  useEffect(() => {
    fetchDictionaryTerms({ size: 2000 })
      .then((list) => {
        const map = new Map<string, DictionaryTermDto>();
        for (const t of list) {
          map.set(t.term.toLowerCase(), t);
        }
        setTerms(map);
      })
      .catch(() => {})
      .finally(() => setReady(true));
  }, []);

  return (
    <DictCtx.Provider value={{ terms, ready }}>
      {children}
    </DictCtx.Provider>
  );
}

export function useDictionary() {
  return useContext(DictCtx);
}
