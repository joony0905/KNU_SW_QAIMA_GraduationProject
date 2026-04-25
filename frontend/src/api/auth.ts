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

export interface TokenRefreshResponse {
  accessToken: string;
}

export const login = async (payload: LoginRequest): Promise<LoginResponse> => {
  const res = await api.post("/auth/login", payload);
  // ApiResponse<LoginResponseDto>
  return res.data.data;
};

// AT 재발급 — RT는 httpOnly 쿠키로 자동 전송된다.
export const refresh = async (): Promise<TokenRefreshResponse> => {
  const res = await api.post("/auth/refresh");
  return res.data.data;
};

// 로그아웃 — 서버가 RT 쿠키를 만료시킨다.
export const logout = async (): Promise<void> => {
  await api.post("/auth/logout");
};

// 이메일 인증 요청 (인증코드 발송)
export const requestEmailVerification = async (email: string): Promise<void> => {
  await api.post("/email/verification/request", { email });
};

// 이메일 인증 확인 (인증코드 검증)
export const confirmEmailVerification = async (
  email: string,
  code: string
): Promise<void> => {
  await api.post("/email/verification/confirm", { email, code });
};

// 회원가입
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  birthdate: string;
  phone: string;
}

export const signup = async (payload: SignupRequest): Promise<void> => {
  await api.post("/auth/signup", payload);
};
