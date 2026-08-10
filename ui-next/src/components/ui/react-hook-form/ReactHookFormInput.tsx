import _isNil from "lodash/isNil";
import {
  FieldPath,
  FieldValues,
  PathValue,
  UseControllerProps,
  useController,
} from "react-hook-form";

import ConductorInput, {
  ConductorInputProps,
} from "components/ui/inputs/ConductorInput";

type ReactHookFormInputProps<
  T extends FieldValues,
  U extends FieldPath<T>,
> = ConductorInputProps &
  UseControllerProps<T, U> & {
    inputTransform?: (value: PathValue<T, U>, lastFormValues?: T) => string;
    outputTransform?: (value: string, lastFormValues?: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
  };

const validateInputValue = (value: any) => {
  if (_isNil(value)) {
    return "";
  }
  return value;
};

export default function ReactHookFormInput<
  T extends FieldValues,
  U extends FieldPath<T>,
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
}: ReactHookFormInputProps<T, U>) {
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
    <ConductorInput
      {...fieldProps}
      {...props}
      value={validateInputValue(
        inputTransform(value, control?._formValues as T),
      )}
      onChange={(event) => {
        if (outputTransform) {
          onChange(
            outputTransform(event.target.value, control?._formValues as T),
          );
          onChangeCallback?.(outputTransform(event.target.value));
        } else {
          onChange(event.target.value);
          onChangeCallback?.(event.target.value as PathValue<T, U>);
        }
      }}
    />
  );
}
