import { createContext, useContext, useEffect, useState } from "react";
import type { DictionaryTermDto } from "../types/dictionary";
import { fetchDictionaryTerms } from "../api/dictionary";
import { getMyProfile } from "../api/user";
import { isLoggedIn } from "../utils/auth";
import {
  DEFAULT_INVEST_LEVEL,
  toInvestLevel,
  type InvestLevel,
} from "../utils/investLevel";

// 백엔드 DictionaryService.MAX_PAGE_SIZE = 200. size 를 더 크게 보내도 200으로 잘린다.
// term ASC 정렬이라 200개 안에는 영문 entry 가 먼저 채워져 한글 entry 가 누락되므로,
// 페이지를 끝까지 순회해서 모든 entry 를 모은다.
const DICT_PAGE_SIZE = 200;
const DICT_MAX_PAGES = 50; // 안전장치: 약 10,000 entry 까지 처리

async function fetchAllDictionaryTerms(): Promise<DictionaryTermDto[]> {
  const all: DictionaryTermDto[] = [];
  for (let page = 0; page < DICT_MAX_PAGES; page++) {
    const batch = await fetchDictionaryTerms({ page, size: DICT_PAGE_SIZE });
    if (batch.length === 0) break;
    all.push(...batch);
    if (batch.length < DICT_PAGE_SIZE) break;
  }
  return all;
}

interface DictContextValue {
  terms: Map<string, DictionaryTermDto>;
  ready: boolean;
  // 사용자 환경설정: 용어에 마우스를 올리면(클릭 없이) 설명을 보여줄지 여부.
  // 백엔드 User.glossaryHover (기본 false). 비로그인 시 false.
  glossaryHover: boolean;
  setGlossaryHover: (value: boolean) => void;
  // 사용자 투자레벨(=백엔드 User.experience). 분석 결과 보기 영역 등 전역에서 참조.
  investLevel: InvestLevel;
  investLevelReady: boolean;
  setInvestLevel: (value: InvestLevel) => void;
}

const DictCtx = createContext<DictContextValue>({
  terms: new Map(),
  ready: false,
  glossaryHover: false,
  setGlossaryHover: () => {},
  investLevel: DEFAULT_INVEST_LEVEL,
  investLevelReady: false,
  setInvestLevel: () => {},
});

export function DictProvider({ children }: { children: React.ReactNode }) {
  const [terms, setTerms] = useState<Map<string, DictionaryTermDto>>(new Map());
  const [ready, setReady] = useState(false);
  const [glossaryHover, setGlossaryHover] = useState(false);
  const [investLevelReady, setInvestLevelReady] = useState(false);
  const [investLevel, setInvestLevel] = useState<InvestLevel>(DEFAULT_INVEST_LEVEL);

  useEffect(() => {
    fetchAllDictionaryTerms()
      .then((list) => {
        const map = new Map<string, DictionaryTermDto>();
        for (const t of list) {
          map.set(t.term.toLowerCase(), t);
          if (t.termEn?.trim()) {
            map.set(t.termEn.trim().toLowerCase(), t);
          }
          for (const alias of t.aliases ?? []) {
            if (alias?.trim()) {
              map.set(alias.trim().toLowerCase(), t);
            }
          }
        }
        setTerms(map);
      })
      .catch(() => {})
      .finally(() => setReady(true));
  }, []);

  // 로그인 사용자면 글로서리 hover · 투자레벨을 읽어와 앱 전역에 반영.
  // 비로그인 시 /users/me(인증 필요) 호출 → 401 → 강제 로그인 이동을 피하려고
  // 호출 자체를 막는다. (워치리스트 동기화와 동일한 가드)
  useEffect(() => {
    let alive = true;

    const syncProfile = () => {
      if (!isLoggedIn()) {
        setGlossaryHover(false);
        setInvestLevel(DEFAULT_INVEST_LEVEL);
        setInvestLevelReady(true);
        return;
      }

      setInvestLevelReady(false);
      getMyProfile()
        .then((p) => {
          if (!alive) return;
          setGlossaryHover(p.glossaryHover);
          setInvestLevel(toInvestLevel(p.experience));
        })
        .catch(() => {
          // 프로필 조회 실패 시 기본값 유지
        })
        .finally(() => {
          if (alive) {
            setInvestLevelReady(true);
          }
        });
    };

    syncProfile();
    window.addEventListener("qaima:auth-change", syncProfile);

    return () => {
      alive = false;
      window.removeEventListener("qaima:auth-change", syncProfile);
    };
  }, []);

  return (
    <DictCtx.Provider
      value={{
        terms,
        ready,
        glossaryHover,
        setGlossaryHover,
        investLevel,
        investLevelReady,
        setInvestLevel,
      }}
    >
      {children}
    </DictCtx.Provider>
  );
}

export function useDictionary() {
  return useContext(DictCtx);
}
