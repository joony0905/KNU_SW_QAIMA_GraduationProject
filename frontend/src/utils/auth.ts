import { getAccessToken } from "../api/tokenStore";

export const isLoggedIn = (): boolean => {
  return !!getAccessToken();
};
