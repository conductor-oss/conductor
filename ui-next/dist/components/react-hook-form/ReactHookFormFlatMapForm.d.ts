import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { ConductorFlatMapFormProps } from "components/FlatMapForm/ConductorFlatMapForm";
type ReactHookFormFlatMapFormProps<T extends FieldValues, U extends FieldPath<T>> = ConductorFlatMapFormProps & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => ConductorFlatMapFormProps["value"];
    outputTransform?: (value: ConductorFlatMapFormProps["value"], lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormFlatMapForm<T extends FieldValues, U extends FieldPath<T>>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormFlatMapFormProps<T, U>): import("react").JSX.Element;
export {};
