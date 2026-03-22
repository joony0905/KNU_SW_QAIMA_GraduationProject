export const isLoggedIn = (): boolean => {
  const token = localStorage.getItem("qaima_token");
  return !!token;
};
