import { Monaco } from "@monaco-editor/react";
import _path from "lodash/fp/path";
import _update from "lodash/fp/update";
import _keys from "lodash/keys";
import _nth from "lodash/nth";
import { IdempotencyValuesProp } from "pages/definition/RunWorkflow/state";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { MutableRefObject } from "react";
import {
  DoWhileTaskDef,
  InlineTaskDef,
  JDBCTaskDef,
  SwitchTaskDef,
} from "types/TaskType";
import { AuthHeaders, TaskDef, TaskType } from "types/common";
import { logger } from "utils/logger";
import { mock } from "mock-json-schema";
import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { JsonSchema } from "@jsonforms/core";

const VARIABLE_DEFINER = "$.";
const IDLE_MINIMUM_VALUE_IF_FAIL_TO_GET_REF = 500;
const A_MARGIN_THREASHHOLD = 22;

export type OnlyTheWordInfoProp = {
  word: string;
  startColumn: number;
  endColumn: number;
};

export const editorAddCommandAltEnter = (
  editor: Monaco,
  monaco: Monaco,
  taskRef: MutableRefObject<
    | Partial<InlineTaskDef>
    | Partial<DoWhileTaskDef>
    | Partial<SwitchTaskDef>
    | Partial<JDBCTaskDef>
    | null
  >,
  callBack: (onlyTheWordInfo: OnlyTheWordInfoProp) => void,
) => {
  return editor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.Enter, () => {
    const position = editor.getPosition(); // Get the current cursor position
    const model = editor.getModel();

    if (model) {
      const onlyTheWordInfo: OnlyTheWordInfoProp =
        model.getWordAtPosition(position); // This only selects the word

      const startColumn = onlyTheWordInfo?.startColumn;
      if (startColumn > VARIABLE_DEFINER.length) {
        // Avoid blowing up because of wrong position.
        const newStart = Math.max(startColumn - VARIABLE_DEFINER.length, 1); // We select a new start
        let word = null;
        // Create a new range from th new start including $.
        const wordRange = new monaco.Range(
          position.lineNumber,
          newStart,
          position.lineNumber,
          onlyTheWordInfo.endColumn,
        );
        word = model.getValueInRange(wordRange);
        if (word && word?.includes(VARIABLE_DEFINER)) {
          const maybeNewVariable = word.word;
          const currentVariables = _keys(
            taskRef.current?.inputParameters || {},
          );

          if (!currentVariables.includes(maybeNewVariable)) {
            callBack(onlyTheWordInfo);
          }
        }
      }
    }
  });
};

export const editorHandleAutoSize = (
  editor: Monaco,
  parentWrapperRef: MutableRefObject<Monaco>,
) => {
  //auto scrolling according to the content height => https://github.com/microsoft/monaco-editor/issues/794#issuecomment-688959283
  const updateHeight = () => {
    const contentHeight = Math.min(1000, editor.getContentHeight());
    let contentWidth = IDLE_MINIMUM_VALUE_IF_FAIL_TO_GET_REF;

    if (parentWrapperRef) {
      contentWidth = parentWrapperRef.current.getBoundingClientRect().width;
    }
    try {
      editor.layout({
        width: contentWidth - A_MARGIN_THREASHHOLD,
        height: contentHeight,
      });
    } catch {
      /* empty */
    }
  };
  editor.onDidContentSizeChange(updateHeight);
  updateHeight();
};

export const editorDecorations = (
  model: Monaco,
  parameters: string[],
  monaco: Monaco,
) => {
  return parameters.map((word: string) => {
    const escapedWord = word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const wordRegex = new RegExp(
      `${escapedWord}(?![a-zA-Z0-9_$])(?:\\.\\w+|\\['\\w+'\\]|\\[\\w+\\])*`,
      "g",
    );
    let match;
    const decorators = [];
    while ((match = wordRegex.exec(model.getValue()))) {
      const startPos = model.getPositionAt(match.index);
      const endPos = model.getPositionAt(match.index + match[0].length);

      if (startPos && endPos) {
        decorators.push({
          range: new monaco.Range(
            startPos.lineNumber,
            startPos.column,
            endPos.lineNumber,
            endPos.column,
          ),
          options: {
            className: "squiggly-error",
          },
        });
      }
    }
    return decorators;
  });
};

