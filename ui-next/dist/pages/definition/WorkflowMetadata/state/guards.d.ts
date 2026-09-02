import { WorkflowMetadataMachineContext, WorkflowChangedEvent } from "./types";
export declare const hasMetadataChanges: ({ metadataChanges }: WorkflowMetadataMachineContext, { workflow }: WorkflowChangedEvent) => boolean;
