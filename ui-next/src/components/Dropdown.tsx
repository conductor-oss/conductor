import CancelOutlinedIcon from "@mui/icons-material/CancelOutlined";
import {
  Autocomplete,
  AutocompleteProps,
  CSSObject,
  Chip,
  FormControl,
  TextFieldPropsSizeOverrides,
  Theme,
} from "@mui/material";
import { OverridableStringUnion } from "@mui/types";
import { ForwardedRef, ReactNode, forwardRef, useEffect, useRef } from "react";
import { colors } from "theme/tokens/variables";
import Input, { CustomInputProps } from "./Input";

const autocompleteStyle = {
  ".MuiAutocomplete-popupIndicator": {
    color: (theme: Theme) =>
      theme.palette.mode === "dark" ? colors.white : colors.black,
  },
};

// Define the option types more clearly
type DropdownOption = string | number | { label: string };

// Simplified approach that works better with MUI's complex generics
type DropdownProps = Omit<
  AutocompleteProps<
    DropdownOption,
    boolean | undefined,
    boolean | undefined,
    boolean | undefined
  >,
  "renderInput" | "onInputChange" | "options"
> & {
  onInputChange?: (value: string) => void;
  label?: ReactNode;
  style?: CSSObject;
  error?: boolean;
  size?: OverridableStringUnion<
    "small" | "medium",
    TextFieldPropsSizeOverrides
  >;
  helperText?: ReactNode;
  inputProps?: CustomInputProps;
  required?: boolean;
  options?: readonly DropdownOption[];
};

const Dropdown = forwardRef(
  (
    {
      label,
      className,
      style,
      error,
      size,
      helperText,
      inputProps,
      required,
      autoFocus,
      onInputChange,
      options,
      ...props
    }: DropdownProps,
    ref: ForwardedRef<HTMLDivElement>,
  ) => {
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
      if (autoFocus && inputRef.current?.focus) {
        inputRef.current.focus();
      }
    }, [autoFocus]);

    const handleInputChange = (typingValue: string) => {
      if (onInputChange) {
        onInputChange(typingValue);
      }
    };

    const isRequired =
      required &&
      (!props?.multiple ||
        (Array.isArray(props?.value) && props?.value?.length === 0));

    const { InputProps: inputPropsInputProps, ...restInputProps } =
      inputProps || { InputProps: {} };

    return (
      <FormControl style={style} className={className} fullWidth>
        <Autocomplete
          ref={ref}
          sx={autocompleteStyle}
          renderInput={({ InputProps, ...restParams }) => (
            <Input
              {...restParams}
              label={label}
              error={error}
              size={size}
              onChange={handleInputChange}
              helperText={helperText}
              required={isRequired}
              inputRef={inputRef}
              InputProps={{ ...InputProps, ...inputPropsInputProps }}
              {...restInputProps}
            />
          )}
          renderTags={(value, getTagProps) =>
            (value as DropdownOption[]).map((v, index) => {
              const renderableLabel: string =
                typeof v === "string" || typeof v === "number"
                  ? String(v)
                  : v.label;
              const { key, ...otherTagProps } = getTagProps({ index });
              return (
                <Chip
                  key={key}
                  label={renderableLabel}
                  {...otherTagProps}
                  sx={{
                    marginTop: "1px",
                    marginBottom: "1px",
                    backgroundColor: "rgba(221, 221, 221, 1)",
                    color: "#000",
                    borderRadius: "30px",
                    "& .MuiSvgIcon-root": {
                      background: "transparent",
                      fill: "black",
                    },
                  }}
                  deleteIcon={<CancelOutlinedIcon />}
                />
              );
            })
          }
          options={options || []}
          {...props}
        />
      </FormControl>
    );
  },
);

export default Dropdown;
