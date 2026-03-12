import { Paper } from "@mui/material";
import { useSelector } from "@xstate/react";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";

import { TaskType } from "types";
import { TaskHeaderMachineEvents } from "./state/types";
import { TaskFormHeaderSimple } from "./TaskFormHeaderSimple";
import { TaskFormHeaderTasks } from "./TaskFormHeaderTasks";
import { featureFlags, FEATURES } from "utils/flags";

export interface TaskFormHeaderProps {
  taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}

const showServiceTemplateSelector = featureFlags.isEnabled(
  FEATURES.REMOTE_SERVICES,
);
// TODO we should probably have two different components when for simple and one for the other... Two many ifs
const TaskFormHeader: FunctionComponent<TaskFormHeaderProps> = ({
  taskFormHeaderActor,
}) => {
  const taskType = useSelector(
    taskFormHeaderActor,
    (state) => state.context.taskType,
  );

  const tasksArrayWithTaskDropdown = [
    TaskType.SIMPLE,
    TaskType.HUMAN,
    ...(showServiceTemplateSelector ? [TaskType.HTTP, TaskType.GRPC] : []),
  ];

  return (
    <Paper
      elevation={0}
      variant="elevation"
      square
      sx={{
        width: "100%",
        padding: "4px 24px",
        background: (theme) => theme.palette.customBackground.form,
      }}
    >
      {tasksArrayWithTaskDropdown.includes(taskType) ? (
        <TaskFormHeaderSimple taskFormHeaderActor={taskFormHeaderActor} />
      ) : (
        <TaskFormHeaderTasks taskFormHeaderActor={taskFormHeaderActor} />
      )}
    </Paper>
  );
};

export default TaskFormHeader;
