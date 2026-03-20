import { AuthHeaders, ErrorObj } from "types/common";
import {
  CloudTemplateType,
  CloudTemplateTypeV1,
  CloudTemplateTypeV2,
  IntegrationAndModel,
} from "types/CloudTemplateType";
import { logger } from "./logger";
import { getErrorMessage, tryFunc, tryToJson } from "./utils";
import { WorkflowDef } from "types/WorkflowDef";
import { fetchWithContext } from "plugins/fetch";
import { CommonTaskDef } from "types/TaskType";
import _zip from "lodash/zip";
import _flow from "lodash/flow";
import { featureFlags, FEATURES } from "./flags";
import { replaceValues } from "./object";
import { HumanTemplate } from "types/HumanTaskTypes";
import { SchemaDefinition } from "types/SchemaDefinition";
import {
  SchemaResult,
  TaskResult,
  HumanTemplateResult,
  WorkflowResult,
  IntegrationAndModelResult,
  ModelResult,
  IntegrationResult,
  PromptResult,
} from "types/CloudTemplateResults";
import { IntegrationI, ModelDto } from "types/Integrations";
import { PromptDef } from "types/Prompts";
import { toMaybeQueryString } from "./toMaybeQueryString";

const CLOUD_CALL_STORAGE_KEY = "cloudTemplates";
const WF_TEMPLATE_URL_PREFIX = "ct-wf-";

const TASK_TEMPLATE_URL_PREFIX = "ct-task-";

// https://beta-saas.orkesconductor.com/api/templates
// https://cloud.orkes.io/api/templates

