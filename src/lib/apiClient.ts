import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080", // 백엔드 주소 있으면 여기 넣으면 됨
  timeout: 5000,
});

export default api;