import { useRef, useState } from "react";
import { api, ApiError, ImportResult } from "../api";

export default function ImportCsv({
  accountId,
  onImported,
}: {
  accountId: number;
  onImported: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onFile(file: File) {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await api.importFills(accountId, file);
      setResult(res);
      if (res.imported > 0) onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import failed.");
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  return (
    <div className="import">
      <div className="import-row">
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          disabled={busy}
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) onFile(f);
          }}
        />
        <span className="muted small">
          CSV header: symbol,exchange,assetClass,side,type,quantity,price,fee,filledAt
        </span>
      </div>

      {busy && <div className="muted">Importing…</div>}
      {error && <div className="error">{error}</div>}

      {result && (
        <div className="import-result">
          <span className="pos">{result.imported} imported</span>
          {result.failed > 0 && <span className="neg"> · {result.failed} failed</span>}
          {result.errors.length > 0 && (
            <ul className="import-errors">
              {result.errors.map((e) => (
                <li key={e.line}>
                  <span className="mono">line {e.line}</span>: {e.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
