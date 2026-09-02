import { TaskFormProps } from "./types";
/**
 * Config form for the AGENT task. Two runtimes, one task type, disjoint input shapes:
 * `agentType: "a2a"` calls a remote Agent2Agent endpoint (poll / streaming / push modes); `"conductor"`
 * runs a registered agent on the embedded agentspan runtime — its input mirrors `POST /api/agent/start`
 * (`AgentStartRequest`), not the A2A message shape.
 */
export declare const AgentTaskForm: ({ task, onChange }: TaskFormProps) => import("react").JSX.Element;
