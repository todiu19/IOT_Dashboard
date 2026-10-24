export const API_BASE = (import.meta.env?.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");

export const API = {
  login: "/api/auth/login",
  profile: "/api/profile",
  logout: "/api/auth/logout",
  dashboard: "/api/dashboard",
  alertStatistics: "/api/dashboard/alert-statistics",
  sensors: "/api/data-sensor",
  history: "/api/action-history",
  controlDevice: (id) => `/api/device/${id}/control`
};

export async function apiFetch(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    method,
    headers,
    credentials: "include"
  });
  if (response.status === 204) return null;

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }

  if (!response.ok) {
    const error = new Error(typeof payload === "string" ? payload : payload?.message || `HTTP ${response.status}`);
    error.status = response.status;
    error.errorCode = payload?.errorCode;
    throw error;
  }
  if (payload && typeof payload === "object" && "code" in payload) {
    if (payload.code !== 0) throw new Error(payload.message || `API error ${payload.code}`);
    return payload.data ?? null;
  }
  return payload;
}

export function getWebSocketUrl() {
  const url = new URL("/ws", API_BASE);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}
