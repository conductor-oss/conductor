import { FunctionComponent } from "react";
interface ConductorArrayMapFormProps {
    value: {
        role: string;
        message: string;
    }[];
    onChange: (messages: {
        role: string;
        message: string;
    }[]) => void;
}
declare const ConductorArrayMapFormBase: FunctionComponent<ConductorArrayMapFormProps>;
declare const ConductorArrayMapForm: FunctionComponent<ConductorArrayMapFormProps & {
    label?: string;
    taskType: import("../../../../../..").FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
}>;
export { ConductorArrayMapForm, ConductorArrayMapFormBase };
