import InputAdornment from "@mui/material/InputAdornment";
import TextField, { TextFieldProps } from "@mui/material/TextField";
import { X as ClearIcon } from "@phosphor-icons/react";
import IconButton from "components/ui/buttons/MuiIconButton";
import { ChangeEvent, forwardRef, useImperativeHandle, useRef } from "react";
import { disabledInputStyle } from "shared/styles";

export type CustomInputProps = Omit<TextFieldProps, "onBlur" | "onChange"> & {
  clearable?: boolean;
  onBlur?: (value: string) => void;
  onChange?: (value: string) => void;
};

const CustomInput = forwardRef(
  (
    {
      label = null,
      clearable,
      onBlur = () => null,
      onChange = () => null,
      value,
      ...props
    }: CustomInputProps,
    ref,
  ) => {
    const inputRef = useRef<HTMLInputElement | null>(null);
    useImperativeHandle(ref, () => inputRef?.current, [inputRef]);

    function handleClear(_e: any) {
      if (inputRef.current?.value) {
        inputRef.current.value = "";
      }

      if (onBlur) {
        onBlur("");
      }

      if (onChange) {
        onChange("");
      }
    }

    function handleBlur(
      e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
    ) {
      if (onBlur) {
        const { value } = e.target;

        onBlur(value);
      }
    }

    function handleChange(
      e: ChangeEvent<HTMLTextAreaElement | HTMLInputElement>,
    ) {
      if (onChange) {
        const { value } = e.target;

        onChange(value);
      }
    }

    return (
      <TextField
        label={label ? label : null}
        inputRef={inputRef}
        sx={{
          " & .MuiInputBase-root": {
            paddingLeft: "0px",
          },
          ...disabledInputStyle,
        }}
        InputProps={{
          endAdornment:
            clearable && value ? (
              <InputAdornment
                position="end"
                sx={{
                  marginRight: -2,
                }}
              >
                <IconButton
                  size="small"
                  onClick={handleClear}
                  disabled={props.disabled}
                  sx={{
                    color: (theme) =>
                      theme.palette.mode === "dark" ? "#fff" : null,
                  }}
                >
                  <ClearIcon />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          autoFocus: props.autoFocus,
        }}
        onBlur={handleBlur}
        onChange={handleChange}
        value={value}
        {...props}
      />
    );
  },
);

export default CustomInput;
