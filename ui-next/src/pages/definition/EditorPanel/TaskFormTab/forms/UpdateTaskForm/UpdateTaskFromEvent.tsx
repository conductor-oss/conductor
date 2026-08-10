import { FormControlLabel, Grid, Radio, RadioGroup } from "@mui/material";
import _omit from "lodash/omit";
import React, { useMemo } from "react";

import ConductorInput, {
  ConductorInputProps,
} from "../../../../../../components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariablesProps } from "components/FlatMapForm/ConductorAutocompleteVariables";

type EventTaskReferenceInput = { taskId: string };
type WorkflowTaskReferenceInput = { workflowId: string; taskRefName: string };
export type EventJson = Partial<
  EventTaskReferenceInput & WorkflowTaskReferenceInput
>;
export type AnInputComponent = React.FunctionComponent<
  ConductorInputProps | ConductorAutocompleteVariablesProps
>;

interface FormWithRadioGroupProps {
  value: EventJson;
  onChange: (value: EventJson) => void;
  inputComponent?: AnInputComponent;
}

const omitTaskId = (value: EventJson) => _omit(value, "taskId");

const omitWorkflowID = (value: EventJson) =>
  _omit(value, ["workflowId", "taskRefName"]);

const _isTaskIdSelected = (value: EventJson) => value?.taskId != null;

export const UpdateTaskFormEvent = ({
  value,
  onChange,
  inputComponent: InputComponent = ConductorInput as AnInputComponent,
}: FormWithRadioGroupProps) => {
  const isTaskIdSelected = useMemo(() => _isTaskIdSelected(value), [value]);

  return (
    <>
      <RadioGroup
        sx={{ color: "#767676", ">label >span": { fontWeight: 600, mb: 2 } }}
        name="refresh-radio-group-options"
        row
        value={isTaskIdSelected ? "task-id" : "workflow-id-task-ref"}
        onChange={(event) => {
          if (event.target.value === "task-id") {
            onChange({
              taskId: value.taskId ?? "",
            });
          } else {
            onChange({
              workflowId: value.workflowId ?? "",
              taskRefName: value.taskRefName ?? "",
            });
          }
        }}
      >
        <FormControlLabel
          control={<Radio />}
          label="Workflow Id + Task Ref Name"
          value="workflow-id-task-ref"
          id="workflow-and-task-ref-radio-button"
        />
        <FormControlLabel
          control={<Radio />}
          label="Task ID"
          value="task-id"
          id="task-id-radio-button"
        />
      </RadioGroup>
      {isTaskIdSelected ? (
        <Grid size={12}>
          <InputComponent
            fullWidth
            label="Task ID"
            value={value?.taskId}
            onChange={(val: any) => {
              const newValue =
                typeof val === "string" ? val : val?.target?.value;

              onChange(omitWorkflowID({ ...value, taskId: newValue }));
            }}
          />
        </Grid>
      ) : (
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <InputComponent
              fullWidth
              label="Workflow ID"
              value={value?.workflowId}
              onChange={(val: any) => {
                const newValue =
                  typeof val === "string" ? val : val?.target?.value;

                onChange(omitTaskId({ ...value, workflowId: newValue }));
              }}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <InputComponent
              fullWidth
              label="Task reference name"
              value={value?.taskRefName}
              onChange={(val: any) => {
                const newValue =
                  typeof val === "string" ? val : val?.target?.value;

                onChange(omitTaskId({ ...value, taskRefName: newValue }));
              }}
            />
          </Grid>
        </Grid>
      )}
    </>
  );
};
