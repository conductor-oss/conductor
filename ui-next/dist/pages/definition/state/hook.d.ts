import { WorkflowDefinitionEvents } from "pages/definition/state/types";
import { User } from "types/User";
import { State } from "xstate";
export declare const useWorkflowDefinition: (currentUser: User) => readonly [{
    readonly handleSetMessage: (messageSeverity: any) => State<import("pages/definition/state/types").DefinitionMachineContext, WorkflowDefinitionEvents, any, {
        value: any;
        context: import("pages/definition/state/types").DefinitionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, WorkflowDefinitionEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleResetMessage: () => void;
    readonly setLeftPanelExpanded: () => void;
}, {
    readonly isNewWorkflow: boolean;
    readonly workflowName: string;
    readonly workflowVersions: number[];
    readonly currentVersion: string | undefined;
    readonly message: {
        text?: string;
        severity?: string;
    };
    readonly definitionActor: import("xstate").Interpreter<import("pages/definition/state/types").DefinitionMachineContext, any, WorkflowDefinitionEvents, {
        value: any;
        context: import("pages/definition/state/types").DefinitionMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, WorkflowDefinitionEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly leftPanelExpanded: any;
    readonly blogUrl: string;
    readonly isNotFound: boolean;
    readonly isErrorFetching: boolean;
}];
