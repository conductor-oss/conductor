import { WorkflowExecution } from "types/Execution";
import InputOutput from "./ExecutionInputOutput";

export function TasksToDomain({ execution }: { execution: WorkflowExecution }) {
  return (
    <InputOutput
      execution={execution as unknown as Record<string, unknown>}
      data={[
        {
          title: "Task to domain",
          src: execution.taskToDomain ?? {},
          hidden: !execution.taskToDomain,
          style: {
            minWidth: 340,
          },
        },
      ]}
    />
  );
}
