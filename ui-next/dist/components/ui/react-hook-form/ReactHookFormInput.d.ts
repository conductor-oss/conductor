import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { ConductorInputProps } from "components/ui/inputs/ConductorInput";
type ReactHookFormInputProps<T extends FieldValues, U extends FieldPath<T>> = ConductorInputProps & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => string;
    outputTransform?: (value: string, lastFormValues?: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormInput<T extends FieldValues, U extends FieldPath<T>>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormInputProps<T, U>): import("react").JSX.Element;
export {};
