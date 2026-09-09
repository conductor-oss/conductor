import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TaskStatus } from "types";
import SideTaskCards from "./SideTaskCards";

/**
 * The browser check for this lives behind an OSS release, so the interaction is pinned here: a
 * collapsed stack that says how many there are, an expanded list that names them, and a click that
 * asks for the task to be opened in the right panel.
 */
describe("SideTaskCards", () => {
  const sideTask = (name: string, status: TaskStatus, id: string) =>
    ({
      taskId: id,
      referenceTaskName: `_guardrail_${name}_0`,
      status,
      workflowTask: {
        name,
        taskReferenceName: `_guardrail_${name}_0`,
        type: "SIMPLE",
      },
    }) as any;

  const three = [
    sideTask("pii", TaskStatus.COMPLETED, "t1"),
    sideTask("toxicity", TaskStatus.COMPLETED, "t2"),
    sideTask("pci_dss", TaskStatus.IN_PROGRESS, "t3"),
  ];

  it("renders nothing when the task has none", () => {
    const { container } = render(<SideTaskCards sideTasks={[]} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("collapses to a count of how many ran", () => {
    render(<SideTaskCards sideTasks={three} />);

    expect(screen.getByText("3 side tasks")).toBeTruthy();
    expect(screen.queryByText("toxicity")).toBeNull();
  });

  it("names the single task rather than counting it", () => {
    render(<SideTaskCards sideTasks={[three[0]]} />);

    expect(screen.getByText("pii")).toBeTruthy();
  });

  it("expands to one card per task on click, and collapses again", () => {
    render(<SideTaskCards sideTasks={three} />);

    fireEvent.click(screen.getByText("3 side tasks"));

    expect(screen.getByText("pii")).toBeTruthy();
    expect(screen.getByText("toxicity")).toBeTruthy();
    expect(screen.getByText("pci_dss")).toBeTruthy();
  });

  it("asks for a task to be opened when its card is clicked", () => {
    const onSelectTask = vi.fn();
    render(<SideTaskCards sideTasks={three} onSelectTask={onSelectTask} />);

    fireEvent.click(screen.getByText("3 side tasks"));
    fireEvent.click(screen.getByText("toxicity"));

    expect(onSelectTask).toHaveBeenCalledTimes(1);
    expect(onSelectTask.mock.calls[0][0].taskId).toBe("t2");
  });

  /** The node under it must not also react, or clicking a side task would select the wrong thing. */
  it("does not let the click reach the node beneath it", () => {
    const onNodeClick = vi.fn();
    render(
      <div onClick={onNodeClick}>
        <SideTaskCards sideTasks={three} />
      </div>,
    );

    fireEvent.click(screen.getByText("3 side tasks"));

    expect(onNodeClick).not.toHaveBeenCalled();
  });
});
