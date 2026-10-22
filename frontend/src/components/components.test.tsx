import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import App from "../App";
import { getToken, Performance, Position, setSession } from "../api";
import { json, mockFetch, problem, session } from "../test/http";
import ImportCsv from "./ImportCsv";
import Login from "./Login";
import PerformanceCards from "./PerformanceCards";
import PositionsTable from "./PositionsTable";

// Recharts needs layout (ResizeObserver, real sizes) that jsdom does not have; the chart
// is presentation only, so the dashboard tests swap it for a stub.
vi.mock("./ExposureChart", () => ({ default: () => <div data-testid="exposure-chart" /> }));

const position = (over: Partial<Position> = {}): Position => ({
  accountId: 1,
  instrumentId: 10,
  symbol: "AAPL",
  exchange: "NASDAQ",
  netQuantity: "100",
  avgEntryPrice: "187.25",
  avgFillPrice: "187.25",
  totalFees: "1.00",
  fillCount: 2,
  lastFilledAt: "2026-01-02T15:00:00Z",
  ...over,
});

const perf = (over: Partial<Performance> = {}): Performance => ({
  accountId: 1,
  totalOrders: 4,
  totalFills: 5,
  openPositions: 1,
  closedTrades: 3,
  totalFees: "4.50",
  grossNotional: "50000",
  openExposure: "18725",
  realizedPnl: "1234.5",
  winRate: "0.6667",
  ...over,
});

describe("Login", () => {
  it("stores the session and reports success", async () => {
    mockFetch({ "POST /api/auth/login": () => json(session(1)) });
    const onAuthed = vi.fn();
    render(<Login onAuthed={onAuthed} />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(onAuthed).toHaveBeenCalledOnce());
    expect(getToken()).toBe("access-1");
  });

  it("shows the server's reason on bad credentials and stays signed out", async () => {
    mockFetch({ "POST /api/auth/login": () => problem(401, "Invalid email or password") });
    const onAuthed = vi.fn();
    render(<Login onAuthed={onAuthed} />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
    expect(onAuthed).not.toHaveBeenCalled();
    expect(getToken()).toBeNull();
  });

  it("switches to registration and posts to the register endpoint", async () => {
    const http = mockFetch({ "POST /api/auth/register": () => json(session(1)) });
    render(<Login onAuthed={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: /Need an account/ }));
    const email = screen.getByLabelText("Email");
    await userEvent.clear(email);
    await userEvent.type(email, "new@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Register" }));
    await waitFor(() => expect(http.count("POST", "/api/auth/register")).toBe(1));
    expect(JSON.parse(String(http.calls[0].init.body)).email).toBe("new@example.com");
  });
});

