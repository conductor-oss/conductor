import { FormControlLabel, Grid, Link, Switch } from "@mui/material";
import { useSelector } from "@xstate/react";
import MuiTypography from "components/MuiTypography";
import PromptVariables from "components/PromptVariables";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { path as _path, clone, setWith } from "lodash/fp";
import { ConductorValueInput } from "pages/definition/EditorPanel/TaskFormTab/forms/ConductorValueInput";
import { ConductorArrayMapFormBase } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields/ConductorArrayMapForm";
import {
  LLMFormFieldsEvents,
  LLMFormFieldsMachineContext,
  LLMFormFieldsMachineEventTypes,
} from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields/state";
import { useGetSetHandler } from "pages/definition/EditorPanel/TaskFormTab/forms/useGetSetHandler";
import { FunctionComponent, useMemo } from "react";
import { TaskDef } from "types/common";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { ActorRef, State } from "xstate";
import { MEDIA_TYPE_SUGGESTIONS } from "./constants/httpSuggestions";

export type FieldComponentType = FunctionComponent<{
  onChange: (t: Partial<TaskDef>) => void;
  actor: ActorRef<LLMFormFieldsEvents>;
  task: Partial<TaskDef>;
}>;

const DEFAULT_VALUES_FOR_ARRAY = { object: [] };

// this was root of many issues. though fixed i would still get rid of JSONField
export const updateField = (path: string, value: any, taskJson: any) => {
  return setWith(clone, path, value, clone(taskJson));
};

const fieldValue = (task: Partial<TaskDef>, type: string) => {
  const value = _path(`inputParameters.${type}`, task);
  if (value != null) {
    return value;
  } else if (
    type === UiIntegrationsFieldType.STOP_WORDS ||
    type === UiIntegrationsFieldType.EMBEDDINGS
  ) {
    return [];
  } else return "";
};

const useSetterGetter = (
  type: UiIntegrationsFieldType,
  task: Partial<TaskDef>,
) => [
  (value: unknown) => updateField(`inputParameters.${type}`, value, task),
  fieldValue(task, type),
];

