export function getErrorMessage(status?: number, errorCode?: string) {
  if (errorCode === "INSUFFICIENT_CREDIT" || status === 402) {
    return "분석 토큰이 부족합니다.";
  }
  if (status === 401) return "로그인이 필요합니다.";
  if (status === 403) return "접근 권한이 없습니다.";
  if (status === 404) return "요청하신 정보를 찾을 수 없습니다.";
  if (status === 500)
    return "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
  return "알 수 없는 오류가 발생했습니다.";
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  const response = (error as {
    response?: {
      status?: number;
      data?: {
        errorCode?: string;
        errors?: Array<{ code?: string; message?: string }>;
      };
    };
  } | null)?.response;

  if (!response) return fallback;

  const errorCode = response.data?.errorCode ?? response.data?.errors?.[0]?.code;
  const mapped = getErrorMessage(response.status, errorCode);
  return mapped === "알 수 없는 오류가 발생했습니다."
    ? response.data?.errors?.[0]?.message ?? fallback
    : mapped;
}
