import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Node 22+ ships an experimental global `localStorage` that shadows jsdom's and is
// unusable without --localstorage-file. Install an in-memory Storage when that happens.
if (typeof globalThis.localStorage?.setItem !== "function") {
  const store = new Map<string, string>();
  const memory: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (k) => store.get(k) ?? null,
    key: (i) => [...store.keys()][i] ?? null,
    removeItem: (k) => void store.delete(k),
    setItem: (k, v) => void store.set(k, String(v)),
  };
  Object.defineProperty(globalThis, "localStorage", { value: memory, configurable: true });
}

afterEach(() => {
  cleanup();
  localStorage.clear();
});
