import { FunctionComponent } from "react";
interface SimpleTaskNameInputProps {
    onChange?: any;
    value?: string;
    error?: boolean;
    helperText?: string;
    isMetaBarEditing?: boolean;
    triggerSuccessEvent?: () => void;
}
declare const SimpleTaskNameInput: FunctionComponent<SimpleTaskNameInputProps>;
export default SimpleTaskNameInput;
