import React from "react";
import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { FieldComponentType } from "utils/fieldHelpers";
import { ActorRef } from "xstate";
import { LLMFormFieldsEvents } from "./state";
interface LLMFormFieldsWrapperProps {
    onChange: (task: Partial<TaskDef>) => void;
    task: Partial<TaskDef>;
    allFieldComponents: Array<[UiIntegrationsFieldType, FieldComponentType]>;
    children: (actor: ActorRef<LLMFormFieldsEvents>) => React.ReactNode;
}
declare const LLMFormFieldsWrapper: ({ onChange, task, allFieldComponents, children, }: LLMFormFieldsWrapperProps) => React.JSX.Element;
export default LLMFormFieldsWrapper;
