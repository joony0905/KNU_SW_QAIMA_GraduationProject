import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { getErrorMessage } from "../utils/errorMessage";
import { clearAccessToken, getAccessToken, setAccessToken } from "./tokenStore";

declare module "axios" {
  export interface AxiosRequestConfig {
    _skipAuthRedirect?: boolean;
  }
}

const BASE_URL = "http://localhost:8080/api/v1"; // 나중에 실 서버 주소로 교체

// 401 재시도 여부 추적용 플래그
interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _skipAuthRedirect?: boolean;
}

const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true, // RT httpOnly 쿠키 전송
});

// 1) 요청 인터셉터: 메모리 AT 자동 첨부
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// refresh 요청이 동시에 여러 번 나가지 않도록 single-flight 처리
let refreshPromise: Promise<string> | null = null;

const requestRefresh = (): Promise<string> => {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(
        `${BASE_URL}/auth/refresh`,
        {},
        { withCredentials: true }
      )
      .then((res) => {
        const newToken: string = res.data?.data?.accessToken;
        if (!newToken) {
          throw new Error("refresh response missing accessToken");
        }
        setAccessToken(newToken);
        return newToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

const isAuthEndpoint = (url?: string): boolean => {
  if (!url) return false;
  return (
    url.includes("/auth/login") ||
    url.includes("/auth/refresh") ||
    url.includes("/auth/logout") ||
    url.includes("/auth/signup")
  );
};

const redirectToLogin = (): void => {
  const currentPath = window.location.pathname + window.location.search;
  if (currentPath !== "/login") {
    sessionStorage.setItem("qaima_redirect", currentPath);
  }
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
};

// 2) 응답 인터셉터: 401 → refresh 후 원요청 재시도, 그 외 공통 에러 처리
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const originalRequest = error.config as RetriableRequestConfig | undefined;
    const skipAuthRedirect = Boolean(originalRequest?._skipAuthRedirect);

    if (
      status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthEndpoint(originalRequest.url) &&
      !skipAuthRedirect
    ) {
      originalRequest._retry = true;
      try {
        const newToken = await requestRefresh();
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api.request(originalRequest);
      } catch (refreshError) {
        clearAccessToken();
        if (!skipAuthRedirect) {
          redirectToLogin();
        }
        return Promise.reject(refreshError);
      }
    }

    const errorData = error.response?.data as
      | { errorCode?: string; errors?: Array<{ code?: string }> }
      | undefined;
    const errorCode = errorData?.errorCode ?? errorData?.errors?.[0]?.code;
    const message = getErrorMessage(status, errorCode);
    console.error("API Error:", status, errorCode, message);

    if (status === 401 && !skipAuthRedirect) {
      // refresh 도 실패했거나 auth 엔드포인트 자체의 401
      clearAccessToken();
      redirectToLogin();
    } else if (status === 403) {
      window.location.href = "/forbidden";
    } else if (status === 500) {
      console.error("서버 내부 오류가 발생했습니다.");
    }

    return Promise.reject(error);
  }
);

export default api;
