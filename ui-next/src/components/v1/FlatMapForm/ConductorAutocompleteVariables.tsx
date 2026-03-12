import {
  Autocomplete,
  AutocompleteRenderOptionState,
  InputLabelProps,
  Popper,
} from "@mui/material";
import { SxProps } from "@mui/system";
import { useSelector } from "@xstate/react";
import match from "autosuggest-highlight/match";
import parse from "autosuggest-highlight/parse";
import ConductorInput, {
  ConductorInputProps,
} from "components/v1/ConductorInput";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import _initial from "lodash/initial";
import _isNil from "lodash/isNil";
import { TaskFormContext } from "pages/definition/EditorPanel/TaskFormTab/state";
import { WorkflowMetadataContext } from "pages/definition/WorkflowMetadata/state";
import {
  DefinitionMachineContext,
  WorkflowEditContext,
} from "pages/definition/state";
import { useGetVariablesForSelectedTasks } from "pages/definition/state/useGetVariablesForSelectedTasks";
import {
  CSSProperties,
  FunctionComponent,
  HTMLAttributes,
  KeyboardEvent,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { autocompleteStyle } from "shared/styles";
import { CoerceToType, TaskDef } from "types/common";
import { DEFAULT_WF_ATTRIBUTES } from "utils/constants";
import { checkCoerceTypeError } from "utils/helpers";
import { ActorRef, State } from "xstate";
import { customFilterOptions, VARIABLE_REGEX } from "./formOptions";

type CohercesToNumber = "integer" | "double";

type TypeCohersionNumber = {
  onChange: (change: number) => void;
  coerceTo: CohercesToNumber;
};
type TypeCohersionString = {
  onChange: (change: string) => void;
  coerceTo?: "string";
};
type TypeCohersion = TypeCohersionNumber | TypeCohersionString;

export type ConductorAutocompleteVariablesProps = {
  label?: string | ReactNode;
  value?: string | number;
  fullWidth?: boolean;
  placeholder?: string;
  helperText?: string;
  taskBranches?: TaskDef[];
  workflowTasks?: TaskDef[];
  workflowInputParameters?: string[];
  actor?: ActorRef<any>;
  otherOptions?: string[] | number[];
  openOnFocus?: boolean;
  secrets?: string[];
  envs?: string[];
  InputLabelProps?: InputLabelProps;
  sxInput?: SxProps;
  onFocus?: () => void;
  growPopper?: boolean;
  workflowActor?: ActorRef<any>;
  onKeyDown?: (value: KeyboardEvent<HTMLInputElement>) => void;
  error?: boolean;
  id?: string;
  inputProps?: ConductorInputProps;
  required?: boolean;
  multiline?: boolean;
  variables?: string[];
  disabled?: boolean;
  onInputChange?: (val: any) => void;
  onBlur?: (val: string) => void;
  renderOption?: (
    props: HTMLAttributes<HTMLLIElement>,
    option: string | number,
    state: AutocompleteRenderOptionState,
  ) => ReactNode;
  getOptionLabel?: (option: string | number) => string;
} & TypeCohersion;

const assertOnChangeNumber = (
  onChange: any,
  coerceTo?: CoerceToType,
): onChange is (n: number) => void => {
  return coerceTo != null && ["integer", "double"].includes(coerceTo!);
};

const assertOnChangeString = (
  onChange: any,
  coerceTo?: CoerceToType,
): onChange is (n: string) => void => {
  return coerceTo == null || ["string"].includes(coerceTo!);
};

const replaceLastrDolarWithValue = (currentValue: string, newValue: string) => {
  return currentValue.slice(0, -1) + newValue;
};

const ConductorAutocompleteVariablesNoContext = ({
  onChange,
  label,
  value = "",
  fullWidth = true,
  required = false,
  placeholder,
  helperText,
  taskBranches = [],
  workflowTasks = [],
  workflowInputParameters = [],
  otherOptions = [],
  openOnFocus = false,
  secrets = [],
  envs = [],
  InputLabelProps,
  sxInput,
  coerceTo = "string",
  onFocus,
  growPopper,
  onKeyDown,
  error = false,
  id,
  inputProps,
  multiline = false,
  variables = [],
  disabled,
  onInputChange,
  onBlur,
  renderOption,
  getOptionLabel: customGetOptionLabel,
}: ConductorAutocompleteVariablesProps) => {
  const inputRef = useRef<HTMLTextAreaElement | HTMLInputElement>(null);
  const [expandField, setExpandField] = useState(false);

  useEffect(() => {
    if (expandField && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.selectionStart = inputRef.current.value.length;
      inputRef.current.selectionEnd = inputRef.current.value.length;
    }
  }, [expandField]);

  const options = useMemo(() => {
    const taskOptions = _initial<TaskDef>(taskBranches!).reduce(
      (result, task: TaskDef) => {
        if (task) {
          const { taskReferenceName } = task;

          return [...result, `\${${taskReferenceName}.output}`];
        }

        return result;
      },
      [] as string[],
    );

    const workflowTaskOptions = workflowTasks?.map(
      (task: TaskDef) => `\${${task.taskReferenceName}.output}`,
    );

    const taskJoinOn = _initial<TaskDef>(taskBranches!).reduce(
      (result, task: TaskDef) => {
        if (
          task &&
          task.type === "JOIN" &&
          task.joinOn &&
          task.joinOn.length > 0
        ) {
          const { joinOn } = task;
          const joinOnSpread = joinOn.map((item) => `\${${item}.output}`);
          return [...result, ...joinOnSpread];
        }

        return result;
      },
      [] as string[],
    );

    const workflowInputOptions = workflowInputParameters?.map(
      (ip: string) => "${workflow.input." + ip + "}",
    );

    const secretNamesOptions = secrets?.map(
      (name: string) => "${workflow.secrets." + name + "}",
    );

    const envNameOptions = envs?.map((n) => "${workflow.env." + n + "}");

    const variableOptions = variables?.map(
      (name: string) => "${workflow.variables." + name + "}",
    );

    const workflowAttributes = DEFAULT_WF_ATTRIBUTES?.map(
      (item) => `\${${item}}`,
    );
    return (otherOptions as string[])
      .concat(`\${workflow.input}`)
      .concat(`\${workflow.secrets}`)
      .concat(`\${workflow.env}`)
      .concat(
        workflowTaskOptions,
        taskOptions,
        taskJoinOn,
        workflowInputOptions,
        workflowAttributes,
        secretNamesOptions,
        envNameOptions,
        variableOptions,
      );
  }, [
    taskBranches,
    workflowTasks,
    workflowInputParameters,
    secrets,
    otherOptions,
    envs,
    variables,
  ]);

  const isErrorValue = useMemo<boolean>(
    () => checkCoerceTypeError({ value, coerceTo }),
    [value, coerceTo],
  );

  const popperStyle = (style: CSSProperties | undefined) => {
    return growPopper ? {} : style;
  };

  return (
    <Autocomplete
      id={id}
      PopperComponent={(props) => (
        <Popper {...props} style={popperStyle(props.style)} />
      )}
      filterOptions={(options, { inputValue }) =>
        customFilterOptions(options, inputValue)
      }
      renderInput={(params) => (
        <ConductorInput
          {...params}
          {...inputProps}
          label={label}
          fullWidth={fullWidth}
          required={required}
          placeholder={placeholder}
          helperText={helperText}
          InputLabelProps={InputLabelProps}
          sx={sxInput}
          error={isErrorValue || error}
          onKeyDown={onKeyDown}
          multiline={expandField}
          inputRef={inputRef}
          InputProps={params.InputProps}
          disabled={disabled}
        />
      )}
      value={value?.toString() ?? ""}
      freeSolo
      disabled={disabled}
      onFocus={() => {
        onFocus?.();
        if (!multiline) {
          setExpandField(true);
        }
      }}
      onBlur={() => {
        if (!multiline) {
          setExpandField(false);
        }
        if (onBlur && inputRef?.current && inputRef?.current?.value) {
          onBlur(inputRef?.current?.value);
          return;
        }
      }}
      openOnFocus={openOnFocus}
      autoComplete
      onChange={(a, b) => {
        if (!_isNil(b) && b !== value) {
          if (assertOnChangeNumber(onChange, coerceTo) && !isNaN(b as any)) {
            onChange(Number(b));
          } else if (assertOnChangeString(onChange, coerceTo)) {
            if (typeof b === "string") {
              const newValue = b as string;
              const currentValue = value.toString();
              if (currentValue.endsWith("$")) {
                onChange(replaceLastrDolarWithValue(currentValue, newValue));
              } else if (VARIABLE_REGEX.test(currentValue)) {
                onChange(currentValue.replace(VARIABLE_REGEX, newValue));
              } else {
                onChange(newValue);
              }
            } else {
              onChange(b);
            }
          }
        }
      }}
      onInputChange={(event: any, o) => {
        if (onInputChange) {
          onInputChange(o);
          return;
        }
        if (o !== value) {
          if (assertOnChangeNumber(onChange, coerceTo) && !isNaN(o as any)) {
            onChange(Number(o));
          } else if (assertOnChangeString(onChange, coerceTo)) {
            onChange(String(o));
          } else {
            (onChange as (val: unknown) => void)(o);
          }
        }
      }}
      getOptionLabel={
        customGetOptionLabel
          ? customGetOptionLabel
          : (option: string | number) => {
              if (typeof option === "number") {
                return option.toString();
              }
              return option;
            }
      }
      renderOption={
        renderOption
          ? renderOption
          : (props, option, { inputValue }) => {
              const matches = match(option as string, inputValue);
              const parts = parse(option as string, matches);

              return (
                <li {...props}>
                  <div>
                    {parts.map((part, index) => (
                      <span
                        key={index}
                        style={{
                          fontWeight: part.highlight ? 700 : 400,
                        }}
                      >
                        {part.text}
                      </span>
                    ))}
                  </div>
                </li>
              );
            }
      }
      options={options}
      sx={[
        autocompleteStyle({ value }),
        // Add padding for clear icon when multiline to prevent text overlap
        (expandField || multiline) && value
          ? {
              "& .MuiTextField-root .MuiOutlinedInput-root.MuiInputBase-root": {
                paddingRight: "48px",
              },
              "& .MuiTextField-root .MuiInputBase-multiline.MuiOutlinedInput-root":
                {
                  paddingRight: "48px",
                },
              "& .MuiTextField-root textarea.MuiInputBase-input.MuiOutlinedInput-input":
                {
                  paddingRight: "0px",
                },
            }
          : null,
      ]}
      clearIcon={<XCloseIcon />}
    />
  );
};

const AutocompleteVariablesWithFormContext: FunctionComponent<
  ConductorAutocompleteVariablesProps
> = ({ actor: formTaskActor, workflowActor, ...restProps }) => {
  const taskBranches = useSelector(
    formTaskActor!,
    (current) => current.context.tasksBranch,
  );

  const workflowInputParameters = useSelector(
    formTaskActor!,
    (current) => current.context.workflowInputParameters,
  );
  // fetching secrets from context
  const secrets = useSelector(workflowActor!, (state) => state.context.secrets);

  const workflowVariableInputs = useGetVariablesForSelectedTasks(workflowActor);

  const envsObj =
    useSelector(
      workflowActor!,
      (state: State<DefinitionMachineContext>) => state.context?.envs,
    ) || {};
  // refining secrets with just secretNames
  const secretNames =
    secrets && secrets.length > 0 ? secrets.map((item: any) => item?.name) : [];
  return (
    <ConductorAutocompleteVariablesNoContext
      {...restProps}
      taskBranches={taskBranches}
      workflowInputParameters={workflowInputParameters}
      secrets={secretNames}
      variables={workflowVariableInputs}
      envs={Object.keys(envsObj)}
    />
  );
};

const AutocompleteVariablesWithWorkflowMetadataContext: FunctionComponent<
  ConductorAutocompleteVariablesProps
> = ({ actor: metadataActor, workflowActor, ...restProps }) => {
  const taskBranches: TaskDef[] = [];
  const workflowTasks = useSelector(
    metadataActor!,
    (current) => current.context.metadataChanges.tasks,
  );
  const workflowInputParameters = useSelector(
    metadataActor!,
    (current) => current.context.metadataChanges.inputParameters,
  );
  // fetching secrets from context
  const secrets = useSelector(workflowActor!, (state) => state.context.secrets);

  const workflowVariableInputs = useGetVariablesForSelectedTasks(workflowActor);

  const envsObj =
    useSelector(
      workflowActor!,
      (state: State<DefinitionMachineContext>) => state.context?.envs,
    ) || {};

  // refining secrets with just secretNames
  const secretNames =
    secrets && secrets.length > 0 ? secrets.map((item: any) => item?.name) : [];

  const envsOptions = Object.keys(envsObj);

  return (
    <ConductorAutocompleteVariablesNoContext
      {...restProps}
      taskBranches={taskBranches}
      workflowInputParameters={workflowInputParameters}
      envs={envsOptions}
      secrets={secretNames}
      variables={workflowVariableInputs}
      workflowTasks={workflowTasks}
    />
  );
};

export const ConductorAutocompleteVariables: FunctionComponent<
  ConductorAutocompleteVariablesProps
> = (props) => {
  const { formTaskActor } = useContext(TaskFormContext);
  const { workflowMetadataActor } = useContext(WorkflowMetadataContext);
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);

  if (!_isNil(formTaskActor) && !_isNil(workflowDefinitionActor)) {
    return (
      <AutocompleteVariablesWithFormContext
        {...props}
        actor={formTaskActor}
        workflowActor={workflowDefinitionActor}
      />
    );
  } else if (
    !_isNil(WorkflowMetadataContext) &&
    !_isNil(workflowDefinitionActor)
  ) {
    return (
      <AutocompleteVariablesWithWorkflowMetadataContext
        {...props}
        actor={workflowMetadataActor!}
        workflowActor={workflowDefinitionActor}
      />
    );
  }
  return <ConductorAutocompleteVariablesNoContext {...props} />;
};
