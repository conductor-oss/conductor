import { NodeData } from "reaflow";
import { ExecutionTask } from "types";
import { ExecutionTabs } from "./types";
export declare const useExecutionMachine: () => readonly [{
    readonly refetch: () => import("xstate").State<import("./types").ExecutionMachineContext, import("./types").ExecutionMachineEvents, any, {
        value: any;
        context: import("./types").ExecutionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").ExecutionMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly selectTask: (taskSel: {
        ref?: string;
        taskId?: string;
    }) => void;
    readonly expandDynamic: (taskReferenceName: string) => import("xstate").State<import("./types").ExecutionMachineContext, import("./types").ExecutionMachineEvents, any, {
        value: any;
        context: import("./types").ExecutionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").ExecutionMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly collapseDynamic: (taskReferenceName: string) => import("xstate").State<import("./types").ExecutionMachineContext, import("./types").ExecutionMachineEvents, any, {
        value: any;
        context: import("./types").ExecutionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").ExecutionMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly clearError: () => import("xstate").State<import("./types").ExecutionMachineContext, import("./types").ExecutionMachineEvents, any, {
        value: any;
        context: import("./types").ExecutionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").ExecutionMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly rerunExecutionWithLatestDefinitions: () => void;
    readonly createSheduleWithLatestDefinitions: () => void;
    readonly restartExecutionWithLatestDefinitions: () => void;
    readonly restartExecutionWithCurrentDefinitions: () => void;
    readonly retryExcutionFromFailed: () => void;
    readonly resumeExecution: () => void;
    readonly terminateExecution: () => void;
    readonly pauseExecution: () => void;
    readonly retryResumeSubworkflow: () => void;
    readonly changeExecutionTab: (tab: ExecutionTabs) => void;
    readonly updateDuration: (duration: number) => void;
    readonly closeRightPanel: () => void;
    readonly handleUpdateVariables: (data: string) => void;
    readonly selectNode: (node: NodeData) => void;
    readonly toggleAssistantPanel: () => void;
}, {
    flowActor: import("xstate").ActorRef<import("components/features/flow/state").FlowEvents, any> | undefined;
    countdownActor: import("xstate").ActorRef<any, any> | undefined;
    execution: import("types").WorkflowExecution | undefined;
    executionId: string | undefined;
    isReady: boolean;
    executionStatusMap: import("./StatusMapTypes").StatusMap | undefined;
    maybeError: import("./types").ErrorType | undefined;
    maybeMessage: import("./types").MessageType | undefined;
    openedTab: ExecutionTabs;
    taskListActor: import("xstate").ActorRef<any, any>;
    rightPanelActor: import("xstate").ActorRef<any, any>;
    isNoAccess: boolean;
    isNotFound: boolean;
    doWhileSelection: import("types").DoWhileSelection[] | undefined;
    nodes: NodeData<any>[];
    isAssistantPanelOpen: boolean;
    selectedTask: ExecutionTask<{
        forkedTasks: string[];
        forkedTaskDefs: import("types").TaskDef[];
        docLink?: string;
    }> | undefined;
}];
