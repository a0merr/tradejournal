import { useEffect, useState } from "react";
import { Account, api, ApiError, Performance, Position } from "../api";
import PositionsTable from "./PositionsTable";
import PerformanceCards from "./PerformanceCards";
import ExposureChart from "./ExposureChart";
import ImportCsv from "./ImportCsv";

export default function Dashboard({ onLogout }: { onLogout: () => void }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [positions, setPositions] = useState<Position[]>([]);
  const [perf, setPerf] = useState<Performance | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadTick, setReloadTick] = useState(0);

  // Load accounts once; pick the first.
  useEffect(() => {
    api
      .accounts()
      .then((accs) => {
        setAccounts(accs);
        if (accs.length > 0) setAccountId(accs[0].id);
        else setLoading(false);
      })
      .catch((err) => handle(err));
  }, []);

  // Reload positions + performance whenever the selected account changes.
  useEffect(() => {
    if (accountId == null) return;
    setLoading(true);
    Promise.all([api.positions(), api.performance(accountId)])
      .then(([pos, p]) => {
        setPositions(pos.filter((x) => x.accountId === accountId));
        setPerf(p);
        setError(null);
      })
      .catch((err) => handle(err))
      .finally(() => setLoading(false));
  }, [accountId, reloadTick]);

  function handle(err: unknown) {
    if (err instanceof ApiError && err.status === 401) {
      onLogout();
      return;
    }
    setError(err instanceof ApiError ? err.message : "Failed to load data.");
    setLoading(false);
  }

  return (
    <div className="dashboard">
      <header className="topbar">
        <span className="brand">tradejournal</span>
        <div className="topbar-right">
          {accounts.length > 0 && (
            <select
              aria-label="Select account"
              title="Select account"
              value={accountId ?? ""}
              onChange={(e) => setAccountId(Number(e.target.value))}
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.broker} · {a.baseCurrency} (#{a.id})
                </option>
              ))}
            </select>
          )}
          <button type="button" className="link" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>

      {error && <div className="error banner">{error}</div>}
      {loading && <div className="muted">Loading…</div>}

      {!loading && perf && (
        <>
          <PerformanceCards perf={perf} />

          <section className="card">
            <h2>Exposure by instrument</h2>
            <ExposureChart positions={positions} />
          </section>

          <section className="card">
            <h2>Positions</h2>
            <PositionsTable positions={positions} />
          </section>

          {accountId != null && (
            <section className="card">
              <h2>Import fills (CSV)</h2>
              <ImportCsv
                accountId={accountId}
                onImported={() => setReloadTick((t) => t + 1)}
              />
            </section>
          )}
        </>
      )}
    </div>
  );
}
