export function getErrorMessage(status?: number, errorCode?: string) {
  const normalizedCode = errorCode?.split(":", 1)[0];

  switch (normalizedCode) {
    case "INSUFFICIENT_CREDIT":
      return "분석 토큰이 부족합니다.";
    case "UNAUTHORIZED":
    case "INVALID_TOKEN":
      return "로그인이 필요합니다.";
    case "FORBIDDEN":
      return "접근 권한이 없습니다.";
    case "RESOURCE_NOT_FOUND":
    case "META_NOT_FOUND":
      return "요청하신 정보를 찾을 수 없습니다.";
    case "VALIDATION_ERROR":
    case "BAD_REQUEST":
      return "요청 값이 올바르지 않습니다.";
    case "ANALYSIS_API_FAILED":
    case "FEATURE1_ANALYZE_FAILED":
    case "FEATURE2_ANALYZE_FAILED":
    case "FEATURE3_ANALYZE_FAILED":
    case "PEER_CLUSTER_FAILED":
    case "NEWS_SENTIMENT_FAILED":
      return "분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    case "EXTERNAL_API_FAILED":
    case "EXTERNAL_DECODE_FAILED":
    case "KIS_HTTP_ERROR":
    case "KIS_DECODE_ERROR":
    case "KIS_BIZ_ERROR":
      return "외부 데이터 연동 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    case "CONFIGURATION_ERROR":
    case "INTERNAL_ERROR":
      return "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
  }

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
