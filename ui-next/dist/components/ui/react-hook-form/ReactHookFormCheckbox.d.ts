import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { ConductorCheckboxProps } from "components/ui/inputs/ConductorCheckbox";
type ReactHookFormCheckboxProps<T extends FieldValues, U extends FieldPath<T>> = ConductorCheckboxProps & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => ConductorCheckboxProps["value"];
    outputTransform?: (value: boolean, lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormCheckbox<T extends FieldValues, U extends FieldPath<T>>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormCheckboxProps<T, U>): import("react").JSX.Element;
export {};
