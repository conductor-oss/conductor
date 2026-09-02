import { IScheduleDto } from "types/Schedulers";
export interface CloneScheduleDialogProps {
    schedule: IScheduleDto;
    defaultName: string;
    onClose: () => void;
    onSuccess: () => void;
    onError?: (error: Response) => void | Promise<void>;
}
declare const CloneScheduleDialog: ({ schedule, defaultName, onClose, onSuccess, onError, }: CloneScheduleDialogProps) => import("react").JSX.Element;
export default CloneScheduleDialog;
