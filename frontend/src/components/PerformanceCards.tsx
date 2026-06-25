import { Performance } from "../api";

const money = (s: string | null) =>
  s == null ? "—" : Number(s).toLocaleString(undefined, { style: "currency", currency: "USD" });

function Card({ label, value }: { label: string; value: string }) {
  return (
    <div className="card stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value mono">{value}</div>
    </div>
  );
}

export default function PerformanceCards({ perf }: { perf: Performance }) {
  return (
    <div className="stats">
      <Card label="Open exposure" value={money(perf.openExposure)} />
      <Card label="Gross notional" value={money(perf.grossNotional)} />
      <Card label="Total fees" value={money(perf.totalFees)} />
      <Card label="Open positions" value={String(perf.openPositions)} />
      <Card label="Orders" value={String(perf.totalOrders)} />
      <Card label="Fills" value={String(perf.totalFills)} />
    </div>
  );
}
