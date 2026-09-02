import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { ConductorNameVersionFieldProps } from "components/inputs/ConductorNameVersionField";
type ReactHookFormNameVersionFieldProps<T extends FieldValues, U extends FieldPath<T>> = ConductorNameVersionFieldProps & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => ConductorNameVersionFieldProps["value"];
    outputTransform?: (value: {
        name?: string;
        version?: number;
    } | undefined, lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormNameVersionField<T extends FieldValues, U extends FieldPath<T>>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormNameVersionFieldProps<T, U>): import("react").JSX.Element;
export {};
