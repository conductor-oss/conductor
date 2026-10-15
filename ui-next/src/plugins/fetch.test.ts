import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { agentFetch } from "utils/agentFetch";
import { fetchWithContext, ORG_ID_HEADER_NAME } from "./fetch";
import { pluginRegistry } from "./registry";

const fetchMock = vi.fn();

function sentHeaders(): Headers {
  return new Headers(fetchMock.mock.calls[0][1].headers);
}

beforeEach(() => {
  fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

describe("fetchWithContext", () => {
  it("sends X-Org-Id from the registered org id", async () => {
    vi.spyOn(pluginRegistry, "getOrgId").mockReturnValue("ab12");

    await fetchWithContext(
      "/workflow/x",
      {},
      {
        headers: { "X-Authorization": "token" },
      },
    );

    expect(sentHeaders().get(ORG_ID_HEADER_NAME)).toBe("ab12");
    expect(sentHeaders().get("X-Authorization")).toBe("token");
  });

  it("sends no X-Org-Id and leaves the headers untouched when none is set", async () => {
    const headers = { "X-Authorization": "token" };

    await fetchWithContext("/workflow/x", {}, { headers });

    expect(fetchMock.mock.calls[0][1].headers).toEqual(headers);
    expect(sentHeaders().has(ORG_ID_HEADER_NAME)).toBe(false);
  });

  it("keeps an X-Org-Id the caller set explicitly", async () => {
    vi.spyOn(pluginRegistry, "getOrgId").mockReturnValue("ab12");

    await fetchWithContext(
      "/workflow/x",
      {},
      {
        headers: { [ORG_ID_HEADER_NAME]: "0000" },
      },
    );

    expect(sentHeaders().get(ORG_ID_HEADER_NAME)).toBe("0000");
  });
});

describe("agentFetch", () => {
  it("sends X-Org-Id from the registered org id", async () => {
    vi.spyOn(pluginRegistry, "getOrgId").mockReturnValue("ab12");

    await agentFetch("/api/agent/x");

    expect(sentHeaders().get(ORG_ID_HEADER_NAME)).toBe("ab12");
  });

  it("sends no X-Org-Id when none is set", async () => {
    await agentFetch("/api/agent/x");

    expect(sentHeaders().has(ORG_ID_HEADER_NAME)).toBe(false);
  });
});
