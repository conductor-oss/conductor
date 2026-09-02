import { Monaco } from "@monaco-editor/react";
import { IdempotencyValuesProp } from "pages/definition/RunWorkflow/state";
import { MutableRefObject } from "react";
import { DoWhileTaskDef, InlineTaskDef, JDBCTaskDef, SwitchTaskDef } from "types/TaskType";
import { AuthHeaders, TaskDef, TaskType } from "types/common";
export type OnlyTheWordInfoProp = {
    word: string;
    startColumn: number;
    endColumn: number;
};
export declare const editorAddCommandAltEnter: (editor: Monaco, monaco: Monaco, taskRef: MutableRefObject<Partial<InlineTaskDef> | Partial<DoWhileTaskDef> | Partial<SwitchTaskDef> | Partial<JDBCTaskDef> | null>, callBack: (onlyTheWordInfo: OnlyTheWordInfoProp) => void) => any;
export declare const editorHandleAutoSize: (editor: Monaco, parentWrapperRef: MutableRefObject<Monaco>) => void;
export declare const editorDecorations: (model: Monaco, parameters: string[], monaco: Monaco) => {
    range: any;
    options: {
        className: string;
    };
}[][];
export declare const updateInputParametersCommon: (taskJson: Partial<TaskDef>, originalTask: Partial<TaskDef>, authHeaders: AuthHeaders, onChange: (data: Partial<TaskDef>) => void, workflowNameVersionStringPath: string, inputParametersStringPath: string, taskType: TaskType.START_WORKFLOW | TaskType.SUB_WORKFLOW, getWorkflowDefinitionByNameAndVersionFn: ({ name, version, authHeaders, }: {
    name: string;
    version: number;
    authHeaders: AuthHeaders;
}) => Promise<any>) => Promise<void>;
export declare const handleChangeIdempotencyValues: (data: IdempotencyValuesProp, task: Partial<TaskDef>, path: string, onChange: (task: Partial<TaskDef>) => void) => void;
export declare const getCorrespondingJoinTask: (originalTask: Partial<TaskDef>, tasksList?: Partial<TaskDef>[]) => Partial<TaskDef>[];
/**
 * Fetches a schema by name and version, then generates default values from it
 * @param schemaName - The name of the schema
 * @param schemaVersion - The version of the schema (optional)
 * @param authHeaders - Authentication headers for the API request
 * @returns Promise that resolves to default values object, or null if fetching/generation fails
 */
export declare const getDefaultValuesFromSchema: (schemaName: string, schemaVersion: number | undefined, authHeaders: AuthHeaders) => Promise<Record<string, unknown> | null>;
/**
 * Checks if inputParameters should be populated from schema and returns default values if conditions are met
 * @param newSchema - The new schema form value
 * @param currentTask - The current task definition
 * @param authHeaders - Authentication headers for the API request
 * @returns Promise that resolves to default values object if conditions are met, or null otherwise
 */
export declare const getInputParametersFromSchemaIfNeeded: (newSchema: {
    inputSchema?: {
        name?: string;
        version?: number;
    };
} | undefined, currentTask: Partial<TaskDef> | undefined, authHeaders: AuthHeaders) => Promise<Record<string, unknown> | null>;
