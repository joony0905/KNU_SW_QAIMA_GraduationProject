import i18n from "../i18n";

const isEnglish = () => i18n.language.toLowerCase().startsWith("en");

const MESSAGES = {
  insufficientCredit: { ko: "분석 토큰이 부족합니다.", en: "Not enough analysis credits." },
  unauthorized: { ko: "로그인이 필요합니다.", en: "Login is required." },
  forbidden: { ko: "접근 권한이 없습니다.", en: "You do not have permission to access this." },
  notFound: { ko: "요청하신 정보를 찾을 수 없습니다.", en: "The requested information could not be found." },
  badRequest: { ko: "요청 값이 올바르지 않습니다.", en: "The request values are invalid." },
  analysisFailed: { ko: "분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", en: "An error occurred during analysis. Please try again shortly." },
  externalFailed: { ko: "외부 데이터 연동 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", en: "An external data integration error occurred. Please try again shortly." },
  serverError: { ko: "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", en: "A server error occurred. Please try again shortly." },
  unknown: { ko: "알 수 없는 오류가 발생했습니다.", en: "An unknown error occurred." },
} as const;

const msg = (key: keyof typeof MESSAGES) => (isEnglish() ? MESSAGES[key].en : MESSAGES[key].ko);

export function getErrorMessage(status?: number, errorCode?: string) {
  const normalizedCode = errorCode?.split(":", 1)[0];

  switch (normalizedCode) {
    case "INSUFFICIENT_CREDIT":
      return msg("insufficientCredit");
    case "UNAUTHORIZED":
    case "INVALID_TOKEN":
      return msg("unauthorized");
    case "FORBIDDEN":
      return msg("forbidden");
    case "RESOURCE_NOT_FOUND":
    case "META_NOT_FOUND":
      return msg("notFound");
    case "VALIDATION_ERROR":
    case "BAD_REQUEST":
      return msg("badRequest");
    case "ANALYSIS_API_FAILED":
    case "FEATURE1_ANALYZE_FAILED":
    case "FEATURE2_ANALYZE_FAILED":
    case "FEATURE3_ANALYZE_FAILED":
    case "PEER_CLUSTER_FAILED":
    case "NEWS_SENTIMENT_FAILED":
      return msg("analysisFailed");
    case "EXTERNAL_API_FAILED":
    case "EXTERNAL_DECODE_FAILED":
    case "KIS_HTTP_ERROR":
    case "KIS_DECODE_ERROR":
    case "KIS_BIZ_ERROR":
      return msg("externalFailed");
    case "CONFIGURATION_ERROR":
    case "INTERNAL_ERROR":
      return msg("serverError");
  }

  if (errorCode === "INSUFFICIENT_CREDIT" || status === 402) {
    return msg("insufficientCredit");
  }
  if (status === 401) return msg("unauthorized");
  if (status === 403) return msg("forbidden");
  if (status === 404) return msg("notFound");
  if (status === 500)
    return msg("serverError");
  return msg("unknown");
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
  return mapped === msg("unknown")
    ? response.data?.errors?.[0]?.message ?? fallback
    : mapped;
}
