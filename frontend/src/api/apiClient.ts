import axios from "axios";
import { getErrorMessage } from "../utils/errorMessage";

const api = axios.create({
  baseURL: "http://localhost:8080/api/v1", // 나중에 실 서버 주소로 교체
  withCredentials: true,
});

// 1) 요청 인터셉터: 토큰 자동 첨부
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("qaima_token");
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 2) 응답 인터셉터: 공통 에러 처리 뼈대
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status;
    const errorCode = error.response?.data?.errorCode;
    const message = getErrorMessage(status, errorCode);

    console.error("API Error:", status, errorCode, message);

    if (status === 401) {
      localStorage.removeItem("qaima_token");
      localStorage.removeItem("qaima_refresh_token");
      window.location.href = "/login";
    } else if (status === 403) {
      window.location.href = "/forbidden";
    } else if (status === 500) {
      console.error("서버 내부 오류가 발생했습니다.");
    }

    return Promise.reject(error);
  }
);

export default api;
