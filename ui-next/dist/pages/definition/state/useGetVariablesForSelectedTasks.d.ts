import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./types";
export declare const useGetVariablesForSelectedTasks: (workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents> | undefined) => string[];
