// src/api/auth.ts
import api from "./apiClient";

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

export const login = async (payload: LoginRequest): Promise<LoginResponse> => {
  const res = await api.post("/auth/login", payload);
  // ApiResponse<LoginResponseDto>라고 가정
  return res.data.data;
};
