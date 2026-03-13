import { CommonTaskDef, WorkflowDef } from "types";
import { HumanTemplate } from "types/HumanTaskTypes";
import { IntegrationI, ModelDto } from "types/Integrations";
import { PromptDef } from "types/Prompts";
import { SchemaDefinition } from "types/SchemaDefinition";

export type WorkflowResult = {
  success: boolean;
  message?: string;
  workflow: WorkflowDef;
};

export type TaskResult = {
  success: boolean;
  message?: string;
  task: CommonTaskDef;
};

export type SchemaResult = {
  success: boolean;
  message?: string;
  schema: SchemaDefinition;
};

export type IntegrationResult = {
  success: boolean;
  message?: string;
  integration: IntegrationI;
};

export type ModelResult = {
  success: boolean;
  message?: string;
  model: ModelDto;
};

export type PromptResult = {
  success: boolean;
  message?: string;
  prompt: PromptDef;
};

export type IntegrationAndModelResult = {
  success: boolean;
  message?: string;
  integration: IntegrationI;
  modelResults: ModelResult[];
};

export type HumanTemplateResult = {
  success: boolean;
  message?: string;
  userForm: HumanTemplate;
};
