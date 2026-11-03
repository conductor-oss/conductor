import { renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { useMCPIntegrations, useMCPTools } from "./useMCPIntegrations";

const { fetchHook, definitions, providers } = vi.hoisted(() => ({
  fetchHook: vi.fn(),
  definitions: [
    { type: "aws-lambda", category: "MCP", iconName: "lambda" },
    { type: "azure-functions", category: "MCP" },
    { type: "google-cloud-functions", category: "MCP" },
    { type: "firebase-functions", category: "MCP" },
    { type: "firebase-firestore-v5", category: "MCP" },
  ],
  providers: [
    { type: "aws-lambda", category: "MCP", name: "lambda", enabled: true },
    { type: "aws-lambda", category: "MCP", name: "inactive", enabled: false },
    { type: "azure-functions", category: "MCP", name: "azure", enabled: true },
    {
      type: "google-cloud-functions",
      category: "MCP",
      name: "gcp-functions",
      enabled: true,
    },
    { type: "slack", category: "MCP", name: "slack", enabled: true },
    { type: "postgres", category: "MCP", name: "postgres", enabled: true },
    {
      type: "firebase-functions",
      category: "MCP",
      name: "firebase",
      enabled: true,
    },
    {
      type: "firebase-firestore-v5",
      category: "MCP",
      name: "firestore",
      enabled: true,
    },
    { type: "aws", category: "CLOUD", name: "aws", enabled: true },
  ],
}));
vi.mock("utils/query", () => ({ useFetch: fetchHook }));

beforeEach(() => {
  window.conductor = {};
  fetchHook.mockReset().mockImplementation((url) => ({
    // Deliberately return cached data even when the query is disabled.
    data: url === "/integrations/def" ? definitions : providers,
    isLoading: false,
  }));
});

it("uses all advertised MCP types, but only configured enabled connections", () => {
  const { result } = renderHook(useMCPIntegrations);
  expect(result.current.integrations.map((item) => item.name)).toEqual([
    "lambda",
    "azure",
    "gcp-functions",
    "firebase",
    "firestore",
  ]);
  expect(result.current.integrations[0]).toMatchObject({
    iconName: "lambda",
    status: "active",
  });
  expect(
    result.current.integrations.some((item) => item.name === "slack"),
  ).toBe(false);
});
it("handles absent catalog without admitting orphaned configured providers", () => {
  fetchHook.mockImplementation((url) => ({
    data: url === "/integrations/def" ? undefined : providers,
    isLoading: false,
  }));
  expect(renderHook(useMCPIntegrations).result.current.integrations).toEqual(
    [],
  );
});

it("reports loading with missing responses", () => {
  fetchHook.mockReturnValue({ data: undefined, isLoading: true });
  const { result } = renderHook(useMCPIntegrations);
  expect(result.current.integrations).toEqual([]);
  expect(result.current.isLoading).toBe(true);
});
it("suppresses tool fetches and cached tools without a selection", () => {
  fetchHook.mockReturnValue({ data: [{ api: "invoke" }], isLoading: false });
  expect(renderHook(() => useMCPTools()).result.current.tools).toEqual([]);
  expect(renderHook(() => useMCPTools("azure")).result.current.tools).toEqual([
    { api: "invoke" },
  ]);
});

it("accepts every advertised migrated provider without frontend allowlists", () => {
  const entries = [
    "aws-lambda",
    "aws-s3",
    "azure-functions",
    "azure-storage",
    "commonroom",
    "discourse",
    "github",
    "google-calendar",
    "google-cloud-functions",
    "google-cloud-storage",
    "google-docs",
    "google-drive",
    "google-sheets",
    "google-slides",
    "hubspot",
    "jira",
    "mysql",
    "notion",
    "postgres",
    "redis",
    "slack",
    "stripe-v2",
    "wordpress-com",
  ].map((type) => ({ type, name: type, category: "MCP", enabled: true }));
  fetchHook.mockReturnValue({ data: entries, isLoading: false });
  expect(
    renderHook(useMCPIntegrations).result.current.integrations.map(
      (item) => item.type,
    ),
  ).toEqual(entries.map((item) => item.type));
});

it("returns empty tools when an enabled selection has no response yet", () => {
  fetchHook.mockReturnValue({ data: undefined, isLoading: true });
  const { result } = renderHook(() => useMCPTools("azure"));
  expect(result.current.tools).toEqual([]);
  expect(result.current.isLoading).toBe(true);
});
