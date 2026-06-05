import {
  FieldPath,
  FieldValues,
  PathValue,
  UseControllerProps,
  useController,
} from "react-hook-form";
import _isNil from "lodash/isNil";

import IdempotencyForm, {
  IdempotencyFormProps,
} from "pages/runWorkflow/IdempotencyForm";

type ReactHookFormIdempotencyFormProps<
  T extends FieldValues,
  U extends FieldPath<T>,
> = Omit<IdempotencyFormProps, "idempotencyValues" | "onChange"> &
  UseControllerProps<T, U> & {
    inputTransform?: (
      value: PathValue<T, U>,
      lastFormValues?: T,
    ) => IdempotencyFormProps["idempotencyValues"];
    outputTransform?: (
      value: IdempotencyFormProps["idempotencyValues"],
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

export default function ReactHookFormIdempotencyForm<
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
}: ReactHookFormIdempotencyFormProps<T, U>) {
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
    <IdempotencyForm
      {...fieldProps}
      {...props}
      idempotencyValues={validateInputValue(
        inputTransform(value, control?._formValues as T),
      )}
      onChange={(values) => {
        if (outputTransform) {
          onChange(outputTransform(values, control?._formValues as T));
          onChangeCallback?.(
            outputTransform(values, control?._formValues as T),
          );
        } else {
          onChange(values);
          onChangeCallback?.(values as PathValue<T, U>);
        }
      }}
    />
  );
}
