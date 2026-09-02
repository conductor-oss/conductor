import { ActorRef } from "xstate";
import { ErrorInspectorMachineEvents } from "./state/types";
interface ErrorInspectorProps {
    errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>;
}
declare const ErrorInspector: ({ errorInspectorActor }: ErrorInspectorProps) => import("react").JSX.Element;
export default ErrorInspector;
