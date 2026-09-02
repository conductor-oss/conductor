import { WorkflowDefinitionEvents } from "pages/definition/state/types";
import { ActorRef } from "xstate";
declare const GraphPanel: ({ definitionActor, }: {
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
}) => import("react").JSX.Element;
export default GraphPanel;
