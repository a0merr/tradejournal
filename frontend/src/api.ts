// Thin typed client over the tradejournal REST API. Token is held in localStorage.

// In dev this is empty, so requests stay same-origin and Vite proxies /api to the
// backend. In a deployed static build it's the backend's absolute URL.
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

const TOKEN_KEY = "tradejournal.token";
const REFRESH_KEY = "tradejournal.refreshToken";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

/** Stores a whole session. Passing null clears both tokens (sign-out). */
export function setSession(auth: AuthResponse | null) {
  setToken(auth?.token ?? null);
  if (auth) localStorage.setItem(REFRESH_KEY, auth.refreshToken);
  else localStorage.removeItem(REFRESH_KEY);
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  refreshToken: string;
  refreshExpiresInMinutes: number;
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
  /** FIFO cost basis of what is still open. Null once the position is flat. */
  avgEntryPrice: string | null;
  /** Average across every fill, buys and sells alike — an execution figure, not a basis. */
  avgFillPrice: string;
  totalFees: string;
  fillCount: number;
  lastFilledAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
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
  closedTrades: number;
  totalFees: string;
  grossNotional: string;
  openExposure: string;
  realizedPnl: string | null;
  winRate: string | null;
}

export interface ImportResult {
  imported: number;
  /** Rows whose externalId was already on file — a re-import, not a failure. */
  skipped: number;
  failed: number;
  errors: { line: number; message: string }[];
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

/**
 * Refresh tokens are single-use, so two concurrent refreshes would spend the same token twice
 * and the server would (correctly) treat the second as a stolen-token replay and kill the
 * session. One in-flight refresh is shared by every caller that is waiting on it.
 */
let refreshInFlight: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = rotateTokens().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function rotateTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  try {
    const resp = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!resp.ok) {
      setSession(null);
      return false;
    }
    setSession((await resp.json()) as AuthResponse);
    return true;
  } catch {
    return false; // network failure: keep the session, let the caller surface the error
  }
}

/** Reads the RFC 7807 `detail` the API returns for every failure. */
async function problemDetail(resp: Response): Promise<string> {
  try {
    const body = await resp.json();
    return body.detail ?? body.message ?? resp.statusText;
  } catch {
    return resp.statusText;
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}/api${path}`, { ...init, headers });
  if (resp.status === 401) {
    // A 401 from /auth/* is a credentials failure, not an expired access token.
    if (allowRetry && !path.startsWith("/auth/") && (await refreshSession())) {
      return request<T>(path, init, false);
    }
    if (path.startsWith("/auth/")) {
      throw new ApiError(401, await problemDetail(resp));
    }
    setSession(null);
    throw new ApiError(401, "Session expired — please sign in again.");
  }
  if (!resp.ok) {
    throw new ApiError(resp.status, await problemDetail(resp));
  }
  if (resp.status === 204) return undefined as T;
  return resp.json() as Promise<T>;
}

// Multipart upload — bypasses the JSON request() helper. Browser sets the boundary.
// A standalone function rather than a method so the 401 retry can recurse without the
// object literal referring to itself.
async function importFills(
  accountId: number,
  file: File,
  allowRetry = true,
): Promise<ImportResult> {
  const form = new FormData();
  form.append("file", file);
  form.append("accountId", String(accountId));
  const headers = new Headers();
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const resp = await fetch(`${API_BASE}/api/fills/import`, {
    method: "POST",
    headers,
    body: form,
  });
  if (resp.status === 401) {
    if (allowRetry && (await refreshSession())) {
      return importFills(accountId, file, false);
    }
    setSession(null);
    throw new ApiError(401, "Session expired — please sign in again.");
  }
  if (!resp.ok) {
    throw new ApiError(resp.status, await problemDetail(resp));
  }
  return resp.json() as Promise<ImportResult>;
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
  orders: (page = 0, size = 50) =>
    request<PageResponse<Order>>(`/orders?page=${page}&size=${size}`),
  performance: (accountId: number) =>
    request<Performance>(`/performance?accountId=${accountId}`),

  /** Ends the session server-side, so the refresh token cannot outlive the sign-out. */
  async logout(): Promise<void> {
    const refreshToken = getRefreshToken();
    setSession(null);
    if (!refreshToken) return;
    try {
      await fetch(`${API_BASE}/api/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    } catch {
      /* local sign-out already happened; the token expires on its own */
    }
  },

  importFills,
};
