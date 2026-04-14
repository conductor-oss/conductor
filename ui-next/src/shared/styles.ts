import { Theme } from "@mui/material";
import { baseLabelStyle } from "theme/styles";
import { isEmpty as _isEmpty } from "lodash";
import { greyText2, lightGrey } from "theme/tokens/colors";

export const disabledInputStyle = {
  "& .MuiOutlinedInput-root.Mui-disabled .MuiOutlinedInput-notchedOutline": {
    borderColor: greyText2,
    backgroundColor: lightGrey,
  },
};

export const dateRangePickerStyle = {
  wrapper: {
    display: "flex",
  },
  input: {
    ">div": { width: "100%" },
    ...disabledInputStyle,
  },
};
export const autocompleteStyle = ({ value }: { value: any }) => ({
  ".MuiTextField-root": {
    ".MuiOutlinedInput-root": {
      pt: "14px",
      pl: "8px",
      pb: "8px",
      ".MuiAutocomplete-input": {
        p: 0,
      },
    },
    ".MuiInputLabel-root": {
      ...(baseLabelStyle as any),
      color: (theme: Theme) =>
        _isEmpty(value) ? theme.palette.input.text : theme.palette.label.text,
      "&.Mui-focused": {
        fontWeight: 500,
        color: (theme: Theme) => theme.palette.input.focus,
      },
      "&.Mui-disabled": {
        color: (theme: Theme) => theme.palette.label.disabled,
      },
      "&.Mui-error": {
        color: (theme: Theme) => theme.palette.input.error,
      },
    },
  },
});

export const customButtonStyle = {
  color: "#000",
  "&:hover": { background: "#0505050a" },
};
