import React from "react";
import { IntegrationDef } from "types";
import { ActorRef } from "xstate";
import { RichAddTaskMenuEvents } from "./state/types";
interface IntegrationDrillDownContentProps {
    richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>;
    onAddToolTask: (tool: any) => void;
    onAddNewIntegration: (integration: IntegrationDef) => void;
}
export declare const IntegrationDrillDownContent: React.FC<IntegrationDrillDownContentProps>;
export {};
