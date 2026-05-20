import { Grid, Paper } from "@mui/material";
import { useSelector } from "@xstate/react";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";

import { Button } from "components";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SimpleTaskNameInput from "../SimpleTaskNameInput";
import {
  TaskFormHeaderEventTypes,
  TaskHeaderMachineEvents,
} from "./state/types";

export interface TaskFormHeaderSimpleProps {
  taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}

export const TaskFormHeaderSimple: FunctionComponent<
  TaskFormHeaderSimpleProps
> = ({ taskFormHeaderActor }) => {
  const taskName = useSelector(
    taskFormHeaderActor,
    (state) => state.context.name,
  );
  const taskReferenceName = useSelector(
    taskFormHeaderActor,
    (state) => state.context.taskReferenceName,
  );

  const send = taskFormHeaderActor.send;

  const handleGenerateNameTaskReferenceName = () => {
    send({
      type: TaskFormHeaderEventTypes.GENERATE_TASK_REFERENCE_NAME,
    });
  };
  const handleChangeName = (value: string) => {
    send({
      type: TaskFormHeaderEventTypes.CHANGE_NAME_VALUE,
      value,
    });
  };

  const handleChangeTaskReferenceName = (value: string) => {
    send({
      type: TaskFormHeaderEventTypes.CHANGE_TASK_REFERENCE_VALUE,
      value,
    });
  };

  const triggerSuccessEvent = () => {
    send({
      type: TaskFormHeaderEventTypes.TASK_CREATED_SUCCESSFULLY,
    });
  };

  return (
    <Paper
      elevation={0}
      variant="elevation"
      square
      sx={{
        width: "100%",
        padding: "4px 0px",
        background: (theme) => theme.palette.customBackground.form,
      }}
    >
      <Grid container sx={{ width: "100%" }} spacing={2}>
        <Grid
          flexGrow={1}
          size={{
            xs: 12,
            sm: 12,
            md: 6,
            lg: 5,
          }}
        >
          <SimpleTaskNameInput
            value={taskName}
            onChange={handleChangeName}
            triggerSuccessEvent={triggerSuccessEvent}
          />
        </Grid>
        <Grid
          flexGrow={1}
          size={{
            md: 4,
            lg: 5,
          }}
        >
          <ConductorInput
            id="task-form-header-simple-reference-name-field"
            fullWidth
            showClearButton
            label="Reference name"
            value={taskReferenceName}
            inputProps={{
              spellCheck: false,
            }}
            onTextInputChange={(value) => {
              handleChangeTaskReferenceName(value);
            }}
            onFocus={() =>
              send({ type: TaskFormHeaderEventTypes.START_EDITING_VALUES })
            }
            onBlur={() =>
              send({ type: TaskFormHeaderEventTypes.STOP_EDITING_VALUES })
            }
          />
        </Grid>
        <Grid
          size={{
            md: 2,
            lg: 1,
          }}
        >
          <Button
            color="secondary"
            size="small"
            onClick={handleGenerateNameTaskReferenceName}
            sx={{ minHeight: 40.13, height: 40.13 }}
          >
            Generate
          </Button>
        </Grid>
      </Grid>
    </Paper>
  );
};