// removing quotes from the string
const cloudTemplatesSourceFlagValue = featureFlags
  .getValue(FEATURES.CLOUD_TEMPLATES_SOURCE)
  ?.replace(/['"]/g, "");

const CLOUD_URL =
  cloudTemplatesSourceFlagValue ??
  "https://raw.githubusercontent.com/conductor-oss/awesome-conductor-apps/refs/heads/main/templates.json";

export const justName = ({ name }: { name: string }) => name;

/**
 * Validates that a template has all the required fields that TemplateCard needs to render properly.
 * This includes: id, title, description, category, tags (as an array), and version >= 2.
 */
export const isValidTemplate = (
  template: CloudTemplateType | null | undefined,
): boolean => {
  if (!template) return false;

  return (
    typeof template.id === "string" &&
    template.id.length > 0 &&
    typeof template.title === "string" &&
    template.title.length > 0 &&
    typeof template.description === "string" &&
    typeof template.category === "string" &&
    template.category.length > 0 &&
    Array.isArray(template.tags) &&
    typeof template.version === "number" &&
    template.version >= 2
  );
};

// const fetchContext = fetchContextNonHook();

export const fetchCloudTemplates = async (): Promise<{
  cloudTemplates: CloudTemplateType[];
}> => {
  try {
    const response = await fetch(CLOUD_URL);
    const result = await response.text();

    const relevantTemplates = JSON.parse(result);

    localStorage.setItem(CLOUD_CALL_STORAGE_KEY, result);

    return { cloudTemplates: relevantTemplates };
  } catch (error) {
    logger.error(error);
    logger.log("Using cached cloud templates");
    const cached = localStorage.getItem(CLOUD_CALL_STORAGE_KEY);
    return { cloudTemplates: tryToJson(cached) || [] };
  }
};

export const fetchCloudTemplatesPreferCached = async (): Promise<{
  cloudTemplates: CloudTemplateType[];
}> => {
  const cached = localStorage.getItem(CLOUD_CALL_STORAGE_KEY);
  if (cached) {
    return { cloudTemplates: tryToJson(cached) || [] };
  }
  return fetchCloudTemplates();
};
// FIXME this code is repeated makes no sense at all

const fetchWorkflowAndCatch = async (
  workflowPath?: string,
  keyPrefix = WF_TEMPLATE_URL_PREFIX,
) => {
  try {
    if (workflowPath) {
      const workflowResponse = await fetch(workflowPath);
      const workflowResult = await workflowResponse.json();

      localStorage.setItem(
        `${keyPrefix}${workflowPath}`,
        JSON.stringify(workflowResult),
      );

      return workflowResult;
    }

    return [];
  } catch {
    return tryToJson(localStorage.getItem(`${keyPrefix}${workflowPath}`)) || [];
  }
};

const fetchTask = async (taskPath?: string) => {
  try {
    if (taskPath) {
      const taskResponse = await fetch(taskPath);
      const taskResult = await taskResponse.json();

      localStorage.setItem(
        `${TASK_TEMPLATE_URL_PREFIX}${taskPath}`,
        taskResult,
      );

      return taskResult;
    }

    return [];
  } catch {
    return (
      tryToJson(
        localStorage.getItem(`${TASK_TEMPLATE_URL_PREFIX}${taskPath}`),
      ) || []
    );
  }
};

const fetchUserForms = async (userFormsPath?: string) => {
  try {
    if (userFormsPath) {
      const userFormsResponse = await fetch(userFormsPath);
      const userFormsResult = await userFormsResponse.json();

      return userFormsResult;
    }

    return [];
  } catch {
    logger.error("Failed to fetch User Forms");
    return [];
  }
};

const fetchSchemas = async (schemasPath?: string) => {
  try {
    if (schemasPath) {
      const schemasResponse = await fetch(schemasPath);
      const schemasResult = await schemasResponse.json();

      return schemasResult;
    }

    return [];
  } catch {
    logger.error("Failed to fetch Schemas");
    return [];
  }
};

const fetchIntegrationAndModels = async (
  integrationsAndModelsPath?: string,
): Promise<IntegrationAndModel[]> => {
  try {
    if (integrationsAndModelsPath) {
      const integrationsAndModelResponse = await fetch(
        integrationsAndModelsPath,
      );
      const integrationsAndModels = await integrationsAndModelResponse.json();

      return integrationsAndModels;
    }

    return [];
  } catch {
    return (
      tryToJson(
        localStorage.getItem(
          `${TASK_TEMPLATE_URL_PREFIX}${integrationsAndModelsPath}`,
        ),
      ) || []
    );
  }
};

const fetchPrompts = async (promptsPath?: string) => {
  try {
    if (promptsPath) {
      const promptsResponse = await fetch(promptsPath);
      const promptsResult = await promptsResponse.json();

      return promptsResult;
    }
    logger.info("prompts path not defined");
    return [];
  } catch {
    logger.error("Failed to fetch Prompts");
    return [];
  }
};
// end of repeated code
export type ImportSummary = {
  workflowResponse: WorkflowDef[];
  taskResponse: CommonTaskDef[];
  userFormsResponse: HumanTemplate[];
  schemasResponse: SchemaDefinition[];
  integrationsAndModelsResponse: IntegrationAndModel[];
  promptsResponse: PromptDef[];
};

const isCloudTemplateV1 = (
  card: CloudTemplateType,
): card is CloudTemplateTypeV1 => {
  return card.version === 1;
};

const isCloudTemplateV2 = (
  card: CloudTemplateType,
): card is CloudTemplateTypeV2 => {
  return card.version === 2;
};

export const fetchWorkflowWithDependencies = async (
  selectedCard: CloudTemplateType,
): Promise<ImportSummary> => {
  const empty = {
    workflowResponse: [],
    taskResponse: [],
    userFormsResponse: [],
    schemasResponse: [],
    integrationsAndModelsResponse: [],
    promptsResponse: [],
  };
  if (!selectedCard) {
    return empty;
  }
  if (isCloudTemplateV1(selectedCard)) {
    return fetchWorkflowWithDependenciesV1(selectedCard);
  }
  if (isCloudTemplateV2(selectedCard)) {
    return fetchWorkflowWithDependenciesV2(selectedCard);
  }
  return empty;
};

export const fetchWorkflowWithDependenciesV2 = (
  selectedCard: CloudTemplateTypeV2,
): ImportSummary => {
  return {
    workflowResponse: (selectedCard?.workflowDefinitions ??
      []) as unknown as WorkflowDef[],
    taskResponse: (selectedCard?.taskDefinitions ??
      []) as unknown as CommonTaskDef[],
    userFormsResponse: (selectedCard?.userForms ??
      []) as unknown as HumanTemplate[],
    schemasResponse: (selectedCard?.schemas ??
      []) as unknown as SchemaDefinition[],
    integrationsAndModelsResponse: (selectedCard?.integrationsWithModels ??
      []) as unknown as IntegrationAndModel[],
    promptsResponse: (selectedCard?.prompts ?? []) as PromptDef[],
  };
};

export const fetchWorkflowWithDependenciesV1 = async (
  selectedCard: CloudTemplateTypeV1,
) => {
  return tryFunc<ImportSummary, ErrorObj>({
    fn: async () => {
      const workflowPath =
        selectedCard?.workflowTemplateDefLink ??
        selectedCard?.workflowDefinitionGithubLink;
      const taskPath =
        selectedCard?.taskTemplateDefsLink ??
        selectedCard?.taskDefinitionsGithubLink;
      const userFormsPath =
        selectedCard?.userFormTemplateDefLink ??
        selectedCard?.userFormsGithubLink;
      const schemasPath =
        selectedCard?.schemaDefTemplateLink ?? selectedCard?.schemasGithubLink;
      const integrationsAndModels =
        selectedCard?.integrationAndModelsGithubLink;
      const promptsPath = selectedCard?.promptsGithubLink;

      const [
        workflowData,
        taskData,
        userFormsData,
        schemasData,
        integrationsAndModelsData,
        promptsData,
      ] = await Promise.all([
        fetchWorkflowAndCatch(workflowPath),
        fetchTask(taskPath),
        fetchUserForms(userFormsPath),
        fetchSchemas(schemasPath),
        fetchIntegrationAndModels(integrationsAndModels),
        fetchPrompts(promptsPath),
      ]);

      return {
        workflowResponse: workflowData ?? [],
        taskResponse: taskData ?? [],
        userFormsResponse: userFormsData ?? [],
        schemasResponse: schemasData ?? [],
        integrationsAndModelsResponse: integrationsAndModelsData ?? [],
        promptsResponse: promptsData ?? [],
      } as const;
    },
    customError: {
      message: "Fetching workflows and tasks failed!",
    },
    showCustomError: false,
  });
};

export const importWorkflow = async (
  context: { authHeaders: AuthHeaders; workflowNames: string[] },
  workflowDefinition: WorkflowDef,
) => {
  const { authHeaders, workflowNames } = context;
  if (workflowNames.includes(workflowDefinition.name)) {
    return {
      workflow: workflowDefinition,
      success: false,
      message: "Workflow already exists",
    };
  }
  try {
    await fetchWithContext(
      "/metadata/workflow?overwrite=true",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify(workflowDefinition),
      },
    );
    return { workflow: workflowDefinition, success: true };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      workflow: workflowDefinition,
      success: false,
      message: errorMessage ?? "Saving Failed",
    };
  }
};

