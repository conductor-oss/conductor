import { FunctionComponent } from "react";
import { BoxProps } from "@mui/material";
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
declare const EditInPlace: FunctionComponent<EditInPlaceProps>;
export default EditInPlace;
