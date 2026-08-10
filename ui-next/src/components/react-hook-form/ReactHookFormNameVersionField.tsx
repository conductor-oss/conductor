import {
  FieldPath,
  FieldValues,
  PathValue,
  useController,
  UseControllerProps,
} from "react-hook-form";
import _isNil from "lodash/isNil";

import {
  ConductorNameVersionField,
  ConductorNameVersionFieldProps,
} from "components/inputs/ConductorNameVersionField";

type ReactHookFormNameVersionFieldProps<
  T extends FieldValues,
  U extends FieldPath<T>,
> = ConductorNameVersionFieldProps &
  UseControllerProps<T, U> & {
    inputTransform?: (
      value: PathValue<T, U>,
      lastFormValues?: T,
    ) => ConductorNameVersionFieldProps["value"];
    outputTransform?: (
      value:
        | {
            name?: string;
            version?: number;
          }
        | undefined,
      lastFormValues: T,
    ) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
  };

const validateInputValue = (value: any) => {
  if (_isNil(value)) {
    return {};
  }
  return value;
};

export default function ReactHookFormNameVersionField<
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

  // Dropdown props
  ...props
}: ReactHookFormNameVersionFieldProps<T, U>) {
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
    <ConductorNameVersionField
      {...fieldProps}
      {...props}
      value={validateInputValue(
        inputTransform(value, control?._formValues as T),
      )}
      onChange={(value) => {
        if (outputTransform) {
          onChange(outputTransform(value, control?._formValues as T));
          onChangeCallback?.(outputTransform(value, control?._formValues as T));
        } else {
          onChange(value);
          onChangeCallback?.(value as PathValue<T, U>);
        }
      }}
    />
  );
}