export const importTask = async (
  context: { authHeaders: AuthHeaders; taskNames: string[] },
  modifiedTaskDefinition: CommonTaskDef,
) => {
  const { authHeaders, taskNames } = context;
  if (taskNames.includes(modifiedTaskDefinition.name)) {
    return {
      task: modifiedTaskDefinition,
      success: false,
      message: "Task already exists",
    };
  }
  try {
    const stringDefinition = JSON.stringify(modifiedTaskDefinition, null, 2);
    const body = `[${stringDefinition}]`;

    await fetchWithContext(
      "/metadata/taskdefs",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body,
      },
    );

    return { task: modifiedTaskDefinition, success: true };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      task: modifiedTaskDefinition,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};

export const importUserForm = async (
  context: { authHeaders: AuthHeaders },
  userFormDefinition: HumanTemplate,
  userFormNames: string[],
) => {
  const { authHeaders } = context;
  if (userFormNames.includes(userFormDefinition.name)) {
    return {
      userForm: userFormDefinition,
      success: false,
      message: "User form already exists",
    };
  }
  try {
    await fetchWithContext(
      "/human/template",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify(userFormDefinition),
      },
    );

    return { userForm: userFormDefinition, success: true };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      userForm: userFormDefinition,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};

export const importSchemas = async (
  context: { authHeaders: AuthHeaders },
  schemasDefinition: SchemaDefinition,
  schemasNames: string[],
) => {
  const { authHeaders } = context;
  if (schemasNames.includes(schemasDefinition.name)) {
    return {
      schema: schemasDefinition,
      success: false,
      message: "Schema already exists",
    };
  }
  try {
    await fetchWithContext(
      "/schema",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify(schemasDefinition),
      },
    );

    return { schema: schemasDefinition, success: true };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      schema: schemasDefinition,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};

