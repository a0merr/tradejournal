import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Position } from "../api";

// Absolute notional exposure per instrument, derived from the positions view.
export default function ExposureChart({ positions }: { positions: Position[] }) {
  const data = positions
    .map((p) => ({
      symbol: p.symbol,
      exposure: Math.abs(Number(p.netQuantity) * Number(p.avgPrice)),
    }))
    .filter((d) => d.exposure > 0)
    .sort((a, b) => b.exposure - a.exposure);

  if (data.length === 0) {
    return <p className="muted">No open exposure to chart.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2733" />
        <XAxis dataKey="symbol" stroke="#8b97a7" fontSize={12} />
        <YAxis stroke="#8b97a7" fontSize={12} />
        <Tooltip
          contentStyle={{ background: "#11161d", border: "1px solid #1f2733" }}
          formatter={(v: number) =>
            v.toLocaleString(undefined, { style: "currency", currency: "USD" })
          }
        />
        <Bar dataKey="exposure" fill="#4f9cff" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
