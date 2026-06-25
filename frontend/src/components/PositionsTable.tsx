import { Position } from "../api";

const num = (s: string, dp = 4) =>
  Number(s).toLocaleString(undefined, { maximumFractionDigits: dp });

export default function PositionsTable({ positions }: { positions: Position[] }) {
  if (positions.length === 0) {
    return <p className="muted">No positions yet — ingest some fills via POST /api/fills.</p>;
  }
  return (
    <table className="grid">
      <thead>
        <tr>
          <th>Instrument</th>
          <th>Exchange</th>
          <th className="r">Net qty</th>
          <th className="r">Avg price</th>
          <th className="r">Fees</th>
          <th className="r">Fills</th>
        </tr>
      </thead>
      <tbody>
        {positions.map((p) => {
          const net = Number(p.netQuantity);
          return (
            <tr key={`${p.accountId}-${p.instrumentId}`}>
              <td className="mono">{p.symbol}</td>
              <td className="muted">{p.exchange}</td>
              <td className={`r mono ${net >= 0 ? "pos" : "neg"}`}>{num(p.netQuantity)}</td>
              <td className="r mono">{num(p.avgPrice, 2)}</td>
              <td className="r mono muted">{num(p.totalFees, 2)}</td>
              <td className="r mono">{p.fillCount}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
