import { vi } from "vitest";

/** A JSON response, or an RFC 7807 problem document when `status` is an error. */
export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function problem(status: number, detail: string): Response {
  return json({ type: "about:blank", title: "Error", status, detail }, status);
}

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>;

/**
 * Replaces global fetch with a router keyed by "METHOD /path". Each route can be a single
 * handler or a queue consumed one call at a time, so a test can script "401, then 200".
 */
export function mockFetch(routes: Record<string, Handler | Handler[]>) {
  const calls: { method: string; path: string; init: RequestInit }[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof input === "string" ? input : input.toString();
    const method = (init.method ?? "GET").toUpperCase();
    const path = new URL(url, "http://localhost").pathname;
    calls.push({ method, path, init });
    const route = routes[`${method} ${path}`];
    if (!route) throw new Error(`unexpected request: ${method} ${path}`);
    const handler = Array.isArray(route) ? route.shift() : route;
    if (!handler) throw new Error(`route exhausted: ${method} ${path}`);
    return handler(url, init);
  });
  vi.stubGlobal("fetch", fn);
  return {
    calls,
    count: (method: string, path: string) =>
      calls.filter((c) => c.method === method && c.path === path).length,
  };
}

export const session = (n: number) => ({
  token: `access-${n}`,
  tokenType: "Bearer",
  expiresInMinutes: 15,
  refreshToken: `refresh-${n}`,
  refreshExpiresInMinutes: 10080,
});
