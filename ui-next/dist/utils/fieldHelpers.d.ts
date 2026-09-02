import { LLMFormFieldsEvents } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields/state";
import { FunctionComponent } from "react";
import { TaskDef } from "types/common";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { ActorRef } from "xstate";
export type FieldComponentType = FunctionComponent<{
    onChange: (t: Partial<TaskDef>) => void;
    actor: ActorRef<LLMFormFieldsEvents>;
    task: Partial<TaskDef>;
}>;
export declare const updateField: (path: string, value: any, taskJson: any) => any;
export declare const fieldsToFieldsFieldsComponents: (fields: UiIntegrationsFieldType[]) => Array<[UiIntegrationsFieldType, FieldComponentType]>;
