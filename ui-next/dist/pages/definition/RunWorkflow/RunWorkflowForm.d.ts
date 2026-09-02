import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state";
import { RunMachineEvents } from "./state";
interface RunWorkFlowFormProps {
    runTabActor: ActorRef<RunMachineEvents>;
    workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents>;
}
export declare const RunWorkFlowForm: ({ runTabActor }: RunWorkFlowFormProps) => import("react").JSX.Element;
export {};
