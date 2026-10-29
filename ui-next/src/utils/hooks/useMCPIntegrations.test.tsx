import { renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { FEATURES } from "utils/flags";
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

it("excludes cached providers and disables requests when the rollout is off", () => {
  const { result } = renderHook(useMCPIntegrations);
  expect(result.current.integrations).toEqual([]);
  for (const [, options] of fetchHook.mock.calls)
    expect(options.when).toBe(false);
});

it("combines only enabled and active allowlisted connections with catalog icons", () => {
  window.conductor = {
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
    [FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED]: true,
  };
  const { result } = renderHook(useMCPIntegrations);
  expect(result.current.integrations.map((item) => item.name)).toEqual([
    "lambda",
  ]);
  expect(result.current.integrations[0]).toMatchObject({
    iconName: "lambda",
    status: "active",
  });
  expect(result.current.isLoading).toBe(false);
});

it("reports loading and handles missing provider responses", () => {
  fetchHook.mockReturnValue({ data: undefined, isLoading: true });
  const { result } = renderHook(useMCPIntegrations);
  expect(result.current.integrations).toEqual([]);
  expect(result.current.isLoading).toBe(true);
});

it("does not request tools without a name or while the master is disabled", () => {
  const { result } = renderHook(() => useMCPTools());
  expect(fetchHook).toHaveBeenLastCalledWith(
    "",
    expect.objectContaining({ when: false }),
  );
  expect(result.current.isLoading).toBe(false);
  renderHook(() => useMCPTools("lambda"));
  expect(fetchHook).toHaveBeenLastCalledWith(
    "/integrations/lambda/def/apis",
    expect.objectContaining({ when: false }),
  );
  window.conductor = {
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
  };
  renderHook(() => useMCPTools("lambda"));
  expect(fetchHook).toHaveBeenLastCalledWith(
    "/integrations/lambda/def/apis",
    expect.objectContaining({ when: true }),
  );
});

it("suppresses cached tools when the current selection or rollout is unavailable", () => {
  fetchHook.mockReturnValue({ data: [{ api: "invoke" }], isLoading: false });
  const disabled = renderHook(() => useMCPTools("lambda"));
  expect(disabled.result.current.tools).toEqual([]);
  disabled.unmount();
  window.conductor = {
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
  };
  const unavailable = renderHook(() => useMCPTools());
  expect(unavailable.result.current.tools).toEqual([]);
});

it("re-evaluates every rollout flag against stable cache references on a mounted hook", () => {
  window.conductor = {
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
  };
  const { result, rerender } = renderHook(useMCPIntegrations);
  const names = () => result.current.integrations.map((item) => item.name);
  expect(names()).toEqual(["lambda"]);
  window.conductor[FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED] = false;
  window.conductor[FEATURES.SERVERLESS_AZURE_FUNCTIONS_ENABLED] = true;
  rerender();
  expect(names()).toEqual([]);
  window.conductor[FEATURES.CONNECTED_APPS_ENABLED] = false;
  rerender();
  expect(names()).toEqual([]);
  window.conductor[FEATURES.CONNECTED_APPS_ENABLED] = true;
  rerender();
  expect(names()).toEqual([]);
  window.conductor[FEATURES.SERVERLESS_AZURE_FUNCTIONS_ENABLED] = false;
  window.conductor[FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED] = true;
  rerender();
  expect(names()).toEqual([]);
  window.conductor[FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED] = false;
  window.conductor[FEATURES.SERVERLESS_GCP_FUNCTIONS_ENABLED] = true;
  rerender();
  expect(names()).toEqual([]);
  window.conductor[FEATURES.SERVERLESS_GCP_FUNCTIONS_ENABLED] = false;
  window.conductor[FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED] = true;
  rerender();
  expect(names()).toEqual(["lambda"]);
});
