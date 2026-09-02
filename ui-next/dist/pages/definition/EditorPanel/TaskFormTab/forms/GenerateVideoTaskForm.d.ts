import { TaskFormProps } from "./types";
/**
 * Config form for GENERATE_VIDEO — generates video from a prompt/image using an LLM provider.
 * The task polls asynchronously for completion via its output jobId.
 */
export declare const GenerateVideoTaskForm: ({ task, onChange }: TaskFormProps) => import("react").JSX.Element;
