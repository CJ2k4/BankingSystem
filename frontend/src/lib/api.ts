import axios from "axios";

/**
 * Shared Axios instance. Auth interceptors (JWT) are added in Phase 1.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

export interface PingResponse {
  status: string;
  service: string;
  timestamp: string;
}

export async function fetchPing(): Promise<PingResponse> {
  const { data } = await api.get<PingResponse>("/api/v1/ping");
  return data;
}
