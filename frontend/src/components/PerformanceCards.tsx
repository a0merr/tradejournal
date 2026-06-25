import { Performance } from "../api";

const money = (s: string | null) =>
  s == null ? "—" : Number(s).toLocaleString(undefined, { style: "currency", currency: "USD" });

const pct = (s: string | null) =>
  s == null ? "—" : `${(Number(s) * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

function Card({ label, value, tone }: { label: string; value: string; tone?: "pos" | "neg" }) {
  return (
    <div className="card stat">
      <div className="stat-label">{label}</div>
      <div className={`stat-value mono ${tone ?? ""}`}>{value}</div>
    </div>
  );
}

export default function PerformanceCards({ perf }: { perf: Performance }) {
  const pnl = perf.realizedPnl == null ? null : Number(perf.realizedPnl);
  const pnlTone = pnl == null ? undefined : pnl >= 0 ? "pos" : "neg";
  return (
    <div className="stats">
      <Card label="Realized PnL" value={money(perf.realizedPnl)} tone={pnlTone} />
      <Card label="Win rate" value={pct(perf.winRate)} />
      <Card label="Closed trades" value={String(perf.closedTrades)} />
      <Card label="Open exposure" value={money(perf.openExposure)} />
      <Card label="Gross notional" value={money(perf.grossNotional)} />
      <Card label="Total fees" value={money(perf.totalFees)} />
      <Card label="Open positions" value={String(perf.openPositions)} />
      <Card label="Orders" value={String(perf.totalOrders)} />
      <Card label="Fills" value={String(perf.totalFills)} />
    </div>
  );
}
