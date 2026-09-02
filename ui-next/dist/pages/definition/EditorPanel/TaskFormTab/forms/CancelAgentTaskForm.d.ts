import { TaskFormProps } from "./types";
/**
 * Config form for CANCEL_AGENT. `agentType: "a2a"` cancels a running task on a remote A2A agent
 * (Agent URL + Task ID); `"conductor"` terminates a Conductor agent execution — same shape as the
 * Terminate Workflow task (execution id + reason).
 */
export declare const CancelAgentTaskForm: ({ task, onChange }: TaskFormProps) => import("react").JSX.Element;
