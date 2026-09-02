import { FormTaskType } from "types/TaskType";
import { FunctionComponent } from "react";
type CommonProps = {
    label?: string;
    taskType: FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
};
declare function maybeVariable<T>(WrappedComponent: FunctionComponent<T>): FunctionComponent<T & CommonProps>;
export default maybeVariable;
