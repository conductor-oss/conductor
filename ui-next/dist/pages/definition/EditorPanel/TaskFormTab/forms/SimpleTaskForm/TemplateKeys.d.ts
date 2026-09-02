import { FunctionComponent } from "react";
import { TaskDef } from "types";
export interface TemplateKeysProps {
    task: Partial<TaskDef>;
    onUniteParameter: (partialInputParams: Record<string, unknown>) => void;
}
export declare const TemplateKeys: FunctionComponent<TemplateKeysProps>;
