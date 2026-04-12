import { Grid } from "@mui/material";
import { Theme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useActor, useSelector } from "@xstate/react";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";

import { Button } from "components";
import ConductorInput from "components/ui/inputs/ConductorInput";
import {
  TaskFormHeaderEventTypes,
  TaskHeaderMachineEvents,
} from "./state/types";

export interface TaskFormHeaderTasksProps {
  taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}
export const TaskFormHeaderTasks: FunctionComponent<
  TaskFormHeaderTasksProps
> = ({ taskFormHeaderActor }) => {
  const isMobileWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );

  const taskName = useSelector(
    taskFormHeaderActor,
    (state) => state.context.name,
  );
  const taskReferenceName = useSelector(
    taskFormHeaderActor,
    (state) => state.context.taskReferenceName,
  );

  const [, send] = useActor(taskFormHeaderActor);

  const handleChangeName = (value: string) => {
    send({
      type: TaskFormHeaderEventTypes.CHANGE_NAME_VALUE,
      value,
    });
  };
  const handleGenerateNameTaskReferenceName = () => {
    send({
      type: TaskFormHeaderEventTypes.GENERATE_TASK_REFERENCE_NAME,
    });
  };

  const handleChangeTaskReferenceName = (value: string) => {
    send({
      type: TaskFormHeaderEventTypes.CHANGE_TASK_REFERENCE_VALUE,
      value,
    });
  };

  return (
    <Grid
      container
      spacing={2}
      flexDirection={isMobileWidth ? "column" : "row"}
      sx={{ width: "100%" }}
    >
      <Grid flexGrow={2}>
        <ConductorInput
          id="task-form-header-task-name-field"
          label="Task definition"
          fullWidth
          value={taskName}
          onTextInputChange={(value) => {
            handleChangeName(value);
          }}
          onFocus={() =>
            send({ type: TaskFormHeaderEventTypes.START_EDITING_VALUES })
          }
          onBlur={() =>
            send({ type: TaskFormHeaderEventTypes.STOP_EDITING_VALUES })
          }
        />
      </Grid>
      <Grid flexGrow={2}>
        <ConductorInput
          id="task-form-header-task-reference-field"
          label="Reference name"
          fullWidth
          value={taskReferenceName}
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
      <Grid>
        <Button
          id="generate-task-name"
          color="secondary"
          size="small"
          onClick={handleGenerateNameTaskReferenceName}
          sx={{ minHeight: 40.13, height: 40.13 }}
        >
          Generate
        </Button>
      </Grid>
    </Grid>
  );
};
