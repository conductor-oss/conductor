import { Chip } from "@mui/material";
import Autocomplete, {
  AutocompleteProps,
  AutocompleteRenderInputParams,
} from "@mui/material/Autocomplete";
import TextField, { TextFieldProps } from "@mui/material/TextField";
import { SyntheticEvent } from "react";

interface Option {
  value: string;
  label: string;
  color: string;
}

interface MultiOptionSelectProps extends Omit<
  AutocompleteProps<Option, true, false, false>,
  "renderInput"
> {
  options: Option[];
  label: string;
  autoFocus?: boolean;
  error?: boolean;
  required?: boolean;
  helperText?: string;
  value: Option[];
  onChange: (_event: SyntheticEvent<Element, Event>, value: Option[]) => void;
  inputProps?: TextFieldProps["inputProps"];
}

const MultiOptionSelect = ({
  options,
  label,
  autoFocus,
  error,
  required,
  helperText,
  value,
  onChange,
  inputProps,
  ...props
}: MultiOptionSelectProps) => {
  return (
    <Autocomplete
      multiple
      options={options}
      getOptionLabel={(option: Option) => option.label}
      value={value}
      onChange={onChange}
      filterSelectedOptions
      renderTags={(value: readonly Option[], getTagProps) =>
        value.map((option: Option, index: number) => {
          const { key, ...otherTagProps } = getTagProps({ index });
          return (
            <Chip
              key={key}
              label={option.label}
              {...otherTagProps}
              sx={{
                backgroundColor: option.color,
                borderRadius: "20px",
                ".MuiSvgIcon-root": { borderRadius: "20px" },
              }}
            />
          );
        })
      }
      renderInput={(params: AutocompleteRenderInputParams) => (
        <TextField
          {...params}
          label={label}
          error={error}
          required={required}
          helperText={helperText}
          inputProps={{
            ...params.inputProps,
            ...inputProps,
          }}
          autoFocus={autoFocus}
        />
      )}
      {...props}
    />
  );
};

export type { MultiOptionSelectProps };
export default MultiOptionSelect;
