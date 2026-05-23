import { useMemo } from "react";
import type { ReactNode } from "react";
import DictTerm from "./DictTerm";
import { useDictionary } from "./DictContext";

interface DictionaryTextProps {
  text: string;
}

const KOREAN_PARTICLES = [
  "으로부터",
  "으로는",
  "으로서",
  "으로써",
  "에서",
  "에게",
  "부터",
  "까지",
  "보다",
  "처럼",
  "으로",
  "로는",
  "로서",
  "로써",
  "은",
  "는",
  "이",
  "가",
  "을",
  "를",
  "와",
  "과",
  "도",
  "에",
  "의",
  "만",
  "로",
].sort((a, b) => b.length - a.length);

const isWordChar = (ch: string | undefined) => (
  Boolean(ch && /[A-Za-z0-9가-힣]/.test(ch))
);

const startsWithParticle = (text: string) => (
  KOREAN_PARTICLES.some((particle) => text.startsWith(particle))
);

const hasStartBoundary = (text: string, start: number) => {
  if (start <= 0) return true;
  return !isWordChar(text[start - 1]);
};

const hasEndBoundary = (text: string, end: number) => {
  const next = text[end];
  if (!next) return true;
  if (!isWordChar(next)) return true;
  return /[가-힣]/.test(next) && startsWithParticle(text.slice(end));
};

export default function DictionaryText({ text }: DictionaryTextProps) {
  const { terms, ready } = useDictionary();

  const termsByFirstChar = useMemo(() => {
    const grouped = new Map<string, string[]>();
    if (!ready || terms.size === 0) return grouped;

    const normalizedTerms = Array.from(terms.values())
      .map((entry) => entry.term.trim())
      .filter((term) => term.length >= 2)
      .sort((a, b) => b.length - a.length);

    for (const term of normalizedTerms) {
      const first = term[0]?.toLowerCase();
      if (!first) continue;
      const group = grouped.get(first) ?? [];
      group.push(term);
      grouped.set(first, group);
    }
    return grouped;
  }, [ready, terms]);

  const content = useMemo(() => {
    if (!ready || !text || termsByFirstChar.size === 0) return text;

    const nodes: ReactNode[] = [];
    const lowerText = text.toLowerCase();
    let cursor = 0;
    let plainStart = 0;
    let key = 0;

    while (cursor < text.length) {
      const candidates = termsByFirstChar.get(lowerText[cursor]);
      let matchedTerm: string | null = null;

      if (candidates && hasStartBoundary(text, cursor)) {
        for (const term of candidates) {
          const end = cursor + term.length;
          if (
            lowerText.startsWith(term.toLowerCase(), cursor)
            && hasEndBoundary(text, end)
          ) {
            matchedTerm = term;
            break;
          }
        }
      }

      if (!matchedTerm) {
        cursor += 1;
        continue;
      }

      if (plainStart < cursor) {
        nodes.push(text.slice(plainStart, cursor));
      }

      const matchedText = text.slice(cursor, cursor + matchedTerm.length);
      nodes.push(
        <DictTerm key={`dict-${key}`} term={matchedTerm}>
          {matchedText}
        </DictTerm>
      );
      key += 1;
      cursor += matchedTerm.length;
      plainStart = cursor;
    }

    if (plainStart < text.length) {
      nodes.push(text.slice(plainStart));
    }

    return nodes.length > 0 ? nodes : text;
  }, [ready, termsByFirstChar, text]);

  return <>{content}</>;
}
