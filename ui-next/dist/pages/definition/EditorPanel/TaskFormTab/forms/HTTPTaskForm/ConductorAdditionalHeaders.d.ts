import { FunctionComponent } from "react";
interface ConductorAdditionalHeadersProps {
    headers: Record<string, string>;
    onChangeHeaders: (headers: Record<string, string>) => void;
}
declare const ConductorAdditionalHeadersBase: FunctionComponent<ConductorAdditionalHeadersProps>;
declare const ConductorAdditionalHeaders: FunctionComponent<ConductorAdditionalHeadersProps & {
    label?: string;
    taskType: import("../../../../../..").FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
}>;
export { ConductorAdditionalHeaders, ConductorAdditionalHeadersBase };
