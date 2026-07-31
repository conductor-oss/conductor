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

const renderControls = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AgentFeedbackControls executionId="turn-1" />
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

  it("submits and displays the canonical selected rating", async () => {
    fetchWithContext
      .mockResolvedValueOnce({ enabled: true, rating: null })
      .mockResolvedValueOnce({ enabled: true, rating: "positive" });

    renderControls();
    const helpful = await screen.findByRole("button", { name: "Helpful" });
    fireEvent.click(helpful);

    await waitFor(() => expect(fetchWithContext).toHaveBeenCalledTimes(2));
    expect(fetchWithContext.mock.calls[1][2]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ rating: "positive" }),
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Helpful" })).toHaveClass(
        "MuiButton-contained",
      ),
    );
  });

  it("shows a local retryable error when submission fails", async () => {
    fetchWithContext
      .mockResolvedValueOnce({ enabled: true, rating: null })
      .mockRejectedValueOnce(new Error("unavailable"));

    renderControls();
    fireEvent.click(await screen.findByRole("button", { name: "Not helpful" }));

    expect(
      await screen.findByText("Feedback could not be saved. Please try again."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Helpful" })).toBeEnabled();
  });
});