const importIntegration = async (
  context: { authHeaders: AuthHeaders },
  integration: IntegrationI,
): Promise<IntegrationResult> => {
  const { authHeaders } = context;
  try {
    await fetchWithContext(
      `/integrations/provider/${integration.name}`,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify(integration),
      },
    );
    return {
      success: true,
      integration,
    };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      integration: integration,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};

const importModel = async (
  context: { authHeaders: AuthHeaders },
  model: ModelDto,
): Promise<ModelResult> => {
  const { authHeaders } = context;
  try {
    await fetchWithContext(
      `/integrations/provider/${model.integrationName}/integration/${model.api}`,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify(model),
      },
    );
    return {
      model: model,
      success: true,
    };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      model: model,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};

const importIntegrationAndModel = async (
  context: { authHeaders: AuthHeaders },
  integrationAndModel: IntegrationAndModel,
  existingIntegrations: IntegrationI[],
): Promise<IntegrationAndModelResult> => {
  const integration = integrationAndModel.integration;
  const existingIntegration = existingIntegrations.find(
    (i) => i.name === integration.name,
  );
  let importIntegrationResult: IntegrationResult;
  if (existingIntegration === undefined) {
    importIntegrationResult = await importIntegration(context, integration);
    if (importIntegrationResult?.success) {
      const models = integrationAndModel.models;
      const importModelResults: ModelResult[] = await Promise.all(
        models.map((model) => importModel(context, model)),
      );
      return {
        integration: integration,
        modelResults: importModelResults,
        success: true,
        message: "Integration and models imported successfully",
      };
    }
  } else {
    importIntegrationResult = {
      integration: existingIntegration,
      success: true,
      message: "Integration already exists",
    };

    const models = integrationAndModel.models;
    const importModelResults: ModelResult[] = await Promise.all(
      models.map((model) => importModel(context, model)),
    );
    return {
      integration: integration,
      modelResults: importModelResults,
      success: true,
      message: "Integration and models imported successfully",
    };
  }

  return {
    ...importIntegrationResult,
    modelResults: [],
  };
};

