import {
  Box,
  InputAdornment,
  TextField,
  TextFieldProps,
  IconButton,
  Theme,
  SxProps,
} from "@mui/material";
import { forwardRef, useState, ChangeEvent, useRef, Ref } from "react";
import { blueLightMode } from "theme/tokens/colors";
import XCloseIcon from "../../icons/XCloseIcon";

const style = {
  inputStyle: {
    "& .MuiOutlinedInput-notchedOutline, & .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline":
      {
        border: "none",
      },
    "& .MuiTextField-root, & .MuiInputBase-root ": {
      fontSize: "12px",
      minHeight: "30px",
      borderRadius: "20px",
      padding: "0px",
      px: "4px",
      background: (theme: Theme) => theme.palette.input.background,
    },
    "& .MuiInputAdornment-positionStart": {
      width: "12px",
    },
    "& .MuiInputAdornment-positionEnd": {
      paddingRight: "4px",
    },
  },
};

export type RoundedInputProps = Omit<TextFieldProps, "onBlur" | "onChange"> & {
  placeholder?: string;
  autoFocus?: boolean;
  required?: boolean;
  multiline?: boolean;
  onBlur?: (value: string) => void;
  onChange?: (value: string) => void;
  icon?: any;
  clearButton?: boolean;
  textFieldSx?: SxProps<Theme>;
};

export const RoundedInput = forwardRef(
  (
    {
      placeholder,
      autoFocus,
      onBlur = () => null,
      multiline,
      onChange = () => null,
      icon,
      clearButton = true,
      textFieldSx,
      ...rest
    }: RoundedInputProps,
    ref: Ref<HTMLDivElement>,
  ) => {
    const [isFocused, setIsFocused] = useState(autoFocus);
    const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null);
    const handleFocus = () => {
      setIsFocused(true);
    };

    const handleBlur = (event: any) => {
      setIsFocused(false);
      if (typeof onBlur === "function") {
        const { value } = event.target;
        onBlur(value);
      }
    };

    const handleChange = (
      event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
    ) => {
      if (onChange) {
        const { value } = event.target;
        onChange(value);
      }
    };

    const clearValue = () => {
      if (onChange) {
        onChange("");
      }
      if (inputRef.current !== null) {
        inputRef.current.focus();
      }
    };
    return (
      <Box sx={{ overflow: "hidden" }}>
        <TextField
          ref={ref}
          inputRef={inputRef}
          sx={{
            " & .MuiInputBase-root": {
              height: multiline ? "auto" : "30px",
              border: isFocused
                ? `1px solid ${blueLightMode}`
                : "1px solid rgba(175, 175, 175, 1)",
            },
            " & .MuiOutlinedInput-notchedOutline": {
              border: "none",
            },
            ...(textFieldSx ? textFieldSx : style.inputStyle),
          }}
          placeholder={placeholder}
          autoFocus={autoFocus}
          multiline={multiline}
          fullWidth
          onFocus={handleFocus}
          onBlur={handleBlur}
          onChange={handleChange}
          InputProps={{
            ...(icon && {
              startAdornment: (
                <InputAdornment position="start">{icon}</InputAdornment>
              ),
            }),
            ...(clearButton && {
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label="clear value"
                    onClick={clearValue}
                    edge="end"
                  >
                    <XCloseIcon color={blueLightMode} />
                  </IconButton>
                </InputAdornment>
              ),
            }),
          }}
          {...rest}
        />
      </Box>
    );
  },
);
