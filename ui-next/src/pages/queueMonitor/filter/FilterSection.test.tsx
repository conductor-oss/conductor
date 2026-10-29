import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { RangeOptions } from "../state";
import { FilterSection } from "./FilterSection";
import { useFilterUpdate } from "./hook";

// The MUI X date picker reaches for `matchMedia` (desktop vs. mobile
// variant) which jsdom doesn't implement, and the picker's own popup
// behavior is irrelevant to what this suite checks — the compound-field
// wiring around it. A lightweight stand-in that exposes value/onChange as a
// plain input keeps the suite fast and independent of picker internals.
vi.mock("components/ui/date-time/ConductorDateTimePicker", () => ({
  default: ({
    value,
    onChange,
    onOpen,
    inputProps,
  }: {
    value: Date | null;
    onChange: (value: Date | null) => void;
    onOpen?: () => void;
    inputProps?: { onFocus?: () => void };
  }) => (
    <input
      aria-label="Date time"
      value={value ? value.toISOString() : ""}
      onChange={(e) =>
        onChange(e.target.value ? new Date(e.target.value) : null)
      }
      onFocus={inputProps?.onFocus}
      onClick={onOpen}
    />
  ),
}));

vi.mock("./hook", () => ({
  useFilterUpdate: vi.fn(),
}));

const mockedUseFilterUpdate = vi.mocked(useFilterUpdate);