const importPrompt = async (
  context: { authHeaders: AuthHeaders },
  prompt: PromptDef,
): Promise<PromptResult> => {
  const { authHeaders } = context;
  const promptObj = {
    models: prompt.integrations,
    description: prompt.description,
  };
  try {
    await fetchWithContext(
      `/prompts/${prompt.name}${toMaybeQueryString(promptObj)}`,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: prompt.template,
      },
    );
    return {
      prompt,
      success: true,
    };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      prompt,
      success: false,
      message: errorMessage ?? "Saving failed",
    };
  }
};
export type ImportWorkflowApplicationArgs = {
  authHeaders: AuthHeaders;
  workflowNames: string[];
  taskNames: string[];
  existingIntegrations: IntegrationI[]; // Merge existing integrations if exists, So that we dont replace their apikey
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

export const importWorkflowWithDependencies = async (
  context: ImportWorkflowApplicationArgs,
): ImportWorkflowApplicationResult => {
  const {
    workflowNames = [],
    taskNames = [],
    existingIntegrations = [],
    cardWorkflowDefinitions = [],
    cardWorkflowDefinitionChanges = [],
    cardTaskDefinitions = [],
    cardUserForms = [],
    cardSchemas = [],
    cardIntegrationsAndModels = [],
    cardPrompts = [],
  } = context;

  // Test if names dont colide between new workflows
  const maybeWorkflowRepeatedNames =
    new Set(cardWorkflowDefinitionChanges.map(justName)).size !==
    cardWorkflowDefinitionChanges.length
      ? cardWorkflowDefinitionChanges.map((w) => ({
          workflow: w,
          success: false,
          message: "Workflows cant have the same name",
        }))
      : [];

  const maybeTasksRepeatedNames =
    new Set(cardTaskDefinitions.map(justName)).size !==
    cardTaskDefinitions.length
      ? cardTaskDefinitions.map((t) => ({
          task: t,
          success: false,
          message: "Tasks cant have the same name",
        }))
      : [];

  if (
    maybeTasksRepeatedNames.length > 0 ||
    maybeWorkflowRepeatedNames.length > 0
  ) {
    return {
      importWorkflowResults: maybeWorkflowRepeatedNames,
      importTaskResults: maybeTasksRepeatedNames,
      importUserFormResults: [],
      importSchemaResults: [],
      importIntegrationAndModelResults: [],
      importPromptsResult: [],
    };
  }

  // Re test if the changed dont colide with other already defined workflows
  // Patch pre-test for duplicated
  const maybeDuplicateWf = cardWorkflowDefinitionChanges.reduce(
    (acc, w) =>
      workflowNames.includes(w.name)
        ? acc.concat({
            //@ts-ignore
            workflow: w,
            success: false,
            message: "Workflow already exists",
          })
        : acc,
    [],
  );

  const maybeDuplicateTasks = cardTaskDefinitions.reduce(
    (acc, t) =>
      taskNames.includes(t.name)
        ? acc.concat({
            // @ts-ignore
            task: t,
            success: false,
            message: "Task already exists",
          })
        : acc,
    [],
  );

  if (maybeDuplicateTasks.length > 0 || maybeDuplicateWf.length > 0) {
    return {
      importWorkflowResults: maybeDuplicateWf,
      importTaskResults: maybeDuplicateTasks,
      importUserFormResults: [],
      importSchemaResults: [],
      importIntegrationAndModelResults: [],
      importPromptsResult: [],
    };
  }
  // Build the replacement operations for workflows
  const workflowsChangesOperationsBeforeImport = _zip<WorkflowDef, WorkflowDef>(
    cardWorkflowDefinitions,
    cardWorkflowDefinitionChanges,
  ).map(
    ([ow, cw]) =>
      (w: Record<string | number, unknown>) =>
        replaceValues(w, ow!.name, cw!.name),
  );

  // Apply the replacement operations to the workflows
  const workflowResults = cardWorkflowDefinitions.map(
    _flow(workflowsChangesOperationsBeforeImport),
  );

  /// Integrations handling;
  const [
    importWorkflowResults = [],
    importTaskResults = [],
    importUserFormResults = [],
    importSchemaResults = [],
    importIntegrationAndModelResults = [],
  ] = await Promise.all([
    Promise.all(
      workflowResults.map((workflowDefinition: WorkflowDef) =>
        importWorkflow(context, workflowDefinition),
      ),
    ),
    Promise.all(
      cardTaskDefinitions.map((taskDefinition: CommonTaskDef) =>
        importTask(context, taskDefinition),
      ),
    ),
    Promise.all(
      cardUserForms.map((userFormDefinition: HumanTemplate) =>
        importUserForm(context, userFormDefinition, []),
      ),
    ),
    Promise.all(
      cardSchemas.map((schemasDefinition: SchemaDefinition) =>
        importSchemas(context, schemasDefinition, []),
      ),
    ),
    Promise.all(
      cardIntegrationsAndModels.map(
        (integrationAndModel: IntegrationAndModel) =>
          importIntegrationAndModel(
            context,
            integrationAndModel,
            existingIntegrations,
          ),
      ),
    ),
  ]);

  // Prompts require integrations to be imported first
  const importPromptsResult = await Promise.all(
    cardPrompts.map((prompt: PromptDef) => importPrompt(context, prompt)),
  );
  // Ask @Gulam why we needed this
  // const cacheQueryKey = [fetchContext.stack, WORKFLOW_METADATA_SHORT_URL];
  // queryClient.removeQueries(cacheQueryKey);

  return {
    importWorkflowResults,
    importTaskResults,
    importUserFormResults,
    importSchemaResults,
    importIntegrationAndModelResults,
    importPromptsResult,
  };
};
