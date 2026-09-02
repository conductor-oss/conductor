import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { IdempotencyFormProps } from "pages/runWorkflow/IdempotencyForm";
type ReactHookFormIdempotencyFormProps<T extends FieldValues, U extends FieldPath<T>> = Omit<IdempotencyFormProps, "idempotencyValues" | "onChange"> & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => IdempotencyFormProps["idempotencyValues"];
    outputTransform?: (value: IdempotencyFormProps["idempotencyValues"], lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormIdempotencyForm<T extends FieldValues, U extends FieldPath<T>>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormIdempotencyFormProps<T, U>): import("react").JSX.Element;
export {};
