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

// 비밀번호 재설정 링크 발송 요청
// 백엔드는 가입되지 않은 이메일이어도 200 OK 로 응답한다(이메일 enumeration 방지).
// 60초 rate limit 이 걸려 있다.
export const requestPasswordReset = async (email: string): Promise<void> => {
  await api.post("/email/pwdreset/request", { email });
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

// 소셜 로그인 — 백엔드가 RT 쿠키 발급 후 success URL 로 다시 리다이렉트한다.
export type SocialProvider = "google" | "kakao" | "naver";

const OAUTH2_BASE_URL = "http://localhost:8080/api/v1/auth/oauth2";

export const startOAuth2Login = (provider: SocialProvider): void => {
  // 로그인 후 돌아갈 경로를 기록해 둔다.
  const currentPath = window.location.pathname + window.location.search;
  if (
    currentPath !== "/login" &&
    !currentPath.startsWith("/login/oauth2/")
  ) {
    sessionStorage.setItem("qaima_redirect", currentPath);
  }
  window.location.href = `${OAUTH2_BASE_URL}/${provider}`;
};
