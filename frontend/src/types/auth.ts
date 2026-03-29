// src/types/auth.ts
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  userId: number;
  email: string;
  name: string;
  accessToken: string;
}

export interface ApiResponse<T> {
  meta: { status: string; warning?: string | null; warnings?: string[] } | null;
  data: T;
  errors: Array<{ code: string; message: string }>;
}
