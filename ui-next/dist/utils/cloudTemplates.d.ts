import { AuthHeaders } from "types/common";
import { CloudTemplateType, CloudTemplateTypeV1, CloudTemplateTypeV2, IntegrationAndModel } from "types/CloudTemplateType";
import { WorkflowDef } from "types/WorkflowDef";
import { CommonTaskDef } from "types/TaskType";
import { HumanTemplate } from "types/HumanTaskTypes";
import { SchemaDefinition } from "types/SchemaDefinition";
import { SchemaResult, TaskResult, HumanTemplateResult, WorkflowResult, IntegrationAndModelResult, PromptResult } from "types/CloudTemplateResults";
import { IntegrationI } from "types/Integrations";
import { PromptDef } from "types/Prompts";
export declare const justName: ({ name }: {
    name: string;
}) => string;
/**
 * Validates that a template has all the required fields that TemplateCard needs to render properly.
 * This includes: id, title, description, category, tags (as an array), and version >= 2.
 */
export declare const isValidTemplate: (template: CloudTemplateType | null | undefined) => boolean;
export declare const fetchCloudTemplates: () => Promise<{
    cloudTemplates: CloudTemplateType[];
}>;
export declare const fetchCloudTemplatesPreferCached: () => Promise<{
    cloudTemplates: CloudTemplateType[];
}>;
export type ImportSummary = {
    workflowResponse: WorkflowDef[];
    taskResponse: CommonTaskDef[];
    userFormsResponse: HumanTemplate[];
    schemasResponse: SchemaDefinition[];
    integrationsAndModelsResponse: IntegrationAndModel[];
    promptsResponse: PromptDef[];
};
export declare const fetchWorkflowWithDependencies: (selectedCard: CloudTemplateType) => Promise<ImportSummary>;
export declare const fetchWorkflowWithDependenciesV2: (selectedCard: CloudTemplateTypeV2) => ImportSummary;
export declare const fetchWorkflowWithDependenciesV1: (selectedCard: CloudTemplateTypeV1) => Promise<ImportSummary>;
export declare const importWorkflow: (context: {
    authHeaders: AuthHeaders;
    workflowNames: string[];
}, workflowDefinition: WorkflowDef) => Promise<{
    workflow: WorkflowDef;
    success: boolean;
    message: string;
} | {
    workflow: WorkflowDef;
    success: boolean;
    message?: undefined;
}>;
export declare const importTask: (context: {
    authHeaders: AuthHeaders;
    taskNames: string[];
}, modifiedTaskDefinition: CommonTaskDef) => Promise<{
    task: CommonTaskDef;
    success: boolean;
    message: string;
} | {
    task: CommonTaskDef;
    success: boolean;
    message?: undefined;
}>;
export declare const importUserForm: (context: {
    authHeaders: AuthHeaders;
}, userFormDefinition: HumanTemplate, userFormNames: string[]) => Promise<{
    userForm: HumanTemplate;
    success: boolean;
    message: string;
} | {
    userForm: HumanTemplate;
    success: boolean;
    message?: undefined;
}>;
export declare const importSchemas: (context: {
    authHeaders: AuthHeaders;
}, schemasDefinition: SchemaDefinition, schemasNames: string[]) => Promise<{
    schema: SchemaDefinition;
    success: boolean;
    message: string;
} | {
    schema: SchemaDefinition;
    success: boolean;
    message?: undefined;
}>;
export type ImportWorkflowApplicationArgs = {
    authHeaders: AuthHeaders;
    workflowNames: string[];
    taskNames: string[];
    existingIntegrations: IntegrationI[];
    cardWorkflowDefinitions: WorkflowDef[];
    cardWorkflowDefinitionChanges: WorkflowDef[];
    cardTaskDefinitions?: CommonTaskDef[];
    cardUserForms?: HumanTemplate[];
    cardSchemas?: SchemaDefinition[];
    cardIntegrationsAndModels?: IntegrationAndModel[];
    cardPrompts?: PromptDef[];
};
export type ImportWorkflowApplicationResult = Promise<{
    importWorkflowResults: WorkflowResult[];
    importTaskResults: TaskResult[];
    importUserFormResults: HumanTemplateResult[];
    importSchemaResults: SchemaResult[];
    importIntegrationAndModelResults: IntegrationAndModelResult[];
    importPromptsResult: PromptResult[];
}>;
export declare const importWorkflowWithDependencies: (context: ImportWorkflowApplicationArgs) => ImportWorkflowApplicationResult;
