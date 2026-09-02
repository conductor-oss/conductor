import { FunctionComponent } from "react";
interface UpdateTaskStatusFormProps {
    onConfirm: (status: string, body: string) => void;
}
export declare const UpdateTaskStatusForm: FunctionComponent<UpdateTaskStatusFormProps>;
export {};
