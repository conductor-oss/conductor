import { ActorRef } from "xstate";
import { ErrorInspectorMachineEvents } from "./types";
export declare const useErrorInspectorActor: (errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>) => readonly [{
    readonly workflowErrors: any;
    readonly taskErrors: any;
    readonly unreachableTaskErrors: any;
    readonly serverErrors: any;
    readonly runWorkflowErrors: any;
    readonly taskReferenceErrors: any;
    readonly workflowReferenceErrors: any;
    readonly errorCount: any;
    readonly warningCount: any;
    readonly taskErrorsExpanded: any;
    readonly workflowErrorsExpanded: any;
    readonly referenceTaskErrorsExpanded: any;
    readonly referenceWorkflowErrorsExpanded: any;
    readonly expanded: any;
    readonly tasks: any;
}, {
    readonly handleToggleTaskErrors: () => void;
    readonly handleToggleWorkflowErrors: () => void;
    readonly handleCleanServerErrors: () => void;
    readonly handleToggleTaskReferenceErrors: () => void;
    readonly handleToggleWorkflowReferenceErrors: () => void;
    readonly handleClickReference: (referenceText: string) => void;
    readonly handleToggleErrorInspector: () => void;
    readonly handleSetErrorInspectorCollapsed: () => void;
    readonly handleJumpToFirstError: () => void;
}];
