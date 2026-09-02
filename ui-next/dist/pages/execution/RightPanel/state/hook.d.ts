import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { ActorRef } from "xstate";
import { RightPanelEvents } from "./types";
export declare const useRightPanelActor: (rightPanelActor: ActorRef<RightPanelEvents>) => readonly [{
    readonly selectedTask: ExecutionTask<{
        forkedTasks: string[];
        forkedTaskDefs: import("../../../..").TaskDef[];
        docLink?: string;
    }> | undefined;
    readonly retryIterationOptions: (ExecutionTask<{
        forkedTasks: string[];
        forkedTaskDefs: import("../../../..").TaskDef[];
        docLink?: string;
    }> | import("../iterationHelpers").IterationPlaceholder)[] | undefined;
    readonly parentDoWhileRef: string | undefined;
    readonly maybeSiblings: ExecutionTask<{
        forkedTasks: string[];
        forkedTaskDefs: import("../../../..").TaskDef[];
        docLink?: string;
    }>[];
    readonly executionId: string | undefined;
    readonly authHeaders: import("../../../..").AuthHeaders | undefined;
    readonly isIteration: boolean | undefined;
    readonly errorMessage: string | undefined;
    readonly taskLogs: import("../../../..").TaskLog[] | undefined;
    readonly currentTab: number;
}, {
    readonly handleClosePanel: () => void;
    readonly handleChangeTaskStatus: (status: string, body: string) => void;
    readonly handleReRunRequest: () => void;
    readonly clearErrorMessage: () => void;
    readonly handleSelectTask: (selectedTask: ExecutionTask) => void;
    readonly handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
}];
