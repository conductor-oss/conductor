import { FunctionComponent } from "react";
import { WaitType } from "pages/definition/EditorPanel/TaskFormTab/forms/WaitTaskForm/types";
declare const SelectWaitType: FunctionComponent<{
    options: WaitType[];
    onChange: (val: WaitType) => void;
    value: string;
}>;
export default SelectWaitType;
