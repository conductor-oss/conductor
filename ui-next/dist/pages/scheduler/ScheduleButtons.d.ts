import { FunctionComponent } from "react";
export interface ScheduleButtonsProps {
    isConfirmingSave: boolean;
    couldNotParseJson: boolean;
    cancelConfirmSave: () => void;
    saveScheduleSubmit: () => void;
    clearScheduleForm: () => void;
    setSaveConfirmationOpen: () => void;
}
declare const ScheduleButtons: FunctionComponent<ScheduleButtonsProps>;
export default ScheduleButtons;
