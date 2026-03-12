import { useSelector } from "@xstate/react";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import { TaskDefinitionDialogsProps } from "pages/definition/task/dialogs/state";
import {
  TaskDefinitionMachineState,
  TaskDefinitionMachineEventType,
} from "pages/definition/task/state/types";
//
const TaskDefinitionDialogs = ({
  taskDefActor,
}: TaskDefinitionDialogsProps) => {
  const isConfirmReset = useSelector(taskDefActor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.RESET_FORM,
      TaskDefinitionMachineState.RESET_FORM_CONFIRM,
    ]),
  );

  const isConfirmDelete = useSelector(taskDefActor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.DELETE_FORM,
      TaskDefinitionMachineState.DELETE_FORM_CONFIRM,
    ]),
  );

  const originTaskDefinition = useSelector(
    taskDefActor,
    (state) => state.context.originTaskDefinition,
  );

  const handleResetConfirmation = (val: boolean) => {
    taskDefActor.send({
      type: val
        ? TaskDefinitionMachineEventType.CONFIRM_RESET_TASK
        : TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE,
    });
  };

  return (
    <>
      {isConfirmReset ? (
        <ConfirmChoiceDialog
          handleConfirmationValue={handleResetConfirmation}
          message={
            "You will lose all changes made in the editor. Please confirm resetting task definition to its original state."
          }
          header={"Resetting Confirmation"}
        />
      ) : null}

      {isConfirmDelete && (
        <ConfirmChoiceDialog
          handleConfirmationValue={handleResetConfirmation}
          message={
            <>
              Are you sure you want to delete&nbsp;
              <strong style={{ color: "red" }}>
                {originTaskDefinition?.name}
              </strong>
              &nbsp;task definition? This change cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type&nbsp;
                <strong>{originTaskDefinition?.name}</strong>
                &nbsp;to confirm.
              </div>
            </>
          }
          header={"Deletion Confirmation"}
          isInputConfirmation
          valueToBeDeleted={originTaskDefinition?.name}
        />
      )}
    </>
  );
};

export default TaskDefinitionDialogs;