export const updateInputParametersCommon = async (
  taskJson: Partial<TaskDef>,
  originalTask: Partial<TaskDef>,
  authHeaders: AuthHeaders,
  onChange: (data: Partial<TaskDef>) => void,
  workflowNameVersionStringPath: string,
  inputParametersStringPath: string,
  taskType: TaskType.START_WORKFLOW | TaskType.SUB_WORKFLOW,
  getWorkflowDefinitionByNameAndVersionFn: ({
    name,
    version,
    authHeaders,
  }: {
    name: string;
    version: number;
    authHeaders: AuthHeaders;
  }) => Promise<any>,
) => {
  const wfName = _path(`${workflowNameVersionStringPath}.name`, taskJson) || "";
  const wfVersion =
    _path(`${workflowNameVersionStringPath}.version`, taskJson) || "";

  if (
    (wfName !==
      (_path(`${workflowNameVersionStringPath}.name`, originalTask) || "") ||
      wfVersion !==
        (_path(`${workflowNameVersionStringPath}.version`, originalTask) ||
          "")) &&
    [wfName, wfVersion].every(
      (val) =>
        val != null && String(val).trim() !== "" && !String(val).includes("$"),
    )
  ) {
    try {
      const workflowDef = await getWorkflowDefinitionByNameAndVersionFn({
        name: wfName,
        version: wfVersion as number,
        authHeaders,
      });
      const entries = workflowDef?.inputParameters.map((value: string) => [
        value,
        _path(`${inputParametersStringPath}.${[value]}`, taskJson) || "",
      ]);

      if (entries && entries.length > 0) {
        const inputParams = Object.fromEntries(entries);

        const payloadForStartWorkflow = {
          ...taskJson.inputParameters,
          startWorkflow: {
            ...taskJson.inputParameters?.startWorkflow,
            input: inputParams,
          },
        };
        const payload =
          taskType === TaskType.START_WORKFLOW
            ? payloadForStartWorkflow
            : inputParams;

        onChange({
          ...taskJson,
          inputParameters: payload,
        });
      } else {
        onChange({ ...taskJson });
      }
    } catch (error) {
      logger.error(error);
      return;
    }
  } else {
    onChange({ ...taskJson });
  }
};

export const handleChangeIdempotencyValues = (
  data: IdempotencyValuesProp,
  task: Partial<TaskDef>,
  path: string,
  onChange: (task: Partial<TaskDef>) => void,
) => {
  const idempotencyStrategy = () => {
    if (!data?.idempotencyKey && task.type !== TaskType.SUB_WORKFLOW) {
      return undefined;
    }
    if (data.idempotencyStrategy) {
      return data.idempotencyStrategy;
    }
    if (_path(`${path}.idempotencyStrategy`, task)) {
      return _path(`${path}.idempotencyStrategy`, task);
    }
    return IdempotencyStrategyEnum.RETURN_EXISTING;
  };

  const maybeIdempotencyStrategy = idempotencyStrategy();
  const maybeIdempotencyKey =
    data?.idempotencyKey === "" ? undefined : data?.idempotencyKey;

  const taskJson = { ...task };

  const updatedTask = _update(
    path,
    (item) => ({
      ...item,
      idempotencyKey: maybeIdempotencyKey,
      idempotencyStrategy: maybeIdempotencyStrategy,
    }),
    taskJson,
  );

  onChange({ ...updatedTask });
};

export const getCorrespondingJoinTask = (
  originalTask: Partial<TaskDef>,
  tasksList: Partial<TaskDef>[] = [],
) => {
  if (originalTask && tasksList && tasksList.length > 0) {
    const taskIndex = tasksList.findIndex(
      (item) => item?.taskReferenceName === originalTask?.taskReferenceName,
    );
    if (taskIndex > -1) {
      const nextTask = _nth(tasksList, taskIndex + 1);
      if (nextTask && nextTask.type === TaskType.JOIN) {
        return [nextTask];
      }
      return [];
    }
    return [];
  }
  return [];
};

const fetchContext = fetchContextNonHook();

/**
 * Fetches a schema by name and version, then generates default values from it
 * @param schemaName - The name of the schema
 * @param schemaVersion - The version of the schema (optional)
 * @param authHeaders - Authentication headers for the API request
 * @returns Promise that resolves to default values object, or null if fetching/generation fails
 */
export const getDefaultValuesFromSchema = async (
  schemaName: string,
  schemaVersion: number | undefined,
  authHeaders: AuthHeaders,
): Promise<Record<string, unknown> | null> => {
  if (!schemaName) {
    return null;
  }

  try {
    const url = `/schema/${schemaName}${schemaVersion ? `/${schemaVersion}` : ""}`;

    const response = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers: authHeaders }),
    );

    if (response?.data) {
      const defaultValues = mock(response.data as JsonSchema);
      if (defaultValues && Object.keys(defaultValues).length > 0) {
        return defaultValues as Record<string, unknown>;
      }
    }
    return null;
  } catch (error) {
    logger.warn("Failed to fetch schema for default values:", error);
    return null;
  }
};

/**
 * Checks if inputParameters should be populated from schema and returns default values if conditions are met
 * @param newSchema - The new schema form value
 * @param currentTask - The current task definition
 * @param authHeaders - Authentication headers for the API request
 * @returns Promise that resolves to default values object if conditions are met, or null otherwise
 */
export const getInputParametersFromSchemaIfNeeded = async (
  newSchema: { inputSchema?: { name?: string; version?: number } } | undefined,
  currentTask: Partial<TaskDef> | undefined,
  authHeaders: AuthHeaders,
): Promise<Record<string, unknown> | null> => {
  // Check if inputSchema is being updated and inputParameters is empty
  const hasInputSchema = newSchema?.inputSchema?.name;
  const inputSchemaChanged =
    newSchema?.inputSchema?.name !==
      currentTask?.taskDefinition?.inputSchema?.name ||
    newSchema?.inputSchema?.version !==
      currentTask?.taskDefinition?.inputSchema?.version;

  if (hasInputSchema && inputSchemaChanged && newSchema?.inputSchema?.name) {
    return await getDefaultValuesFromSchema(
      newSchema.inputSchema.name,
      newSchema.inputSchema.version,
      authHeaders,
    );
  }

  return null;
};
