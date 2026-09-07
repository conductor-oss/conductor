import "@testing-library/jest-dom";
import { vi } from "vitest";

// Newer Node versions ship an experimental global `localStorage` that is
// only functional when the process is started with `--localstorage-file`.
// Without that flag `localStorage` resolves to an object whose methods throw
// (or, on some versions, to `undefined`), which breaks any module that reads
// localStorage at import time (e.g. utils/flags.ts). jsdom's own
// implementation isn't reliably installed ahead of that broken global in
// every Node/vitest version combo, so provide a plain in-memory stand-in.
if (
  typeof localStorage === "undefined" ||
  typeof localStorage.getItem !== "function"
) {
  const store = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    setItem: (key: string, value: string) => store.set(key, String(value)),
    removeItem: (key: string) => store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  });
}

// Monaco Editor calls document.queryCommandSupported during module init,
// which jsdom does not implement. Stub it out globally.
Object.defineProperty(document, "queryCommandSupported", {
  value: vi.fn(() => false),
  writable: true,
});

// Monaco Editor does not run in jsdom. Mock the package so tests that render
// components containing editors get a lightweight no-op instead.
vi.mock("@monaco-editor/react", () => ({
  default: vi.fn(() => null),
  Editor: vi.fn(() => null),
  DiffEditor: vi.fn(() => null),
  useMonaco: vi.fn(() => null),
  loader: { config: vi.fn() },
}));
