import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkflowExecution } from "types/Execution";
import { TasksToDomain } from "./TasksToDomain";

vi.mock("./ExecutionInputOutput", () => ({
  default: ({ data }: { data: { title: string; hidden: boolean }[] }) => (
    <>
      {data
        .filter((item) => !item.hidden)
        .map((item) => (
          <h2 key={item.title}>{item.title}</h2>
        ))}
    </>
  ),
}));

describe("TasksToDomain", () => {
  it("titles the mapping in lowercase as task to domain", () => {
    const execution = {
      taskToDomain: { validate: "canary" },
    } as unknown as WorkflowExecution;

    render(<TasksToDomain execution={execution} />);

    expect(
      screen.getByRole("heading", { name: "Task to domain" }),
    ).toBeInTheDocument();
  });
});
