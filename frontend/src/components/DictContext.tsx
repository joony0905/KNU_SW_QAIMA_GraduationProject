import { createContext, useContext, useEffect, useState } from "react";
import type { DictionaryTermDto } from "../types/dictionary";
import { fetchDictionaryTerms } from "../api/dictionary";
import { getMyProfile } from "../api/user";
import { isLoggedIn } from "../utils/auth";

interface DictContextValue {
  terms: Map<string, DictionaryTermDto>;
  ready: boolean;
  // 사용자 환경설정: 용어에 마우스를 올리면(클릭 없이) 설명을 보여줄지 여부.
  // 백엔드 User.glossaryHover (기본 false). 비로그인 시 false.
  glossaryHover: boolean;
  setGlossaryHover: (value: boolean) => void;
}

const DictCtx = createContext<DictContextValue>({
  terms: new Map(),
  ready: false,
  glossaryHover: false,
  setGlossaryHover: () => {},
});

export function DictProvider({ children }: { children: React.ReactNode }) {
  const [terms, setTerms] = useState<Map<string, DictionaryTermDto>>(new Map());
  const [ready, setReady] = useState(false);
  const [glossaryHover, setGlossaryHover] = useState(false);

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

  // 로그인 사용자면 글로서리 hover 환경설정을 한 번 읽어와 앱 전역에 반영.
  // 비로그인 시 /users/me(인증 필요) 호출 → 401 → 강제 로그인 이동을 피하려고
  // 호출 자체를 막는다. (워치리스트 동기화와 동일한 가드)
  useEffect(() => {
    if (!isLoggedIn()) return;
    let alive = true;
    getMyProfile()
      .then((p) => {
        if (alive) setGlossaryHover(p.glossaryHover);
      })
      .catch(() => {
        // 프로필 조회 실패 시 기본값(false) 유지
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <DictCtx.Provider value={{ terms, ready, glossaryHover, setGlossaryHover }}>
      {children}
    </DictCtx.Provider>
  );
}

export function useDictionary() {
  return useContext(DictCtx);
}
