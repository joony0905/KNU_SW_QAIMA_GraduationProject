export interface DictionaryTermDto {
  term: string;
  initial: string;
  description: string;
  descriptionEn?: string | null;
  source?: string;
  tag?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DictionaryInitialCountDto {
  initial: string;
  count: number;
}
