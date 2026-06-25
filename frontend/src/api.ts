// Thin typed client over the tradejournal REST API. Token is held in localStorage.

// In dev this is empty, so requests stay same-origin and Vite proxies /api to the
// backend. In a deployed static build it's the backend's absolute URL.
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

const TOKEN_KEY = "tradejournal.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
}

export interface Account {
  id: number;
  broker: string;
  baseCurrency: string;
  createdAt: string;
}

export interface Position {
  accountId: number;
  instrumentId: number;
  symbol: string;
  exchange: string;
  netQuantity: string;
  avgPrice: string;
  totalFees: string;
  fillCount: number;
  lastFilledAt: string;
}

export interface Order {
  id: number;
  accountId: number;
  symbol: string;
  exchange: string;
  side: "BUY" | "SELL";
  type: "MARKET" | "LIMIT";
  quantity: string;
  status: string;
  createdAt: string;
}

export interface Performance {
  accountId: number;
  totalOrders: number;
  totalFills: number;
  openPositions: number;
  totalFees: string;
  grossNotional: string;
  openExposure: string;
  realizedPnl: string | null;
  winRate: string | null;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}/api${path}`, { ...init, headers });
  if (resp.status === 401) {
    setToken(null);
    throw new ApiError(401, "Session expired — please sign in again.");
  }
  if (!resp.ok) {
    let detail = resp.statusText;
    try {
      const body = await resp.json();
      detail = body.detail ?? body.message ?? detail;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(resp.status, detail);
  }
  if (resp.status === 204) return undefined as T;
  return resp.json() as Promise<T>;
}

export const api = {
  register: (email: string, password: string) =>
    request<AuthResponse>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  accounts: () => request<Account[]>("/accounts"),
  positions: () => request<Position[]>("/positions"),
  orders: () => request<Order[]>("/orders"),
  performance: (accountId: number) =>
    request<Performance>(`/performance?accountId=${accountId}`),
};
