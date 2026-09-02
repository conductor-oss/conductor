import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { FieldComponentType } from "utils/fieldHelpers";
import { ActorRef } from "xstate";
import { LLMFormFieldsEvents } from "./state";
interface LLMFormFieldsProps {
    onChange: (task: Partial<TaskDef>) => void;
    task: Partial<TaskDef>;
    fieldFieldComponents: Array<[UiIntegrationsFieldType, FieldComponentType]>;
    actor: ActorRef<LLMFormFieldsEvents>;
}
export declare const LLMFormFields: ({ fieldFieldComponents, onChange, task, actor, }: LLMFormFieldsProps) => import("react").JSX.Element;
export type { LLMFormFieldsProps };
