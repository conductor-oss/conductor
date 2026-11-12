/**
 * The "Click here" link on a TASK_SUMMARY task (a fork with more than 300
 * branches) must leave the execution page on the Task List tab.
 *
 * Closing the panel clears taskId/taskReferenceName through useQueryState in
 * the same tick, so the test's onClose does the same. If the tab is written
 * any other way, that clear rebuilds the URL from the pre-click location and
 * the tab param is lost.
 */

import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { useQueryState } from "react-router-use-location-state";
import { describe, expect, it } from "vitest";
import { ExecutionTask } from "types";
import { SummaryTask } from "./SummaryTask";

const summaryTask = {
  taskType: "TASK_SUMMARY",
  taskId: "summary-task-id",
  referenceTaskName: "fork_summary",
  status: "FAILED",
  outputData: {
    summary: {
      totalTasks: 600,
      taskCountByStatus: { COMPLETED: 598, FAILED: 2 },
    },
  },
} as unknown as ExecutionTask;

let currentSearch = "";
let goBack = () => {};

const LocationSpy = () => {
  currentSearch = useLocation().search;
  const navigate = useNavigate();
  goBack = () => navigate(-1);
  return null;
};

const Harness = () => {
  const [, setTaskId] = useQueryState("taskId", "");
  const [, setTaskReferenceName] = useQueryState("taskReferenceName", "");
  return (
    <>
      <SummaryTask
        selectedTask={summaryTask}
        onClose={() => {
          setTaskId("");
          setTaskReferenceName("");
        }}
      />
      <LocationSpy />
    </>
  );
};

describe("SummaryTask", () => {
  it("opens the Task List tab and keeps it after the panel closes", () => {
    render(
      <MemoryRouter
        initialEntries={[
          "/execution/wf-1?taskReferenceName=fork_summary&taskId=summary-task-id",
        ]}
      >
        <Harness />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByText("Click here"));

    const params = new URLSearchParams(currentSearch);
    expect(params.get("tab")).toBe("taskList");
    expect(params.get("taskId")).toBeFalsy();
    expect(params.get("taskReferenceName")).toBeFalsy();
  });

  it("adds a history entry so Back returns to where the summary was opened", () => {
    render(
      <MemoryRouter
        initialEntries={[
          "/execution/wf-1",
          "/execution/wf-1?taskReferenceName=fork_summary&taskId=summary-task-id",
        ]}
        initialIndex={1}
      >
        <Harness />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByText("Click here"));
    act(() => goBack());

    const params = new URLSearchParams(currentSearch);
    expect(params.get("tab")).toBeFalsy();
    expect(params.get("taskReferenceName")).toBe("fork_summary");
  });
});
