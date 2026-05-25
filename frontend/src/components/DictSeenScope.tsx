import { createContext, useContext, useEffect, useId, useRef, useSyncExternalStore } from "react";

type Listener = () => void;

interface Registry {
  owners: Map<string, string[]>;
  listeners: Set<Listener>;
  notify: () => void;
}

const DictSeenContext = createContext<Registry | null>(null);

export function DictSeenScope({ children }: { children: React.ReactNode }) {
  const ref = useRef<Registry | null>(null);
  if (!ref.current) {
    const reg: Registry = {
      owners: new Map(),
      listeners: new Set(),
      notify: () => {
        for (const l of reg.listeners) l();
      },
    };
    ref.current = reg;
  }
  return (
    <DictSeenContext.Provider value={ref.current}>
      {children}
    </DictSeenContext.Provider>
  );
}

// 같은 화면에 같은 term 이 여러번 등장할 때 가장 먼저 mount 된 instance 만 true 를 반환한다.
// scope 가 없으면 항상 true (dedupe 비활성).
export function useDictTermOwner(term: string): boolean {
  const reg = useContext(DictSeenContext);
  const id = useId();
  const key = term.toLowerCase();

  useEffect(() => {
    if (!reg) return;
    const list = reg.owners.get(key) ?? [];
    if (!list.includes(id)) {
      list.push(id);
      reg.owners.set(key, list);
      reg.notify();
    }
    return () => {
      const cur = reg.owners.get(key);
      if (!cur) return;
      const next = cur.filter((x) => x !== id);
      if (next.length === 0) reg.owners.delete(key);
      else reg.owners.set(key, next);
      reg.notify();
    };
  }, [reg, key, id]);

  const subscribe = (cb: Listener) => {
    if (!reg) return () => {};
    reg.listeners.add(cb);
    return () => {
      reg.listeners.delete(cb);
    };
  };
  const getSnapshot = () => {
    if (!reg) return true;
    const list = reg.owners.get(key);
    if (!list || list.length === 0) return true;
    return list[0] === id;
  };

  return useSyncExternalStore(subscribe, getSnapshot, () => true);
}
