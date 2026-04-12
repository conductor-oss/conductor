import { ChangeEvent, forwardRef, FunctionComponent } from "react";
import { NumericFormat, NumericFormatProps } from "react-number-format";

import ConductorInput, {
  ConductorInputProps,
} from "components/ui/inputs/ConductorInput";

export type ConductorInputNumberProps = Omit<
  ConductorInputProps,
  "onChange" | "onBlur"
> & {
  value: number | null;
  onChange: (
    val: number | null,
    event?: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => void;
};

interface NumericFormatCustomProps {
  onChange: (event: { target: { name: string; value: string } }) => void;
  name: string;
}

const NumericFormatCustom = forwardRef<
  NumericFormatProps,
  NumericFormatCustomProps & {
    min?: number;
    max?: number;
    allowFloat?: boolean;
  }
>(function NumericFormatCustom(props, ref) {
  const { onChange, min, max, allowFloat = true, ...other } = props;

  return (
    <NumericFormat
      {...other}
      getInputRef={ref}
      decimalScale={allowFloat ? undefined : 0}
      isAllowed={(values) => {
        const { floatValue } = values;
        return (
          floatValue === undefined ||
          ((min === undefined || floatValue >= min) &&
            (max === undefined || floatValue <= max))
        );
      }}
      onValueChange={(values) => {
        onChange({
          target: {
            name: props.name,
            value: values.value,
          },
        });
      }}
    />
  );
});

const ConductorInputNumber: FunctionComponent<ConductorInputNumberProps> = ({
  label = "",
  value,
  onChange,
  ...restProps
}) => {
  const handleChange = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    const { value } = event.target;
    const returnedValue: number | null = value === "" ? null : Number(value);

    onChange(returnedValue, event);
  };

  return (
    <ConductorInput
      {...restProps}
      label={label}
      value={value || value === 0 ? value.toString() : ""}
      onChange={handleChange}
      InputProps={{
        ...restProps.InputProps,
        inputComponent: NumericFormatCustom as any,
      }}
    />
  );
};

export default ConductorInputNumber;
