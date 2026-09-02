import React, { ReactElement } from "react";
import { ActorRef } from "xstate";
import { ServiceMethodsMachineEvents } from "./state/types";
declare const EditTaskDefConfigModal: ({ actor, hedgingComponent, }: {
    actor: ActorRef<ServiceMethodsMachineEvents>;
    hedgingComponent: ReactElement;
}) => React.JSX.Element;
export default EditTaskDefConfigModal;
