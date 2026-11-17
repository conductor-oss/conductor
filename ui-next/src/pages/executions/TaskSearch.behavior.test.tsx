import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter } from "react-router-dom";
import { Provider as ThemeProvider } from "theme/material/provider";
import { TaskSearch } from "./TaskSearch";

const requestedUrls = vi.hoisted(() => {
  // Task search reads its workflow field when the module loads.
  const { readFileSync } = process.getBuiltinModule("node:fs");
  const { runInNewContext } = process.getBuiltinModule("node:vm");
  const source = readFileSync("public/context.js", "utf8");
  const sandbox = { window: {} as Window & typeof globalThis };
  runInNewContext(source, sandbox);
  window.conductor = sandbox.window.conductor;
  return [] as string[];
});

vi.mock("@monaco-editor/react", () => ({
  default: () => null,
  Editor: () => null,
}));

// The page imports react-router while the test router comes from
// react-router-dom. Vitest otherwise loads two copies, and useNavigate
// cannot see the router wrapping the page.
vi.mock("react-router", async () => import("react-router-dom"));

const renderTaskSearch = () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/taskExecs"]}>
        <ThemeProvider>
          <TaskSearch />
        </ThemeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const decodedRequests = () =>
  requestedUrls.map((url) => decodeURIComponent(url));

describe("OSS task search", () => {
  beforeEach(() => {
    requestedUrls.length = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        requestedUrls.push(String(url));
        return {
          ok: true,
          status: 200,
          text: async () => JSON.stringify({ results: [], totalHits: 0 }),
        };
      }),
    );
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }),
    });
  });

  it("searches workflowType and does not offer a task reference name", async () => {
    renderTaskSearch();

    expect(
      screen.queryByRole("textbox", { name: "Task reference name" }),
    ).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Workflow name" }), {
      target: { value: "checkout_flow" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(
        decodedRequests().some((url) =>
          url.includes("workflowType='checkout_flow'"),
        ),
      ).toBe(true);
    });
    expect(
      decodedRequests().some((url) =>
        url.includes("workflowName='checkout_flow'"),
      ),
    ).toBe(false);
    expect(
      decodedRequests().some((url) => url.includes("referenceTaskName=")),
    ).toBe(false);
  });
});
