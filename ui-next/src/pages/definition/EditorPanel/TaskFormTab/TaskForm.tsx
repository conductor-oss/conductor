import { Box, Paper } from "@mui/material";
import { useSelector } from "@xstate/react";
import {
  TaskFormEvents,
  TaskFormMachineContext,
} from "pages/definition/EditorPanel/TaskFormTab/state";
import { WorkflowDefinitionEvents } from "pages/definition/state/types";
import { FunctionComponent } from "react";
import { ActorRef, State } from "xstate";
import TaskFormContent from "./TaskFormContent";
import { TaskFormContextProvider } from "./state";
export interface TaskFormProps {
  formTaskActor: ActorRef<TaskFormEvents>;
}

const NoTaskSelected = () => (
  <Box
    sx={{
      opacity: 0.5,
      padding: 8,
      textAlign: "center",
    }}
  >
    No task selected
  </Box>
);

const TaskForm: FunctionComponent<TaskFormProps> = ({ formTaskActor }) => {
  const hasTaskToEdit = useSelector(
    formTaskActor!,
    (state: State<TaskFormMachineContext>) => state.matches("rendered"),
  );

  const taskType = useSelector(
    formTaskActor!,
    (state: State<TaskFormMachineContext>) => state.context?.originalTask?.type,
  );

  return hasTaskToEdit && taskType && formTaskActor ? (
    <TaskFormContextProvider formTaskActor={formTaskActor}>
      <TaskFormContent />
    </TaskFormContextProvider>
  ) : (
    <NoTaskSelected />
  );
};

const MaybeTaskForm: FunctionComponent<{
  workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents>;
  isInTaskFormState: boolean;
}> = ({ workflowDefinitionActor, isInTaskFormState }) => {
  const formTaskActor =
    //@ts-ignore next-line
    workflowDefinitionActor?.children?.get("formTaskMachine");

  return (
    <Paper
      id="maybe-task-form"
      square
      sx={{
        position: "relative",
        height: "100%",
        width: "100%",
        display: "grid",
        overflow: "hidden",
        gridTemplateRows: "auto 1fr auto",
        background: (theme) => theme.palette.customBackground.form,
      }}
    >
      {isInTaskFormState ? (
        formTaskActor && <TaskForm formTaskActor={formTaskActor} />
      ) : (
        <NoTaskSelected />
      )}
    </Paper>
  );
};

export default MaybeTaskForm;
