import React from "react";
import { ConductorInputProps } from "../../../../../../components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariablesProps } from "components/FlatMapForm/ConductorAutocompleteVariables";
type EventTaskReferenceInput = {
    taskId: string;
};
type WorkflowTaskReferenceInput = {
    workflowId: string;
    taskRefName: string;
};
export type EventJson = Partial<EventTaskReferenceInput & WorkflowTaskReferenceInput>;
export type AnInputComponent = React.FunctionComponent<ConductorInputProps | ConductorAutocompleteVariablesProps>;
interface FormWithRadioGroupProps {
    value: EventJson;
    onChange: (value: EventJson) => void;
    inputComponent?: AnInputComponent;
}
export declare const UpdateTaskFormEvent: ({ value, onChange, inputComponent: InputComponent, }: FormWithRadioGroupProps) => React.JSX.Element;
export {};
