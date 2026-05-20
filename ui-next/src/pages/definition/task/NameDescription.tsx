import { Box, TextField } from "@mui/material";
import { useSelector } from "@xstate/react";
import { Text } from "components";
import EditInPlace from "components/ui/inputs/EditInPlace";
import _isString from "lodash/isString";
import { useTaskDefinitionFormActor } from "pages/definition/task/form/state/hook";
import { TASK_FORM_MACHINE_ID } from "pages/definition/task/state/helpers";
import { FunctionComponent, useRef } from "react";
import { disabledInputStyle } from "shared/styles";
import { ActorRef } from "xstate";
import { TaskDefinitionFormMachineEvent } from "./form/state/types";
import {
  TaskDefinitionMachineEvent,
  TaskDefinitionMachineState,
} from "./state/types";

interface NameDescriptionProps {
  taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
}

const flexWrap = {
  display: "flex",
  alignItems: "center",
  gap: 4,
  flexWrap: "wrap",
};

interface NameDescriptionFromProps {
  // This should not be like this ideally the machine should reuse the editInPLace machine like human form builder does
  formActor: ActorRef<TaskDefinitionFormMachineEvent>;
}

const NameDescriptionForm: FunctionComponent<NameDescriptionFromProps> = ({
  formActor,
}) => {
  const inputNameRef = useRef(null);
  const inputDescriptionRef = useRef(null);

  const [
    { modifiedTaskDefinition, isEditingName, isEditingDescription, error },
    { handleChangeTaskForm, setEditingFieldForm },
  ] = useTaskDefinitionFormActor(formActor);
  return (
    <>
      <EditInPlace
        style={{
          fontSize: "14pt",
          fontWeight: "bold",
          wordBreak: "break-all",
        }}
        isEditing={isEditingName}
        setEditing={(val) => setEditingFieldForm(val ? "name" : "none")}
        text={modifiedTaskDefinition.name}
        childRef={inputNameRef}
        disabled={false}
        placeholder="Type task name here"
        type="input"
      >
        <TextField
          inputRef={inputNameRef}
          fullWidth
          autoFocus
          name="name"
          value={modifiedTaskDefinition.name || ""}
          onChange={(event) => handleChangeTaskForm(event.target.value, event)}
          error={!!error?.name}
          helperText={error?.name?.message}
          sx={{
            input: {
              fontSize: "14pt",
              fontWeight: "bold",
            },
            ...disabledInputStyle,
          }}
        />
      </EditInPlace>
      <EditInPlace
        style={{
          fontSize: "12pt",
          wordBreak: "break-all",
          flexGrow: "1",
        }}
        isEditing={isEditingDescription}
        setEditing={(val) => setEditingFieldForm(val ? "description" : "none")}
        text={modifiedTaskDefinition.description}
        childRef={inputDescriptionRef}
        disabled={false}
        placeholder="Type task description here"
        type="input"
      >
        <TextField
          inputRef={inputDescriptionRef}
          fullWidth
          autoFocus
          name="description"
          onChange={(event) => handleChangeTaskForm(event.target.value, event)}
          value={modifiedTaskDefinition.description || ""}
          error={!!error?.description}
          helperText={error?.description?.message}
          sx={{
            input: {
              fontSize: "12pt",
            },
            ...disabledInputStyle,
          }}
        />
      </EditInPlace>
    </>
  );
};

export const NameDescription: FunctionComponent<NameDescriptionProps> = ({
  taskDefActor,
}) => {
  const isFormState = useSelector(taskDefActor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.MAIN_CONTAINER,
      TaskDefinitionMachineState.FORM,
    ]),
  );

  // @ts-ignore
  const formActor = taskDefActor?.children?.get(TASK_FORM_MACHINE_ID);

  const modifiedTaskDefinition = useSelector(
    taskDefActor,
    (state) => state.context.modifiedTaskDefinition,
  );

  return (
    <Box sx={{ ...flexWrap, marginBottom: "10px" }}>
      {isFormState && formActor ? (
        <NameDescriptionForm formActor={formActor} />
      ) : (
        <>
          <Text sx={{ marginBottom: 0, fontSize: "14pt", fontWeight: "bold" }}>
            {_isString(modifiedTaskDefinition?.name)
              ? modifiedTaskDefinition?.name
              : ""}
          </Text>
          <Text
            sx={{ fontSize: "12pt", wordBreak: "break-all", flexGrow: "1" }}
          >
            {_isString(modifiedTaskDefinition?.description)
              ? modifiedTaskDefinition?.description
              : ""}
          </Text>
        </>
      )}
    </Box>
  );
};
