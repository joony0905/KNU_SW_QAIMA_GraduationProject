export function getErrorMessage(status?: number, errorCode?: string) {
  if (status === 401) return "로그인이 필요합니다.";
  if (status === 403) return "접근 권한이 없습니다.";
  if (status === 404) return "요청하신 정보를 찾을 수 없습니다.";
  if (status === 500)
    return "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
  return "알 수 없는 오류가 발생했습니다.";
}
