import { FunctionComponent } from "react";
import { Box, BoxProps } from "@mui/material";
import { PencilSimple } from "@phosphor-icons/react";

export interface EditInPlaceProps extends BoxProps {
  text: string;
  type: string;
  placeholder: string;
  childRef: any;
  disabled?: boolean;
  isEditing: boolean;
  setEditing: (editing: boolean) => void;
  toggleMetaBarEditMode?: (isMetaBarEditing: boolean) => void;
}

const EditInPlace: FunctionComponent<EditInPlaceProps> = ({
  text,
  type,
  placeholder,
  children,
  childRef: _childRef,
  disabled,
  toggleMetaBarEditMode: _toggleMetaBarEditMode,
  isEditing,
  setEditing,
  ...props
}) => {
  const toggleEditMode = (isOpen: boolean) => {
    setEditing(isOpen);
  };

  const handleKeyDown = (event: any, type: any) => {
    const { key } = event;
    const keys = ["Escape", "Tab"];
    const enterKey = "Enter";
    const allKeys = [...keys, enterKey];
    if (
      (type === "textarea" && keys.indexOf(key) > -1) ||
      (type !== "textarea" && allKeys.indexOf(key) > -1)
    ) {
      toggleEditMode(false);
    }
  };

  return (
    <Box {...props}>
      {isEditing ? (
        <Box
          onBlur={() => {
            toggleEditMode(false);
          }}
          onKeyDown={(e) => handleKeyDown(e, type)}
          {...props}
        >
          {children}
        </Box>
      ) : (
        <Box
          onClick={() => {
            return disabled ? null : toggleEditMode(true);
          }}
        >
          <Box style={{ opacity: text ? 1 : 0.6 }} {...props}>
            {text || placeholder || "Click here to edit..."}
            {!disabled && (
              <PencilSimple
                style={{ marginLeft: "1rem", cursor: "pointer" }}
                id="edit-pencil-icon"
              />
            )}
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default EditInPlace;
