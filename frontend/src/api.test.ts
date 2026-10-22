// The API client owns the session lifecycle: when to refresh, when to give up, and
// never spending a single-use refresh token twice. These tests pin that state machine.
import { describe, expect, it } from "vitest";
import { api, ApiError, getRefreshToken, getToken, setSession } from "./api";
import { json, mockFetch, problem, session } from "./test/http";

describe("request + refresh", () => {
  it("sends the bearer token", async () => {
    setSession(session(1));
    const http = mockFetch({ "GET /api/accounts": () => json([]) });
    await api.accounts();
    const headers = new Headers(http.calls[0].init.headers);
    expect(headers.get("Authorization")).toBe("Bearer access-1");
  });

  it("refreshes once on 401, rotates both tokens, and retries the request", async () => {
    setSession(session(1));
    const http = mockFetch({
      "GET /api/accounts": [() => problem(401, "expired"), () => json([{ id: 7 }])],
      "POST /api/auth/refresh": (_url, init) => {
        expect(JSON.parse(String(init.body))).toEqual({ refreshToken: "refresh-1" });
        return json(session(2));
      },
    });

    await expect(api.accounts()).resolves.toEqual([{ id: 7 }]);
    expect(http.count("POST", "/api/auth/refresh")).toBe(1);
    expect(getToken()).toBe("access-2");
    expect(getRefreshToken()).toBe("refresh-2");
    const retry = new Headers(http.calls[2].init.headers);
    expect(retry.get("Authorization")).toBe("Bearer access-2");
  });

  it("shares one in-flight refresh between concurrent 401s (single-use tokens)", async () => {
    setSession(session(1));
    let release!: () => void;
    const gate = new Promise<void>((r) => (release = r));
    const http = mockFetch({
      "GET /api/accounts": [() => problem(401, "expired"), () => json([])],
      "GET /api/positions": [() => problem(401, "expired"), () => json([])],
      "POST /api/auth/refresh": async () => {
        await gate; // hold the refresh open until both requests have hit their 401
        return json(session(2));
      },
    });

    const both = Promise.all([api.accounts(), api.positions()]);
    await new Promise((r) => setTimeout(r, 0));
    release();
    await both;
    expect(http.count("POST", "/api/auth/refresh")).toBe(1);
  });

  it("retries at most once — a second 401 ends the session", async () => {
    setSession(session(1));
    const http = mockFetch({
      "GET /api/accounts": [() => problem(401, "x"), () => problem(401, "x")],
      "POST /api/auth/refresh": () => json(session(2)),
    });
    await expect(api.accounts()).rejects.toMatchObject({
      status: 401,
      message: "Session expired — please sign in again.",
    });
    expect(http.count("POST", "/api/auth/refresh")).toBe(1);
    expect(getToken()).toBeNull();
  });

  it("clears the session when the refresh token is rejected", async () => {
    setSession(session(1));
    mockFetch({
      "GET /api/accounts": () => problem(401, "expired"),
      "POST /api/auth/refresh": () => problem(401, "replayed"),
    });
    await expect(api.accounts()).rejects.toBeInstanceOf(ApiError);
    expect(getToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it("does not refresh when there is no refresh token", async () => {
    const http = mockFetch({ "GET /api/accounts": () => problem(401, "no token") });
    await expect(api.accounts()).rejects.toMatchObject({ status: 401 });
    expect(http.count("POST", "/api/auth/refresh")).toBe(0);
  });

  it("keeps the session if the refresh fails on the network, not on the server", async () => {
    setSession(session(1));
    mockFetch({
      "GET /api/accounts": () => problem(401, "expired"),
      "POST /api/auth/refresh": () => {
        throw new TypeError("Failed to fetch");
      },
    });
    // A blip during refresh says nothing about the session: report the network, keep the
    // tokens, and let the next request try again. (Previously this signed the user out
    // with "Session expired".)
    await expect(api.accounts()).rejects.toMatchObject({
      status: 0,
      message: expect.stringContaining("Can't reach the server"),
    });
    expect(getToken()).toBe("access-1");
    expect(getRefreshToken()).toBe("refresh-1");
  });

  it("recovers on the next request once the network is back", async () => {
    setSession(session(1));
    const http = mockFetch({
      "GET /api/accounts": [
        () => problem(401, "expired"),
        () => problem(401, "expired"),
        () => json([{ id: 1 }]),
      ],
      "POST /api/auth/refresh": [
        () => {
          throw new TypeError("Failed to fetch");
        },
        () => json(session(2)),
      ],
    });
    await expect(api.accounts()).rejects.toMatchObject({ status: 0 });
    await expect(api.accounts()).resolves.toEqual([{ id: 1 }]);
    expect(http.count("POST", "/api/auth/refresh")).toBe(2);
    expect(getToken()).toBe("access-2");
  });
});

describe("auth endpoints", () => {
  it("a 401 from /auth/login is bad credentials, not an expired session", async () => {
    const http = mockFetch({
      "POST /api/auth/login": () => problem(401, "Invalid email or password"),
    });
    await expect(api.login("a@b.c", "wrongpass")).rejects.toMatchObject({
      status: 401,
      message: "Invalid email or password",
    });
    expect(http.count("POST", "/api/auth/refresh")).toBe(0);
  });

  it("surfaces the problem-detail message on other errors", async () => {
    setSession(session(1));
    mockFetch({ "GET /api/performance": () => problem(404, "Account 9 not found") });
    await expect(api.performance(9)).rejects.toMatchObject({
      status: 404,
      message: "Account 9 not found",
    });
  });

  it("falls back to the status text when the error body is not JSON", async () => {
    setSession(session(1));
    mockFetch({
      "GET /api/accounts": () => new Response("<html>", { status: 502, statusText: "Bad Gateway" }),
    });
    await expect(api.accounts()).rejects.toMatchObject({ status: 502, message: "Bad Gateway" });
  });

  it("logout clears locally first, then revokes the refresh token server-side", async () => {
    setSession(session(1));
    const http = mockFetch({
      "POST /api/auth/logout": (_url, init) => {
        expect(getToken()).toBeNull(); // already signed out locally
        expect(JSON.parse(String(init.body))).toEqual({ refreshToken: "refresh-1" });
        return new Response(null, { status: 204 });
      },
    });
    await api.logout();
    expect(http.count("POST", "/api/auth/logout")).toBe(1);
  });

  it("logout survives the server being unreachable", async () => {
    setSession(session(1));
    mockFetch({
      "POST /api/auth/logout": () => {
        throw new TypeError("Failed to fetch");
      },
    });
    await expect(api.logout()).resolves.toBeUndefined();
    expect(getToken()).toBeNull();
  });
});

describe("importFills", () => {
  const file = () => new File(["symbol,side\nAAPL,BUY\n"], "fills.csv", { type: "text/csv" });

  it("posts multipart without forcing a JSON content type", async () => {
    setSession(session(1));
    const http = mockFetch({
      "POST /api/fills/import": (_url, init) => {
        expect(init.body).toBeInstanceOf(FormData);
        const form = init.body as FormData;
        expect(form.get("accountId")).toBe("3");
        expect(new Headers(init.headers).get("Content-Type")).toBeNull();
        return json({ imported: 1, skipped: 0, failed: 0, errors: [] });
      },
    });
    await expect(api.importFills(3, file())).resolves.toMatchObject({ imported: 1 });
    expect(http.count("POST", "/api/fills/import")).toBe(1);
  });

  it("refreshes and retries the upload on 401", async () => {
    setSession(session(1));
    const http = mockFetch({
      "POST /api/fills/import": [
        () => problem(401, "expired"),
        () => json({ imported: 2, skipped: 0, failed: 0, errors: [] }),
      ],
      "POST /api/auth/refresh": () => json(session(2)),
    });
    await expect(api.importFills(3, file())).resolves.toMatchObject({ imported: 2 });
    expect(http.count("POST", "/api/auth/refresh")).toBe(1);
  });
});
