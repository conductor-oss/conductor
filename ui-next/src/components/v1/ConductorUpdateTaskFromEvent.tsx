import { FormControlLabel, Grid, Radio, RadioGroup } from "@mui/material";
import ConductorInput, {
  ConductorInputProps,
} from "components/v1/ConductorInput";
import _omit from "lodash/omit";
import { ComponentType, useMemo } from "react";

type EventTaskReferenceInput = { taskId: string };
type WorkflowTaskReferenceInput = { workflowId: string; taskRefName: string };
export type EventJson = Partial<
  EventTaskReferenceInput & WorkflowTaskReferenceInput
>;

interface FormWithRadioGroupProps {
  value: EventJson;
  onChange: (value: EventJson) => void;
  inputComponent?: ComponentType<ConductorInputProps>;
}

const omitTaskId = (value: EventJson) => _omit(value, "taskId");

const omitWorkflowID = (value: EventJson) =>
  _omit(value, ["workflowId", "taskRefName"]);

const _isTaskIdSelected = (value: EventJson) => value?.taskId != null;

export const ConductorUpdateTaskFormEvent = ({
  value,
  onChange,
  inputComponent: InputComponent = ConductorInput,
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
          label="Task Id"
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
            onTextInputChange={(val: string) =>
              onChange(omitWorkflowID({ ...value, taskId: val }))
            }
          />
        </Grid>
      ) : (
        <Grid container sx={{ width: "100%" }} spacing={4}>
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
              onTextInputChange={(val: string) =>
                onChange(omitTaskId({ ...value, workflowId: val }))
              }
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
              onTextInputChange={(val: string) =>
                onChange(omitTaskId({ ...value, taskRefName: val }))
              }
            />
          </Grid>
        </Grid>
      )}
    </>
  );
};
