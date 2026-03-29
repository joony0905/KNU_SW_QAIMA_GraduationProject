import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { DictionaryTermDto } from "../types/dictionary";

interface ApiResponse<T> {
  meta: { status: string; warning?: string | null; warnings?: string[] } | null;
  data: T;
  errors: Array<{ code: string; message: string }>;
}

export const fetchDictionaryTerms = async (
  params: { q?: string; initial?: string; page?: number; size?: number } = {},
): Promise<DictionaryTermDto[]> => {
  const mergedParams = { size: 500, ...params };
  const res = await api.get<ApiResponse<DictionaryTermDto[]>>(
    ENDPOINTS.dictionary.search(mergedParams),
  );
  return res.data.data;
};

export const fetchDictionaryTerm = async (
  term: string,
): Promise<DictionaryTermDto> => {
  const res = await api.get<ApiResponse<DictionaryTermDto>>(
    ENDPOINTS.dictionary.getOne(term),
  );
  return res.data.data;
};
