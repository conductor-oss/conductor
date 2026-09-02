import { FieldPath, FieldValues, PathValue, UseControllerProps } from "react-hook-form";
import { ConductorAutocompleteProps } from "components/ui/inputs/ConductorAutoComplete";
type ReactHookFormDropdownProps<T extends FieldValues, U extends FieldPath<T>, V> = ConductorAutocompleteProps<V> & UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => V | V[];
    outputTransform?: (value: V | V[], lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
};
export default function ReactHookFormDropdown<T extends FieldValues, U extends FieldPath<T>, V = string>({ control, name, rules, shouldUnregister, defaultValue, inputTransform, outputTransform, onChangeCallback, ...props }: ReactHookFormDropdownProps<T, U, V>): import("react").JSX.Element;
export {};
