import { Grid } from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { useSelector } from "@xstate/react";
import { FormMachineActionTypes } from "pages/definition/EditorPanel/TaskFormTab/state";
import { useContext } from "react";
import { updateField } from "utils/fieldHelpers";
import { TaskFormContext } from "../state";
import TaskFormSection from "./TaskFormSection";
import DurationWaitTaskForm from "./WaitTaskForm/DurationWaitTaskForm";

/**
 * Boundary timer settings for the selected task.
 *
 * The three fields sit on the WorkflowTask itself rather than inside its taskDefinition,
 * because a boundary timer applies to any task in a workflow — including WAIT, HUMAN and the
 * other system tasks, which have no registered definition to hang one off.
 */

const DEFAULT_STATUS = "COMPLETED";

const COMPLETION_STATUSES = [
  { label: "Completed", value: "COMPLETED" },
  { label: "Failed", value: "FAILED" },
  { label: "Completed with Errors", value: "COMPLETED_WITH_ERRORS" },
];

const BoundaryTimerSection = () => {
  const { formTaskActor } = useContext(TaskFormContext);
  const task = useSelector(
    formTaskActor!,
    (state) => state.context.taskChanges,
  );

  const duration: string = task?.boundaryTimerDuration ?? "";
  const completionStatus: string =
    task?.boundaryTimerCompletionStatus ?? DEFAULT_STATUS;

  const rawOutput = task?.boundaryTimerOutput;
  const output: string =
    rawOutput && Object.keys(rawOutput).length > 0
      ? JSON.stringify(rawOutput, null, 2)
      : "";

  const sendUpdate = (path: string, val: any) => {
    formTaskActor!.send({
      type: FormMachineActionTypes.UPDATE_TASK,
      taskChanges: updateField(path, val || undefined, task),
    });
  };

  const handleOutputChange = (val: string) => {
    try {
      sendUpdate("boundaryTimerOutput", JSON.parse(val));
    } catch {
      // Mid-edit the text is not valid JSON yet; keep the last parsed value.
    }
  };

  const handleDurationChange = (val: string) => {
    let changes = updateField("boundaryTimerDuration", val || undefined, task);
    // The dropdown shows COMPLETED whether or not one was chosen, so write it alongside the
    // duration that gives it meaning — otherwise the definition is silent on a status the
    // form is displaying as selected. Clearing the duration leaves it be: the duration fields
    // emit on every keystroke, and dropping the rest mid-edit would discard a typed output.
    if (val && !task?.boundaryTimerCompletionStatus) {
      changes = updateField(
        "boundaryTimerCompletionStatus",
        DEFAULT_STATUS,
        changes,
      );
    }
    formTaskActor!.send({
      type: FormMachineActionTypes.UPDATE_TASK,
      taskChanges: changes,
    });
  };

  return (
    <TaskFormSection title="Boundary Timer">
      <MuiTypography fontSize={12} opacity={0.6} mb={3}>
        Stop waiting for this task once the duration passes and settle it on the
        status below. Unlike a timeout it does not retry or time the task out,
        so the workflow carries on through its normal path. Leave the duration
        empty to wait indefinitely.
      </MuiTypography>
      <Grid container sx={{ width: "100%" }} spacing={3}>
        <Grid size={12}>
          <MuiTypography fontSize={12} mb={1}>
            Duration
          </MuiTypography>
          <DurationWaitTaskForm
            value={duration}
            onChange={handleDurationChange}
          />
        </Grid>
        <Grid size={12}>
          <ConductorSelect
            id="boundary-timer-completion-status"
            label="Completion status"
            fullWidth
            value={completionStatus}
            onChange={(event: any) =>
              sendUpdate("boundaryTimerCompletionStatus", event.target.value)
            }
            items={COMPLETION_STATUSES}
          />
        </Grid>
        <Grid size={12}>
          <ConductorCodeBlockInput
            label="Output (JSON)"
            language="json"
            value={output}
            onChange={handleOutputChange}
          />
        </Grid>
      </Grid>
    </TaskFormSection>
  );
};

export default BoundaryTimerSection;
