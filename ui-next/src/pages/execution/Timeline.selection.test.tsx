import { fireEvent, render, screen } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { forwardRef, useImperativeHandle, useRef } from "react";
import { describe, expect, it, vi } from "vitest";
import { getTheme } from "theme/theme";
import { ExecutionTask } from "types";
import Timeline from "./Timeline";

vi.mock("react-vis-timeline", () => ({
  default: forwardRef(function TimelineLeaf(
    { clickHandler }: { clickHandler: (event: object) => void },
    ref,
  ) {
    // Installed react-vis-timeline binds event handlers only on mount.
    const mountedClickHandler = useRef(clickHandler);
    useImperativeHandle(ref, () => ({
      timeline: { on() {}, off() {}, setItems() {}, setGroups() {} },
    }));
    return (
      <>
        <button
          onClick={() =>
            mountedClickHandler.current({
              group: "same_ref",
              item: "retry",
              what: "item",
            })
          }
        >
          Runtime range
        </button>
        <button
          onClick={() =>
            mountedClickHandler.current({
              group: "same_ref",
              item: "retry_scheduled",
              what: "item",
            })
          }
        >
          Queue range
        </button>
        <button
          onClick={() =>
            mountedClickHandler.current({
              group: "same_ref",
              what: "group-label",
            })
          }
        >
          Reference label
        </button>
        <button
          onClick={() =>
            mountedClickHandler.current({
              group: "same_ref",
              item: "retry",
              what: "background",
            })
          }
        >
          Background
        </button>
      </>
    );
  }),
}));
const tasks = [
  {
    taskId: "retry",
    referenceTaskName: "same_ref",
    status: "FAILED",
    startTime: 1000,
    endTime: 2000,
    scheduledTime: 500,
    workflowTask: {
      name: "Invoke Function",
      taskReferenceName: "same_ref",
      type: "CONNECTED_APP",
    },
  },
] as ExecutionTask[];
describe("timeline range selection", () => {
  it("uses the current selection callback when returning to the attempt selected at mount", () => {
    const initialSelection = vi.fn();
    const currentSelection = vi.fn();
    const { rerender } = render(
      <ThemeProvider theme={getTheme()}>
        <Timeline tasks={tasks} onClick={initialSelection} />
      </ThemeProvider>,
    );
    rerender(
      <ThemeProvider theme={getTheme()}>
        <Timeline tasks={tasks} onClick={currentSelection} />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByText("Runtime range"));
    expect(currentSelection).toHaveBeenCalledWith({
      ref: "same_ref",
      taskId: "retry",
    });
    expect(initialSelection).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("Queue range"));
    expect(currentSelection).toHaveBeenCalledTimes(2);
  });

  it.each(["Runtime range", "Queue range"])(
    "maps %s to the exact runtime attempt",
    (label) => {
      const onClick = vi.fn();
      render(
        <ThemeProvider theme={getTheme()}>
          <Timeline tasks={tasks} onClick={onClick} />
        </ThemeProvider>,
      );
      fireEvent.click(screen.getByText(label));
      expect(onClick).toHaveBeenCalledWith({
        ref: "same_ref",
        taskId: "retry",
      });
    },
  );
  it("leaves reference-only selection distinct from background clicks", () => {
    const onClick = vi.fn();
    render(
      <ThemeProvider theme={getTheme()}>
        <Timeline tasks={tasks} onClick={onClick} />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByText("Background"));
    expect(onClick).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("Reference label"));
    expect(onClick).toHaveBeenCalledWith({
      ref: "same_ref",
      taskId: undefined,
    });
  });
});
