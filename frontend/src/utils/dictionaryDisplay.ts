import type { DictionaryTermDto } from "../types/dictionary";

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
  return entry.description ?? "";
}
