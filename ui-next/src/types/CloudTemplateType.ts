import { IntegrationI, ModelDto } from "./Integrations";
import { PromptDef } from "./Prompts";

// The original type is IntegrationDef, is broken, it is used for configuring an integration and as integration pulled from the server which is not the same

export type IntegrationAndModel = {
  integration: IntegrationI;
  models: ModelDto[];
};

export type CloudTemplateTypeV1 = {
  id: string;
  title: string;
  description: string;
  featureLabelAndLinks: {
    [key: string]: string;
  };
  githubProjectLinks: {
    [key: string]: string;
  };
  workflowDefinitionGithubLink: string;
  workflowTemplateDefLink?: string;
  taskDefinitionsGithubLink?: string;
  taskTemplateDefsLink?: string;
  userFormsGithubLink?: string;
  userFormTemplateDefLink?: string;
  schemasGithubLink?: string;
  schemaDefTemplateLink?: string;
  helpDocumentationLink?: string;
  integrationAndModelsGithubLink?: string;
  promptsGithubLink?: string;
  thumbnailUrl?: string;
  category: string;
  tags?: string[];
  version: 1;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
};

export type PlaceholderDef = {
  key: string;
  value: string;
};

export type CloudTemplateTypeV2 = {
  id: string;
  title: string;
  description: string;
  featureLabelAndLinks: {
    [key: string]: string;
  };
  githubProjectLinks: {
    [key: string]: string;
  };

  taskDefinitions: Record<string, unknown>[];
  workflowDefinitions: Record<string, unknown>[];
  helpDocumentationLink?: string;
  userForms: Record<string, unknown>[];
  schemas: Record<string, unknown>[];
  integrationsWithModels: IntegrationAndModel[];
  secrets: Record<string, unknown>[];
  environmentVariables: Record<string, unknown>[];
  schedules: Record<string, unknown>[];
  webhooks: Record<string, unknown>[];
  remoteServices: Record<string, unknown>[];
  prompts: PromptDef[];
  placeholders: PlaceholderDef[];
  thumbnailUrl?: string;
  category: string;
  tags?: string[];
  version: 2;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
};

export type CloudTemplateType = CloudTemplateTypeV1 | CloudTemplateTypeV2;
