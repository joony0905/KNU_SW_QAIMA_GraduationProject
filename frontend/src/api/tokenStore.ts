import axios from "axios";

// 액세스 토큰은 XSS 공격 노출을 줄이기 위해 모듈 스코프 메모리에만 보관한다.
// 새로고침 시에는 App 부트스트랩 단계에서 /auth/refresh 로 다시 발급받는다.
let accessToken: string | null = null;

export const setAccessToken = (token: string | null): void => {
  accessToken = token;
};

export const getAccessToken = (): string | null => accessToken;

export const clearAccessToken = (): void => {
  accessToken = null;
};

// App 최초 마운트 시 RT 쿠키로 AT 복원 시도.
// apiClient 인터셉터를 우회하기 위해 직접 axios 로 호출한다.
// 실패(비로그인/만료 등)는 조용히 무시하고 비로그인 상태로 진행한다.
export const bootstrapAccessToken = async (baseUrl: string): Promise<boolean> => {
  try {
    const res = await axios.post(
      `${baseUrl}/auth/refresh`,
      {},
      { withCredentials: true }
    );
    const token: string | undefined = res.data?.data?.accessToken;
    if (token) {
      setAccessToken(token);
      return true;
    }
    return false;
  } catch {
    return false;
  }
};
