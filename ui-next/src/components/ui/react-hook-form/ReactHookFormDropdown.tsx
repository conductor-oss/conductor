import {
  FieldPath,
  FieldValues,
  PathValue,
  UseControllerProps,
  useController,
} from "react-hook-form";
import _isNil from "lodash/isNil";

import {
  ConductorAutoComplete,
  ConductorAutocompleteProps,
} from "components/ui/inputs/ConductorAutoComplete";

type ReactHookFormDropdownProps<
  T extends FieldValues,
  U extends FieldPath<T>,
  V,
> = ConductorAutocompleteProps<V> &
  UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => V | V[];
    outputTransform?: (value: V | V[], lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
  };

const validateInputValue = (value: any) => {
  if (_isNil(value)) {
    return null;
  }
  return value;
};

export default function ReactHookFormDropdown<
  T extends FieldValues,
  U extends FieldPath<T>,
  V = string,
>({
  // Controller props
  control,
  name,
  rules,
  shouldUnregister,
  defaultValue,

  // Manipulate the value before it is passed to the controller
  inputTransform = (value) => value,
  // Manipulate the value before the onChange is fired
  outputTransform,
  // Callback to be called when the value changed
  onChangeCallback,

  // Checkbox props
  ...props
}: ReactHookFormDropdownProps<T, U, V>) {
  const {
    field: { value, onChange, ...fieldProps },
  } = useController<T, U>({
    control,
    name,
    rules,
    shouldUnregister,
    defaultValue,
  });
  return (
    <ConductorAutoComplete
      {...fieldProps}
      {...props}
      value={validateInputValue(
        inputTransform(value, control?._formValues as T),
      )}
      onChange={(_, newValue: V | V[]) => {
        if (outputTransform) {
          onChange(outputTransform(newValue, control?._formValues as T));
          onChangeCallback?.(
            outputTransform(newValue, control?._formValues as T),
          );
        } else {
          onChange(newValue);
          onChangeCallback?.(newValue as PathValue<T, U>);
        }
      }}
      onInputChange={(_, newValue: string) => {
        if (!props.multiple && props.freeSolo) {
          onChange(newValue);
        }
      }}
    />
  );
}
