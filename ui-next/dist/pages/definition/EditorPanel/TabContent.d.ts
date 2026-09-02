import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
interface TabContentProps {
    openedTab: number;
    isReady: boolean;
    isRunWorkflow: boolean;
    isInTaskFormState: boolean;
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
    getTabContentHeight: () => string;
}
export declare const TabContent: ({ openedTab, isReady, isRunWorkflow, isInTaskFormState, definitionActor, getTabContentHeight, }: TabContentProps) => import("react").JSX.Element;
export {};
