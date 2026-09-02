import { TaskFormProps } from "./types";
/**
 * Config form for GET_AGENT_CARD — discovers a remote A2A agent's Agent Card. A2A-only: there is
 * no equivalent "card" concept for a registered Conductor agent, so this task has no agentType
 * selector (unlike AGENT/CANCEL_AGENT).
 */
export declare const GetAgentCardTaskForm: ({ task, onChange }: TaskFormProps) => import("react").JSX.Element;