const aiFieldTypes = {
  [UiIntegrationsFieldType.LLM_PROVIDER]: ({ onChange, task, actor }) => {
    const options = useSelector(actor, (state) =>
      state.context.llmProviderOptions.map(
        ({ name }: { name: string }) => name, // Fix types
      ),
    );
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.LLM_PROVIDER,
      task,
    );
    return (
      <>
        <ConductorAutocompleteVariables
          onChange={(value) => onChange(setValue(value))}
          value={ipValue}
          otherOptions={options}
          label="LLM provider"
          onFocus={() =>
            actor.send({
              type: LLMFormFieldsMachineEventTypes.FOCUS_LLM_PROVIDER,
              task,
            })
          }
        />
      </>
    );
  },
  [UiIntegrationsFieldType.MODEL]: ({ onChange, task, actor }) => {
    const options = useSelector(actor, (state) =>
      state.context.modelOptions.map(
        ({ api }: { api: string }) => api, // Fix types
      ),
    );
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.MODEL,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        otherOptions={options}
        label="Model"
        onFocus={() =>
          actor.send({
            type: LLMFormFieldsMachineEventTypes.FOCUS_MODEL,
            task,
          })
        }
      />
    );
  },
  [UiIntegrationsFieldType.PROMPT_NAME]: ({ onChange, task, actor }) => {
    const promptNames = useSelector(
      actor,
      (state) => state.context.promptNameOptions,
    );
    const [options] = useMemo(() => {
      return [
        promptNames.map(
          ({ name }: { name: string }) => name, // Fix types
        ),
      ];
    }, [promptNames]);

    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.PROMPT_NAME,
      task,
    );

    const currentVariables = task.inputParameters?.promptVariables || {};
    return (
      <Grid container spacing={3} sx={{ width: "100%" }}>
        <Grid size={6}>
          <MuiTypography sx={{ opacity: 0.5, mb: 3 }}>
            Enter a saved AI Prompt name or{" "}
            <Link
              sx={{ fontWeight: 400 }}
              target="_blank"
              href="/ai_prompts/new_ai_prompt_model"
              rel="noreferrer"
            >
              create a new one.
            </Link>
          </MuiTypography>
          <ConductorAutocompleteVariables
            openOnFocus
            onChange={(value) => {
              actor.send({
                type: LLMFormFieldsMachineEventTypes.SELECT_PROMPT_NAME,
                task: setValue(value),
              });
            }}
            value={ipValue}
            otherOptions={options}
            label="Prompt Name"
            onFocus={() =>
              actor.send({
                type: LLMFormFieldsMachineEventTypes.FOCUS_PROMPT_NAMES,
                task,
              })
            }
          />
        </Grid>
        <MuiTypography sx={{ opacity: 0.5 }}>
          {`Variables to be used in the prompt (e.g. "What's the weather in {$userLocation}?").`}
        </MuiTypography>
        <PromptVariables
          currentVariables={currentVariables}
          onChange={onChange}
          updateField={updateField}
          task={task}
        />
      </Grid>
    );
  },
  [UiIntegrationsFieldType.TEXT]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.TEXT,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Text"
      />
    );
  },
  [UiIntegrationsFieldType.VECTOR_DB]: ({ onChange, task, actor }) => {
    const options = useSelector(actor, (state) =>
      state.context.vectorDbOptions.map(
        ({ name }: { name: string }) => name, // Fix types
      ),
    );
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.VECTOR_DB,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        InputLabelProps={{ style: { pointerEvents: "auto" } }}
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        otherOptions={options}
        onFocus={() =>
          actor.send({
            type: LLMFormFieldsMachineEventTypes.FOCUS_VECTORDB,
            task,
          })
        }
        label="Vector database"
        inputProps={{
          tooltip: {
            title: "Vector database",
            content: "Enter the vector database for this task",
          },
        }}
      />
    );
  },
  [UiIntegrationsFieldType.NAMESPACE]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.NAMESPACE,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        InputLabelProps={{ style: { pointerEvents: "auto" } }}
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Namespace"
        inputProps={{
          tooltip: {
            title: "Namespace",
            content: "Enter the namespace this task will utilize",
          },
        }}
      />
    );
  },
  [UiIntegrationsFieldType.QUERY]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.QUERY,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        fullWidth
        label="Query"
        value={ipValue}
        onChange={(value) => onChange(setValue(value))}
      />
    );
  },
  [UiIntegrationsFieldType.ID]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.ID,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        fullWidth
        label="Vector ID"
        value={ipValue}
        onChange={(value) => onChange(setValue(value))}
      />
    );
  },
  [UiIntegrationsFieldType.EMBEDDING_MODEL_PROVIDER]: ({
    onChange,
    task,
    actor,
  }) => {
    const options = useSelector(actor, (state) =>
      state.context.llmProviderOptions.map(
        ({ name }: { name: string }) => name, // Fix types
      ),
    );

    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.EMBEDDING_MODEL_PROVIDER,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        otherOptions={options}
        label="Embedding model provider"
        onFocus={() =>
          actor.send({
            type: LLMFormFieldsMachineEventTypes.FOCUS_LLM_PROVIDER,
            task,
          })
        }
      />
    );
  },
  [UiIntegrationsFieldType.EMBEDDING_MODEL]: ({ onChange, task, actor }) => {
    const options = useSelector(actor, (state) =>
      state.context.embeddingModelOptions.map(
        ({ api }: { api: string }) => api,
      ),
    );

    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.EMBEDDING_MODEL,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        onFocus={() =>
          actor.send({
            type: LLMFormFieldsMachineEventTypes.FOCUS_EMBEDDINGS_MODEL,
            task,
          })
        }
        otherOptions={options}
        label="Embedding model"
      />
    );
  },
  [UiIntegrationsFieldType.URL]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.URL,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="URL"
      />
    );
  },
  [UiIntegrationsFieldType.MEDIA_TYPE]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.MEDIA_TYPE,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        otherOptions={MEDIA_TYPE_SUGGESTIONS}
        label="Media type"
      />
    );
  },
  [UiIntegrationsFieldType.DOC_ID]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.DOC_ID,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Doc ID"
      />
    );
  },
  [UiIntegrationsFieldType.TEMPERATURE]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.TEMPERATURE,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Temperature"
        coerceTo="double"
      />
    );
  },
  [UiIntegrationsFieldType.TOP_P]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.TOP_P,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="TopP"
        coerceTo="double"
      />
    );
  },
  [UiIntegrationsFieldType.MAX_RESULTS]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.MAX_RESULTS,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Max Results"
        coerceTo="integer"
      />
    );
  },
  [UiIntegrationsFieldType.MAX_TOKENS]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.MAX_TOKENS,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        InputLabelProps={{ style: { pointerEvents: "auto" } }}
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        coerceTo="integer"
        label="Token limit"
        inputProps={{
          tooltip: {
            title: "Token limit",
            content:
              "Maximum no. of tokens to return as part of result (a token is approximately 4 characters)",
          },
        }}
      />
    );
  },
  [UiIntegrationsFieldType.STOP_WORDS]: ({ onChange, task }) => {
    const [stopWordsVal, handleStopWordsVal] = useGetSetHandler(
      { onChange, task },
      `inputParameters.stopWords`,
    );

    return (
      <ConductorValueInput
        valueLabel="Stop words"
        value={stopWordsVal}
        onChangeValue={(val) => {
          handleStopWordsVal(val);
        }}
        defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
      />
    );
  },
  [UiIntegrationsFieldType.INDEX]: ({ onChange, task, actor }) => {
    const options = useSelector(
      actor,
      (state: State<LLMFormFieldsMachineContext>) =>
        state.context.indexOptions.map(
          ({ api }: { api: string }) => api, // Fix types
        ),
    );

    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.INDEX,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        onFocus={() =>
          actor.send({
            type: LLMFormFieldsMachineEventTypes.FOCUS_INDEX,
            task,
          })
        }
        label="Index"
        otherOptions={options}
      />
    );
  },
  [UiIntegrationsFieldType.EMBEDDINGS]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.EMBEDDINGS,
      task,
    );

    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Embeddings"
      />
    );
  },
  [UiIntegrationsFieldType.CHUNK_SIZE]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.CHUNK_SIZE,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Chunk Size"
        coerceTo="integer"
      />
    );
  },
  [UiIntegrationsFieldType.CHUNK_OVERLAP]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.CHUNK_OVERLAP,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Chunk overlap"
        coerceTo="integer"
      />
    );
  },

  [UiIntegrationsFieldType.INSTRUCTIONS]: ({ onChange, task, actor }) => {
    const options = useSelector(
      actor,
      (state: State<LLMFormFieldsMachineContext>) =>
        state.context.promptNameOptions.map(({ name }) => name),
    );

    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.INSTRUCTIONS,
      task,
    );

    const currentVariables = task.inputParameters?.promptVariables || {};
    return (
      <Grid container spacing={3} sx={{ width: "100%" }}>
        <Grid size={12}>
          <MuiTypography sx={{ opacity: 0.5, mb: 3 }}>
            Enter a saved AI Prompt name or{" "}
            <Link
              sx={{ fontWeight: 400 }}
              target="_blank"
              href="/ai_prompts/new_ai_prompt_model"
              rel="noreferrer"
            >
              create a new one.
            </Link>
          </MuiTypography>
          <ConductorAutocompleteVariables
            value={ipValue}
            otherOptions={options}
            label="Prompt Name"
            onChange={(value) => {
              actor.send({
                type: LLMFormFieldsMachineEventTypes.SELECT_INSTRUCTIONS,
                task: setValue(value),
              });
            }}
            onFocus={() =>
              actor.send({
                type: LLMFormFieldsMachineEventTypes.FOCUS_PROMPT_NAMES,
                task,
              })
            }
            openOnFocus
          />
        </Grid>
        <MuiTypography sx={{ opacity: 0.5 }}>
          {`Variables to be used in the prompt (e.g. "What's the weather in {$userLocation}?").`}
        </MuiTypography>
        <PromptVariables
          currentVariables={currentVariables}
          onChange={onChange}
          updateField={updateField}
          task={task}
        />
      </Grid>
    );
  },
  [UiIntegrationsFieldType.MESSAGES]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.MESSAGES,
      task,
    );

    return (
      <>
        <MuiTypography pt={1} sx={{ opacity: 0.5 }} mb={3}>
          Messages with roles such as user, assistant, system, etc.
        </MuiTypography>
        <ConductorArrayMapFormBase
          value={ipValue}
          onChange={(value) => onChange(setValue(value))}
        />
      </>
    );
  },
  [UiIntegrationsFieldType.JSON_OUTPUT]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.JSON_OUTPUT,
      task,
    );

    return (
      <>
        <FormControlLabel
          control={
            <Switch
              color="primary"
              checked={!!ipValue}
              onChange={(event) => onChange(setValue(event.target.checked))}
            />
          }
          label="Enable JSON Output"
          sx={{
            "& .MuiFormControlLabel-label": {
              fontWeight: 600,
              color: "#767676",
            },
          }}
        />
        <MuiTypography pt={1} sx={{ opacity: 0.5 }}>
          When enabled, the LLM response will be parsed as JSON. This is useful
          when you expect the model to return structured data.
        </MuiTypography>
      </>
    );
  },
  [UiIntegrationsFieldType.DIMENSIONS]: ({ onChange, task }) => {
    const [setValue, ipValue] = useSetterGetter(
      UiIntegrationsFieldType.DIMENSIONS,
      task,
    );
    return (
      <ConductorAutocompleteVariables
        openOnFocus
        onChange={(value) => onChange(setValue(value))}
        value={ipValue}
        label="Dimensions"
        coerceTo="integer"
      />
    );
  },
} satisfies Record<UiIntegrationsFieldType, FieldComponentType>;

const aIFormField = (type: UiIntegrationsFieldType): FieldComponentType =>
  aiFieldTypes[type];

export const fieldsToFieldsFieldsComponents = (
  fields: UiIntegrationsFieldType[],
): Array<[UiIntegrationsFieldType, FieldComponentType]> =>
  fields.map((field) => [field, aIFormField(field)]);
