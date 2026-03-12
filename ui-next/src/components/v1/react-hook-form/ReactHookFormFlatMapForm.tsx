import {
  FieldPath,
  FieldValues,
  PathValue,
  UseControllerProps,
  useController,
} from "react-hook-form";
import _isNil from "lodash/isNil";

import {
  ConductorFlatMapFormBase,
  ConductorFlatMapFormProps,
} from "../FlatMapForm/ConductorFlatMapForm";

type ReactHookFormFlatMapFormProps<
  T extends FieldValues,
  U extends FieldPath<T>,
> = ConductorFlatMapFormProps &
  UseControllerProps<T, U> & {
    inputTransform?: (
      value: PathValue<T, U>,
      lastFormValues?: T,
    ) => ConductorFlatMapFormProps["value"];
    outputTransform?: (
      value: ConductorFlatMapFormProps["value"],
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

export default function ReactHookFormFlatMapForm<
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
}: ReactHookFormFlatMapFormProps<T, U>) {
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
    <ConductorFlatMapFormBase
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
