import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { AgentFeedbackControls } from "./AgentFeedbackControls";

const fetchWithContext = vi.hoisted(() => vi.fn());

vi.mock("plugins/fetch", () => ({
  fetchWithContext,
  useFetchContext: () => ({ stack: "test", ready: true }),
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({ Authorization: "test" }),
}));

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

const renderControls = (
  executionStatus?: string,
  queryClient = createQueryClient(),
) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <AgentFeedbackControls
        executionId="turn-1"
        executionStatus={executionStatus}
      />
    </QueryClientProvider>,
  );
};

describe("AgentFeedbackControls", () => {
  beforeEach(() => fetchWithContext.mockReset());

  it("does not render controls when the backend disables feedback", async () => {
    fetchWithContext.mockResolvedValue({
      enabled: false,
      reason: "CHILD_EXECUTION",
    });

    renderControls();

    await waitFor(() => expect(fetchWithContext).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Helpful")).not.toBeInTheDocument();
  });

  it("reloads eligibility when an open execution becomes terminal", async () => {
    fetchWithContext
      .mockResolvedValueOnce({
        enabled: false,
        reason: "EXECUTION_NOT_TERMINAL",
      })
      .mockResolvedValueOnce({ enabled: true, rating: null });

    const queryClient = createQueryClient();
    const { rerender } = renderControls("RUNNING", queryClient);
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Helpful")).not.toBeInTheDocument();

    rerender(
      <QueryClientProvider client={queryClient}>
        <AgentFeedbackControls
          executionId="turn-1"
          executionStatus="COMPLETED"
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole("button", { name: "Helpful" }),
    ).toBeVisible();
  });

  it("requires a reason before submitting feedback", async () => {
    fetchWithContext.mockImplementation((path, _context, options) => {
      if (options?.method === "POST") {
        return Promise.resolve({ enabled: true, rating: "positive" });
      }
      if (typeof path === "string" && path.endsWith("/memory")) {
        return Promise.resolve({
          summary: "The agent verified the service health.",
        });
      }
      return Promise.resolve({ enabled: true, rating: null });
    });

    renderControls();
    const helpful = await screen.findByRole("button", { name: "Helpful" });
    fireEvent.click(helpful);

    expect(
      screen.getByRole("dialog", { name: "Share feedback" }),
    ).toBeVisible();
    const memory = await screen.findByRole("textbox", {
      name: "Execution memory",
    });
    expect(memory).toHaveValue("The agent verified the service health.");
    expect(memory).toHaveAttribute("readonly");
    const submitButton = screen.getByRole("button", {
      name: "Submit feedback",
    });
    expect(submitButton).toBeDisabled();
    fireEvent.change(screen.getByRole("textbox", { name: /Reason/ }), {
      target: { value: "  Accurate and easy to follow.  " },
    });
    expect(submitButton).toBeEnabled();
    fireEvent.click(submitButton);

    await waitFor(() =>
      expect(
        fetchWithContext.mock.calls.some(
          ([, , options]) => options?.method === "POST",
        ),
      ).toBe(true),
    );
    const submission = fetchWithContext.mock.calls.find(
      ([, , options]) => options?.method === "POST",
    );
    expect(submission?.[2]).toMatchObject({
      method: "POST",
      body: JSON.stringify({
        rating: "positive",
        reason: "Accurate and easy to follow.",
      }),
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Helpful" })).toHaveClass(
        "MuiButton-contained",
      ),
    );
  });

  it("shows a local retryable error when submission fails", async () => {
    fetchWithContext.mockImplementation((path, _context, options) => {
      if (options?.method === "POST")
        return Promise.reject(new Error("unavailable"));
      if (typeof path === "string" && path.endsWith("/memory"))
        return Promise.resolve({ summary: "Memory" });
      return Promise.resolve({ enabled: true, rating: null });
    });

    renderControls();
    fireEvent.click(await screen.findByRole("button", { name: "Not helpful" }));
    fireEvent.change(screen.getByRole("textbox", { name: /Reason/ }), {
      target: { value: "The conclusion is unsupported." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Submit feedback" }));

    expect(
      await screen.findByText("Feedback could not be saved. Please try again."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Submit feedback" }),
    ).toBeEnabled();
  });
});
