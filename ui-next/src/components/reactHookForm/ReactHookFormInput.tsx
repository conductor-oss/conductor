import { Input } from "components";
import { Control, Controller, FieldPath, FieldValues } from "react-hook-form";

interface IReactHookFormInput<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
> {
  control: Control<TFieldValues>;
  defaultValue?: any;
  error?: boolean;
  fullWidth?: boolean;
  helperText?: string | any;
  label?: string;
  placeholder?: string;
  name: TName;
  required: boolean;
  readOnlyInput?: boolean;
  id?: string;
  spellCheck?: boolean;
  multiline?: boolean;
  minRows?: number | string;
  maxRows?: number | string;
}

export default function ReactHookFormInput<
  T extends FieldValues,
  TN extends FieldPath<T>,
>({
  control,
  defaultValue,
  error,
  fullWidth,
  helperText,
  label,
  placeholder,
  name,
  required,
  readOnlyInput,
  ...props
}: IReactHookFormInput<T, TN>) {
  return (
    <Controller
      control={control}
      defaultValue={defaultValue}
      name={name}
      render={({ field }) => (
        <Input
          {...field}
          {...props}
          error={error}
          fullWidth={fullWidth}
          helperText={helperText}
          label={label}
          placeholder={placeholder}
          required={required}
          inputProps={{ readOnly: readOnlyInput }}
          onChange={(value) => field.onChange(value)}
        />
      )}
      rules={{ required: required }}
    />
  );
}
