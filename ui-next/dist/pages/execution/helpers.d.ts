import { ExecutionTask, WorkflowExecution } from "types/Execution";
import { StatusMap } from "./state/StatusMapTypes";
/**
 * Whether a workflow execution is a Conductor-Agents-compiled agent run (carries
 * the `agentDef`/`agent_sdk` metadata stamp on its workflow definition), as
 * opposed to a plain Conductor workflow. Drives: which tab the execution page
 * defaults to, which sidebar nav item stays highlighted, and which route
 * (/execution/:id vs /agentExecutions/:id) an execution's detail view lives at.
 */
export declare function isAgentWorkflowExecution(execution: Pick<WorkflowExecution, "workflowDefinition"> | undefined | null): boolean;
export declare const taskWithLatestIteration: (tasksList?: ExecutionTask[], taskReferenceName?: string, taskId?: string) => ExecutionTask<{
    forkedTasks: string[];
    forkedTaskDefs: import("../..").TaskDef[];
    docLink?: string;
}> | undefined;
export declare function findTaskFromExecutionStatusMapById(mapObject: StatusMap, id: string | null): ExecutionTask<{
    forkedTasks: string[];
    forkedTaskDefs: import("../..").TaskDef[];
    docLink?: string;
}> | import("./state/StatusMapTypes").TypeStatusMap | null;
