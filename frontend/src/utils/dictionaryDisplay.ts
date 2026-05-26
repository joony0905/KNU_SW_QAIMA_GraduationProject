import type { DictionaryTermDto } from "../types/dictionary";
import { fallbackDictionaryDescriptionEn } from "./dictionaryDescriptionFallback";

export function isEnglishLanguage(language?: string | null): boolean {
  return (language ?? "").toLowerCase().startsWith("en");
}

export function dictionaryDescription(
  entry: DictionaryTermDto | null | undefined,
  language?: string | null,
): string {
  if (!entry) return "";
  if (isEnglishLanguage(language) && entry.descriptionEn?.trim()) {
    return entry.descriptionEn;
  }
  if (isEnglishLanguage(language)) {
    return fallbackDictionaryDescriptionEn(entry.term) ?? entry.description ?? "";
  }
  return entry.description ?? "";
}

export function dictionaryTermLabel(
  entry: DictionaryTermDto | null | undefined,
  language?: string | null,
): string {
  if (!entry) return "";
  if (isEnglishLanguage(language) && entry.termEn?.trim()) {
    return entry.termEn;
  }
  return entry.term;
}
