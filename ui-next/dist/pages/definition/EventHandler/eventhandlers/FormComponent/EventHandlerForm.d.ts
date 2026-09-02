import { ActorRef } from "xstate";
import { FormHandlerEvents } from "./state/types";
declare const EventHandlerForm: ({ actor, }: {
    actor: ActorRef<FormHandlerEvents>;
}) => import("react").JSX.Element;
export default EventHandlerForm;
