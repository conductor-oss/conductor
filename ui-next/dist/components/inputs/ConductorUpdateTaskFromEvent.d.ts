import { ConductorInputProps } from "components/ui/inputs/ConductorInput";
import { ComponentType } from "react";
type EventTaskReferenceInput = {
    taskId: string;
};
type WorkflowTaskReferenceInput = {
    workflowId: string;
    taskRefName: string;
};
export type EventJson = Partial<EventTaskReferenceInput & WorkflowTaskReferenceInput>;
interface FormWithRadioGroupProps {
    value: EventJson;
    onChange: (value: EventJson) => void;
    inputComponent?: ComponentType<ConductorInputProps>;
}
export declare const ConductorUpdateTaskFormEvent: ({ value, onChange, inputComponent: InputComponent, }: FormWithRadioGroupProps) => import("react").JSX.Element;
export {};