describe("FilterSection", () => {
  const handleUpdateQueue = vi.fn();
  const handleUpdateWorkerCount = vi.fn();
  const handleUpdateLastPollFilter = vi.fn();
  const clearAllFields = vi.fn();

  const handlers = {
    handleUpdateQueue,
    handleUpdateWorkerCount,
    handleUpdateLastPollFilter,
    clearAllFields,
  };

  const mockFilterUpdate = ({
    state = {},
    isDisabled = true,
    appliedFilterPath = "/taskQueue",
  }: {
    state?: Record<string, unknown>;
    isDisabled?: boolean;
    appliedFilterPath?: string;
  } = {}) => {
    mockedUseFilterUpdate.mockReturnValue([
      state as never,
      handlers as never,
      isDisabled,
      appliedFilterPath,
    ]);
  };

  const setup = (
    options: {
      state?: Record<string, unknown>;
      isDisabled?: boolean;
      appliedFilterPath?: string;
    } = {},
  ) => {
    mockFilterUpdate(options);

    return render(
      <MemoryRouter>
        <FilterSection queueMachineActor={{} as never} />
      </MemoryRouter>,
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders all three filters with their value placeholders", () => {
    setup();

    expect(screen.getByText("Queue size")).toBeVisible();
    expect(screen.getByText("Worker count")).toBeVisible();
    expect(screen.getByText("Last poll time")).toBeVisible();
    expect(screen.getByPlaceholderText("tasks")).toBeVisible();
    expect(screen.getByPlaceholderText("count")).toBeVisible();
  });

  it("typing a queue size value activates the filter with the default operator", () => {
    setup();

    fireEvent.change(screen.getByPlaceholderText("tasks"), {
      target: { value: "50" },
    });

    expect(handleUpdateQueue).toHaveBeenCalledWith({
      option: RangeOptions.GT,
      size: 50,
    });
  });

  it("preserves the chosen operator when the value changes", () => {
    setup({ state: { queue: { option: RangeOptions.LT, size: 10 } } });

    fireEvent.change(screen.getByPlaceholderText("tasks"), {
      target: { value: "25" },
    });

    expect(handleUpdateQueue).toHaveBeenCalledWith({
      option: RangeOptions.LT,
      size: 25,
    });
  });

  it("clearing a value deactivates the filter entirely", () => {
    setup({ state: { worker: { option: RangeOptions.GT, size: 3 } } });

    fireEvent.change(screen.getByPlaceholderText("count"), {
      target: { value: "" },
    });

    expect(handleUpdateWorkerCount).toHaveBeenCalledWith(undefined);
  });

  it("keeps the > / < operator after queue size or worker count is cleared", () => {
    const { rerender } = setup({
      state: {
        queue: { option: RangeOptions.LT, size: 10 },
        worker: { option: RangeOptions.LT, size: 3 },
      },
    });

    fireEvent.change(screen.getByPlaceholderText("tasks"), {
      target: { value: "" },
    });
    fireEvent.change(screen.getByPlaceholderText("count"), {
      target: { value: "" },
    });

    mockFilterUpdate();
    rerender(
      <MemoryRouter>
        <FilterSection queueMachineActor={{} as never} />
      </MemoryRouter>,
    );

    expect(screen.getByLabelText("Queue size condition")).toHaveTextContent(
      "<",
    );
    expect(screen.getByLabelText("Worker count condition")).toHaveTextContent(
      "<",
    );

    fireEvent.change(screen.getByPlaceholderText("tasks"), {
      target: { value: "8" },
    });
    expect(handleUpdateQueue).toHaveBeenLastCalledWith({
      option: RangeOptions.LT,
      size: 8,
    });
  });

  it("autofills today when focusing an empty last poll time field", () => {
    const now = 1_724_694_400_000;
    vi.spyOn(Date, "now").mockReturnValue(now);
    setup();

    fireEvent.focus(screen.getByLabelText("Date time"));

    expect(handleUpdateLastPollFilter).toHaveBeenCalledWith({
      option: RangeOptions.GT,
      size: now,
    });
  });

  it("autofills today when changing Before/After on an empty last poll time", () => {
    const now = 1_724_694_400_000;
    vi.spyOn(Date, "now").mockReturnValue(now);
    setup();

    fireEvent.mouseDown(screen.getByLabelText("Last poll time condition"));
    fireEvent.click(screen.getByRole("option", { name: "Before" }));

    expect(handleUpdateLastPollFilter).toHaveBeenCalledWith({
      option: RangeOptions.LT,
      size: now,
    });
  });

  it("does not overwrite last poll time when focusing a filled field", () => {
    setup({
      state: { lastPollTime: { option: RangeOptions.GT, size: 100 } },
    });

    fireEvent.focus(screen.getByLabelText("Date time"));

    expect(handleUpdateLastPollFilter).not.toHaveBeenCalled();
  });

  it("changing the operator with no value does not invent a zero-size filter", () => {
    setup();

    fireEvent.mouseDown(screen.getByLabelText("Queue size condition"));
    fireEvent.click(screen.getByRole("option", { name: "<" }));

    expect(handleUpdateQueue).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Queue size condition")).toHaveTextContent(
      "<",
    );
  });

  it("shows the active-state dot only for filters that are set", () => {
    setup({ state: { queue: { option: RangeOptions.GT, size: 5 } } });

    expect(screen.getAllByTestId("filter-active-dot")).toHaveLength(1);
  });

  it("Reset is disabled when no filter has been applied yet", () => {
    setup({ appliedFilterPath: "/taskQueue" });
    expect(screen.getByRole("link", { name: /reset/i })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("Reset is enabled once a filter has been applied", () => {
    setup({ appliedFilterPath: "/taskQueue?queueSize=5&queueOpt=GT" });
    expect(screen.getByRole("link", { name: /reset/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("clicking Reset clears every field", () => {
    setup({ appliedFilterPath: "/taskQueue?queueSize=5&queueOpt=GT" });

    fireEvent.click(screen.getByRole("link", { name: /reset/i }));

    expect(clearAllFields).toHaveBeenCalledTimes(1);
  });

  it("Apply filter is disabled while the hook reports isDisabled", () => {
    setup({ isDisabled: true });
    expect(screen.getByRole("link", { name: /apply filter/i })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("Apply filter is enabled once the hook reports isDisabled: false", () => {
    setup({ isDisabled: false });
    expect(
      screen.getByRole("link", { name: /apply filter/i }),
    ).not.toHaveAttribute("aria-disabled", "true");
  });
});
