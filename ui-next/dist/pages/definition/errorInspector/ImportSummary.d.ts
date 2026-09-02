import { ErrorInspectorMachineEvents } from "./state";
import { ActorRef } from "xstate";
declare const ImportSummaryComponent: ({ errorInspectorActor, }: {
    errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>;
}) => import("react").JSX.Element | null;
export default ImportSummaryComponent;
