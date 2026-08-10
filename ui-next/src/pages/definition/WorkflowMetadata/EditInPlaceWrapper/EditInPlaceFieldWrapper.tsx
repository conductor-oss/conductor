import { ActorRef } from "xstate";
import { CSSProperties, useRef } from "react";
import { useActor, useSelector } from "@xstate/react";
import EditInPlace, {
  EditInPlaceProps,
} from "components/ui/inputs/EditInPlace";
import { InputBase } from "@mui/material";
import { EditInPlaceEventTypes, EditInPlaceMachineEvents } from "./state";
import { FunctionComponent } from "react";

interface EditorInPlaceFieldWrapperProps extends Omit<
  EditInPlaceProps,
  "isEditing" | "setEditing" | "text" | "childRef"
> {
  editInPlaceActor: ActorRef<EditInPlaceMachineEvents>;
  inputStyles?: CSSProperties;
}

export const EditInPlaceFieldWrapper: FunctionComponent<
  EditorInPlaceFieldWrapperProps
> = ({
  editInPlaceActor,
  disabled,
  inputStyles,
  style,
  ...editInPlaceProps
}) => {
  const [, send] = useActor(editInPlaceActor);
  const isEditing = useSelector(editInPlaceActor, (state) =>
    state.matches("editing"),
  );
  const fieldValue = useSelector(
    editInPlaceActor,
    (state) => state.context.value,
  );
  const handleChange = (value: string) => {
    send({ type: EditInPlaceEventTypes.CHANGE_VALUE, value });
  };

  const handleToggleEditing = () => {
    send({ type: EditInPlaceEventTypes.TOGGLE_EDITING });
  };

  const inputRef = useRef(null);
  return (
    <EditInPlace
      style={{
        ...style,
        cursor: disabled ? "not-allowed" : "pointer",
      }}
      isEditing={isEditing}
      setEditing={handleToggleEditing}
      {...editInPlaceProps}
      text={fieldValue}
      childRef={inputRef}
      disabled={disabled}
    >
      <InputBase
        value={fieldValue}
        inputRef={inputRef}
        onChange={({ target: { value } }) => {
          handleChange(value);
        }}
        style={inputStyles}
        id="edit-inline-input-base"
      />
    </EditInPlace>
  );
};