describe("Dashboard (via App)", () => {
  function signedIn() {
    setSession(session(1));
  }

  it("loads the first account and shows only its positions", async () => {
    signedIn();
    mockFetch({
      "GET /api/accounts": () =>
        json([
          { id: 1, broker: "IBKR", baseCurrency: "USD", createdAt: "" },
          { id: 2, broker: "Alpaca", baseCurrency: "USD", createdAt: "" },
        ]),
      "GET /api/positions": () =>
        json([position(), position({ accountId: 2, instrumentId: 11, symbol: "TSLA" })]),
      "GET /api/performance": () => json(perf()),
    });
    render(<App />);
    const table = await screen.findByRole("table");
    expect(within(table).getByText("AAPL")).toBeInTheDocument();
    expect(within(table).queryByText("TSLA")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Select account")).toHaveValue("1");
  });

  it("switching account reloads that account's data", async () => {
    signedIn();
    const http = mockFetch({
      "GET /api/accounts": () =>
        json([
          { id: 1, broker: "IBKR", baseCurrency: "USD", createdAt: "" },
          { id: 2, broker: "Alpaca", baseCurrency: "USD", createdAt: "" },
        ]),
      "GET /api/positions": () =>
        json([position(), position({ accountId: 2, instrumentId: 11, symbol: "TSLA" })]),
      "GET /api/performance": (url) => json(perf({ accountId: Number(new URL(url, "http://x").searchParams.get("accountId")) })),
    });
    render(<App />);
    await screen.findByText("AAPL");
    await userEvent.selectOptions(screen.getByLabelText("Select account"), "2");
    expect(await screen.findByText("TSLA")).toBeInTheDocument();
    expect(screen.queryByText("AAPL")).not.toBeInTheDocument();
    const perfCalls = http.calls.filter((c) => c.path === "/api/performance");
    expect(perfCalls).toHaveLength(2);
  });

  it("an expired session that cannot refresh returns to the login screen", async () => {
    signedIn();
    mockFetch({
      "GET /api/accounts": () => problem(401, "expired"),
      "POST /api/auth/refresh": () => problem(401, "revoked"),
      "POST /api/auth/logout": () => new Response(null, { status: 204 }),
    });
    render(<App />);
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(getToken()).toBeNull();
  });

  it("a server error shows a banner instead of signing out", async () => {
    signedIn();
    mockFetch({
      "GET /api/accounts": () => json([{ id: 1, broker: "IBKR", baseCurrency: "USD", createdAt: "" }]),
      "GET /api/positions": () => json([]),
      "GET /api/performance": () => problem(500, "Replay failed"),
    });
    render(<App />);
    expect(await screen.findByText("Replay failed")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign out" })).toBeInTheDocument();
  });

  it("sign out clears the session and revokes the refresh token", async () => {
    signedIn();
    const http = mockFetch({
      "GET /api/accounts": () => json([]),
      "POST /api/auth/logout": () => new Response(null, { status: 204 }),
    });
    render(<App />);
    await userEvent.click(await screen.findByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
    await waitFor(() => expect(http.count("POST", "/api/auth/logout")).toBe(1));
    expect(getToken()).toBeNull();
  });
});

describe("ImportCsv", () => {
  const csv = () => new File(["symbol\n"], "fills.csv", { type: "text/csv" });

  function upload() {
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    return userEvent.upload(input, csv());
  }

  it("reports imported, skipped and per-line failures, and asks for a reload", async () => {
    setSession(session(1));
    mockFetch({
      "POST /api/fills/import": () =>
        json({
          imported: 3,
          skipped: 2,
          failed: 1,
          errors: [{ line: 7, message: "price must be positive" }],
        }),
    });
    const onImported = vi.fn();
    render(<ImportCsv accountId={1} onImported={onImported} />);
    await upload();
    expect(await screen.findByText("3 imported")).toBeInTheDocument();
    expect(screen.getByText(/2 already on file/)).toBeInTheDocument();
    expect(screen.getByText(/1 failed/)).toBeInTheDocument();
    expect(screen.getByText(/price must be positive/)).toBeInTheDocument();
    expect(onImported).toHaveBeenCalledOnce();
  });

  it("a pure re-upload (all skipped) does not trigger a reload", async () => {
    setSession(session(1));
    mockFetch({
      "POST /api/fills/import": () => json({ imported: 0, skipped: 5, failed: 0, errors: [] }),
    });
    const onImported = vi.fn();
    render(<ImportCsv accountId={1} onImported={onImported} />);
    await upload();
    expect(await screen.findByText("0 imported")).toBeInTheDocument();
    expect(onImported).not.toHaveBeenCalled();
  });

  it("shows the error and clears the input so the same file can be retried", async () => {
    setSession(session(1));
    mockFetch({ "POST /api/fills/import": () => problem(413, "File too large") });
    render(<ImportCsv accountId={1} onImported={vi.fn()} />);
    await upload();
    expect(await screen.findByText("File too large")).toBeInTheDocument();
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    expect(input.value).toBe("");
  });
});

describe("PerformanceCards", () => {
  it("formats money and win rate, and colors PnL by sign", () => {
    const { rerender } = render(<PerformanceCards perf={perf()} />);
    expect(screen.getByText("66.7%")).toBeInTheDocument();
    expect(screen.getByText("$1,234.50")).toHaveClass("pos");
    rerender(<PerformanceCards perf={perf({ realizedPnl: "-50" })} />);
    expect(screen.getByText("-$50.00")).toHaveClass("neg");
  });

  it("shows a dash, not $0, when there is nothing closed yet", () => {
    render(<PerformanceCards perf={perf({ realizedPnl: null, winRate: null })} />);
    expect(screen.getAllByText("—")).toHaveLength(2);
    expect(screen.queryByText("$0.00")).not.toBeInTheDocument();
  });
});

describe("PositionsTable", () => {
  it("has an empty state", () => {
    render(<PositionsTable positions={[]} />);
    expect(screen.getByText(/No positions yet/)).toBeInTheDocument();
  });

  it("marks shorts and shows a dash for a flat position's entry price", () => {
    render(
      <PositionsTable
        positions={[
          position({ netQuantity: "-25", symbol: "TSLA" }),
          position({ instrumentId: 12, netQuantity: "0", avgEntryPrice: null, symbol: "MSFT" }),
        ]}
      />,
    );
    const rows = screen.getAllByRole("row");
    expect(within(rows[1]).getByText("-25")).toHaveClass("neg");
    expect(within(rows[2]).getByText("—")).toBeInTheDocument();
  });
});
