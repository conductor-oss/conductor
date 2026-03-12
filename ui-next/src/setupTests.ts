import "@testing-library/jest-dom";
import { vi } from "vitest";

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
