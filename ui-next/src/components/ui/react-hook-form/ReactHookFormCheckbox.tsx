import {
  FieldPath,
  FieldValues,
  PathValue,
  UseControllerProps,
  useController,
} from "react-hook-form";

import {
  ConductorCheckbox,
  ConductorCheckboxProps,
} from "components/ui/inputs/ConductorCheckbox";

type ReactHookFormCheckboxProps<
  T extends FieldValues,
  U extends FieldPath<T>,
> = ConductorCheckboxProps &
  UseControllerProps<T, U> & {
    inputTransform?: (
      value: PathValue<T, U>,
      lastFormValues?: T,
    ) => ConductorCheckboxProps["value"];
    outputTransform?: (value: boolean, lastFormValues: T) => PathValue<T, U>;
    onChangeCallback?: (value: PathValue<T, U>) => void;
  };

const validateInputValue = (value: any) => {
  return Boolean(value);
};

export default function ReactHookFormCheckbox<
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
}: ReactHookFormCheckboxProps<T, U>) {
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
    <ConductorCheckbox
      {...fieldProps}
      {...props}
      value={validateInputValue(
        inputTransform(value, control?._formValues as T),
      )}
      onChange={(newValue) => {
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
    />
  );
}
