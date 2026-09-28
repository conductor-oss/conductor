import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useNavigate } from "react-router";
import { FunctionComponent } from "react";
import { QueueMachineEventTypes } from "./types";
import { useQueueMachine } from "./hook";

const { send } = vi.hoisted(() => ({ send: vi.fn() }));

vi.mock("@xstate/react", () => ({
  useMachine: () => [undefined, send, {}],
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({}),
}));

const Harness: FunctionComponent<{ to: string }> = ({ to }) => {
  useQueueMachine();
  const navigate = useNavigate();
  return <button onClick={() => navigate(to)}>navigate</button>;
};

const renderHarness = (from: string, to: string) =>
  render(
    <MemoryRouter initialEntries={[from]}>
      <Harness to={to} />
    </MemoryRouter>,
  );

describe("useQueueMachine", () => {
  beforeEach(() => {
    send.mockClear();
  });

  it("fetches once with the filter options parsed from the URL", () => {
    renderHarness("/taskQueue?queueSize=5&queueOpt=GT", "/taskQueue");

    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith({
      type: QueueMachineEventTypes.FETCH_TASKS_QUEUE,
      queue: { size: "5", option: "GT" },
      worker: undefined,
      lastPollTime: undefined,
    });
  });

  it("does not refetch when only the quick search term changes", () => {
    renderHarness(
      "/taskQueue?queueSize=5&queueOpt=GT",
      "/taskQueue?queueSize=5&queueOpt=GT&search=email",
    );

    fireEvent.click(screen.getByRole("button", { name: "navigate" }));

    expect(send).toHaveBeenCalledTimes(1);
  });

  it("does not refetch when only the page changes", () => {
    renderHarness("/taskQueue", "/taskQueue?page=3");

    fireEvent.click(screen.getByRole("button", { name: "navigate" }));

    expect(send).toHaveBeenCalledTimes(1);
  });

  it("refetches when a filter value changes", () => {
    renderHarness(
      "/taskQueue?queueSize=5&queueOpt=GT",
      "/taskQueue?queueSize=25&queueOpt=GT",
    );

    fireEvent.click(screen.getByRole("button", { name: "navigate" }));

    expect(send).toHaveBeenCalledTimes(2);
    expect(send).toHaveBeenLastCalledWith({
      type: QueueMachineEventTypes.FETCH_TASKS_QUEUE,
      queue: { size: "25", option: "GT" },
      worker: undefined,
      lastPollTime: undefined,
    });
  });
});
