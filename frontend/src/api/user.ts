// src/api/user.ts
// 마이페이지 — 로그인 사용자 기준 내 정보 조회/수정.
// 백엔드가 JWT에서 userId를 읽으므로 프론트는 userId를 보내지 않는다.
import api from "./apiClient";

// GET /users/me 응답 (백엔드 UserResponseDto, camelCase)
export interface MyProfile {
  userId: number;
  email: string;
  name: string;
  phone: string;
  birthdate: string;
  gender: string | null;
  country: string | null;
  experience: string | null;
  status: string | null;
  glossaryHover: boolean;
}

// PATCH /users/me body — 보낸 필드만 반영(빈 값/누락은 미반영).
export interface MyProfileUpdate {
  name?: string;
  phone?: string;
  experience?: string;
  glossaryHover?: boolean;
}

export interface SocialProfileCompleteRequest {
  name: string;
  phone: string;
  birthdate: string;
  country: string;
}

export interface MyRiskProfile {
  defaultRiskGamma: number | null;
  profileType: string | null;
}

export interface MyRiskProfileUpdate {
  defaultRiskGamma: number;
}

export const getMyProfile = async (): Promise<MyProfile> => {
  const res = await api.get("/users/me");
  // ApiResponse<UserResponseDto>
  return res.data.data;
};

export const updateMyProfile = async (
  payload: MyProfileUpdate
): Promise<MyProfile> => {
  const res = await api.patch("/users/me", payload);
  return res.data.data;
};

export const completeSocialProfile = async (
  payload: SocialProfileCompleteRequest
): Promise<MyProfile> => {
  const res = await api.patch("/users/me/social-profile", payload);
  return res.data.data;
};

export const getMyRiskProfile = async (): Promise<MyRiskProfile> => {
  const res = await api.get("/users/me/risk-profile");
  return res.data.data;
};

export const updateMyRiskProfile = async (
  payload: MyRiskProfileUpdate
): Promise<MyRiskProfile> => {
  const res = await api.patch("/users/me/risk-profile", payload);
  return res.data.data;
};
