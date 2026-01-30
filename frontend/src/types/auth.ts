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
  success: boolean;
  data: T;
  error: string | null;
}
