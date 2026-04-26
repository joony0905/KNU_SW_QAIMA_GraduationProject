// 사용자 기본 정보(이메일/이름) 보관용 모듈 스코프 스토어.
// AT 와 달리 민감 정보가 아니므로 새로고침 대응을 위해 sessionStorage 에 병행 저장한다.

const SS_KEY = "qaima_user";

export interface UserInfo {
  email: string;
  name?: string;
}

let currentUser: UserInfo | null = null;

const readFromStorage = (): UserInfo | null => {
  try {
    const raw = sessionStorage.getItem(SS_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as UserInfo;
    if (!parsed?.email) return null;
    return parsed;
  } catch {
    return null;
  }
};

export const setUser = (user: UserInfo | null): void => {
  currentUser = user;
  try {
    if (user) {
      sessionStorage.setItem(SS_KEY, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(SS_KEY);
    }
  } catch {
    // sessionStorage 접근 불가 환경은 조용히 무시
  }
};

export const getUser = (): UserInfo | null => {
  if (currentUser) return currentUser;
  const fromStorage = readFromStorage();
  if (fromStorage) {
    currentUser = fromStorage;
  }
  return currentUser;
};

export const clearUser = (): void => {
  currentUser = null;
  try {
    sessionStorage.removeItem(SS_KEY);
  } catch {
    // no-op
  }
};
