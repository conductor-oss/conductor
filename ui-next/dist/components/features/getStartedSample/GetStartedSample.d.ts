import { OperatingSystemEnvironment } from "./types";
export declare const DEFAULT_TASK_NAME = "my_first_simple_task";
export declare const GetStartedSample: ({ serverUrl, onTaskNameUpdated, }: {
    apiKey?: string;
    apiSecret?: string;
    serverUrl?: string;
    environment: OperatingSystemEnvironment;
    onTaskNameUpdated?: (taskName: string) => void;
}) => import("react").JSX.Element;
