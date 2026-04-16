import { Grid } from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { useContext } from "react";
import { useSelector } from "@xstate/react";
import { FormMachineActionTypes } from "pages/definition/EditorPanel/TaskFormTab/state";
import { updateField } from "utils/fieldHelpers";
import { TaskFormContext } from "../state";
import TaskFormSection from "./TaskFormSection";
import DurationWaitTaskForm from "./WaitTaskForm/DurationWaitTaskForm";

const BOUNDARY_TIMER_STATUSES = [
  "COMPLETED",
  "FAILED",
  "COMPLETED_WITH_ERRORS",
];

const BoundaryTimerSection = () => {
  const { formTaskActor } = useContext(TaskFormContext);
  const task = useSelector(
    formTaskActor!,
    (state) => state.context.taskChanges,
  );

  const inputParameters = (task?.inputParameters as any) ?? {};
  const boundaryTimerDuration: string =
    inputParameters.boundaryTimerDuration ?? "";
  const completionStatus: string =
    inputParameters.boundaryTimerCompletionStatus ?? "";

  const rawOutput = inputParameters.boundaryTimerOutput;
  const boundaryTimerOutput: string = rawOutput
    ? typeof rawOutput === "string"
      ? rawOutput
      : JSON.stringify(rawOutput, null, 2)
    : "";

  const sendUpdate = (path: string, val: any) => {
    formTaskActor!.send({
      type: FormMachineActionTypes.UPDATE_TASK,
      taskChanges: updateField(path, val || undefined, task),
    });
  };

  const handleOutputChange = (val: string) => {
    let parsed: any = val;
    try {
      parsed = JSON.parse(val);
    } catch {
      // keep as string if not valid JSON yet
    }
    sendUpdate("inputParameters.boundaryTimerOutput", parsed);
  };

  return (
    <TaskFormSection title="Boundary Timer">
      <MuiTypography fontSize={12} opacity={0.6} mb={3}>
        Auto-complete this task with a configured status if it has not finished
        within the specified duration (e.g. "2 days", "48 hours", "1 days 4
        hours 30 minutes").
      </MuiTypography>
      <Grid container sx={{ width: "100%" }} spacing={3}>
        <Grid size={12}>
          <MuiTypography fontSize={12} mb={1}>
            Duration
          </MuiTypography>
          <DurationWaitTaskForm
            value={boundaryTimerDuration}
            onChange={(val) =>
              sendUpdate("inputParameters.boundaryTimerDuration", val)
            }
          />
        </Grid>
        <Grid size={12}>
          <ConductorAutocompleteVariables
            label="Completion Status"
            value={completionStatus}
            onChange={(val) =>
              sendUpdate("inputParameters.boundaryTimerCompletionStatus", val)
            }
            otherOptions={BOUNDARY_TIMER_STATUSES}
          />
        </Grid>
        <Grid size={12}>
          <ConductorCodeBlockInput
            label="Output (JSON)"
            language="json"
            value={boundaryTimerOutput}
            onChange={handleOutputChange}
          />
        </Grid>
      </Grid>
    </TaskFormSection>
  );
};

export default BoundaryTimerSection;
