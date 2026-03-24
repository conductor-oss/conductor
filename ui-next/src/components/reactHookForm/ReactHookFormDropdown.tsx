import { Dropdown } from "components";
import { Control, Controller, FieldPath, FieldValues } from "react-hook-form";

interface IReactHookFormDropdown<
  TFieldValues extends FieldValues,
  TName extends FieldPath<TFieldValues>,
> {
  control: Control<TFieldValues>;
  defaultValue?: any;
  freeSolo?: boolean;
  fullWidth?: boolean;
  label: string;
  multiple?: boolean;
  name: TName;
  required: boolean;
  options: string[] | number[] | Array<{ label: string }>;
  error?: boolean;
  helperText?: any;
  id?: string;
  getOptionLabel?: (option?: any) => any;
}

export default function ReactHookFormDropdown<
  T extends FieldValues,
  TN extends FieldPath<T>,
>({
  control,
  defaultValue,
  name,
  required,
  ...props
}: IReactHookFormDropdown<T, TN>) {
  return (
    <Controller
      control={control}
      defaultValue={defaultValue}
      name={name}
      render={({ field }) => (
        <Dropdown
          {...field}
          {...props}
          value={field.value}
          onChange={(event: any, item: any) => field.onChange(item)}
          required={required}
        />
      )}
      rules={{ required: required }}
    />
  );
}
