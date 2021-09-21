import React, { useRef } from "react";
import { TextField, InputAdornment, IconButton } from "@material-ui/core";
import ClearIcon from "@material-ui/icons/Clear";

export default function ({ label, clearable, onBlur, onChange, ...props }) {
  const inputRef = useRef();

  function handleClear() {
    inputRef.current.value = "";
    if (onBlur) return onBlur("");
    if (onChange) return onChange("");
  }

  function handleBlur(e) {
    if (onBlur) onBlur(e.target.value);
  }

  function handleChange(e) {
    if (onChange) onChange(e.target.value);
  }

  return (
    <TextField
      label={label}
      inputRef={inputRef}
      InputProps={{
        endAdornment: clearable && (
          <InputAdornment position="end" style={{ marginRight: -8 }}>
            <IconButton size="small" onClick={handleClear}>
              <ClearIcon />
            </IconButton>
          </InputAdornment>
        ),
      }}
      onBlur={handleBlur}
      onChange={handleChange}
      {...props}
    />
  );
}
