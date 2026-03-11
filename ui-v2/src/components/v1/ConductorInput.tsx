import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";
import {
  Box,
  IconButton,
  TextField,
  TextFieldProps,
  Theme,
  useTheme,
} from "@mui/material";
import InputAdornment from "@mui/material/InputAdornment";
import ConductorToolTip, {
  ConductorTooltipProps,
} from "components/conductorTooltip/ConductorTooltip";
import _isEmpty from "lodash/isEmpty";
import {
  ChangeEvent,
  FocusEvent,
  MouseEvent,
  ReactNode,
  Ref,
  forwardRef,
  useContext,
  useRef,
  useState,
} from "react";
import { colors, fontSizes } from "theme/tokens/variables";
import InfoIcon from "./icons/InfoIcon";
import XCloseIcon from "./icons/XCloseIcon";
import { MessageContext } from "./layout/MessageContext/MessageContext";
import { formHelperStyle, inputLabelStyle, labelScale } from "./theme/styles";
import { getColor } from "./theme/theme";

export type ConductorInputStyleProps = {
  theme: Theme;
  isFocused?: boolean;
  error?: boolean;
  multiline?: boolean;
  disabled?: boolean;
  isLabel?: boolean;
  isInputEmpty?: boolean;
};

const inputStyle = ({
  theme,
  error,
  isFocused,
  disabled,
}: ConductorInputStyleProps) => ({
  backgroundColor: colors.white,
  fontSize: fontSizes.fontSize3,
  fontWeight: 200,
  color: error ? theme.palette.input.error : theme.palette.input.text,
  minHeight: "unset",

  // Remove autofill background's input
  "& input:-webkit-autofill": {
    WebkitBoxShadow: "0 0 0 100px #ffffff inset", // Set the box-shadow to none
  },

  "&.MuiOutlinedInput-root": {
    ".MuiInputBase-input": {
      padding: "14px 8px 8px 8px",
      overflow: "hidden",
      textOverflow: "ellipsis",
      whiteSpace: "nowrap",
      "&.Mui-disabled": {
        WebkitTextFillColor: theme.palette.input.label,
      },
    },
    ".MuiInputBase-inputMultiline": {
      whiteSpace: "pre-wrap",
      overflow: "auto",
      textOverflow: "unset",
      p: 0,
    },
    ".MuiOutlinedInput-notchedOutline": {
      borderWidth: 1,
      borderStyle: "solid",
      borderRadius: "4px",
      borderColor: getColor({ theme, isFocused, error }),

      // This will make the legend has same size with the label
      "& legend": {
        maxWidth: "100%",
        fontSize: `${labelScale}em`,
        fontWeight: isFocused ? 500 : "unset",
      },
    },

    "&.Mui-focused.MuiOutlinedInput-notchedOutline": {
      borderWidth: 1,
    },

    "&:hover fieldset": disabled
      ? null
      : {
          borderColor: theme.palette.input.focus,
        },

    "&.Mui-focused": {
      backgroundColor: disabled ? colors.lightGrey : colors.white,
    },

    "&.Mui-disabled": {
      WebkitTextFillColor: theme.palette.input.label,
      borderColor: getColor({ theme }),
      backgroundColor: colors.lightGrey,
    },

    "&.MuiInputBase-multiline": {
      p: "14px 8px 8px 8px",
    },
  },

  "& ::placeholder": {
    color: colors.greyText2,
  },

  ".MuiSelect-select": {
    "&.MuiInputBase-input": {
      pr: "32px",

      "&:focus": {
        backgroundColor: disabled ? colors.lightGrey : colors.white,
        borderRadius: "4px 0 0 4px",
      },
    },
  },
});

export const MaybeTooltipLabel = ({
  tooltip,
  label,
  required,
}: {
  tooltip?: Omit<ConductorTooltipProps, "children">;
  label: ReactNode;
  required?: boolean;
}) => (
  <>
    {tooltip ? (
      <ConductorToolTip placement="top" {...tooltip}>
        <Box sx={{ display: "inline-block" }}>
          {label}
          <Box component="span" sx={{ ml: "3px" }}>
            <InfoIcon size={14} />
          </Box>
          {required && label && "*"}
        </Box>
      </ConductorToolTip>
    ) : (
      label
    )}
  </>
);

const CustomEndAdornment = ({
  clearValue,
  disabled,
  handleClickShowSecret,
  handleCopyValue,
  handleMouseDownSecret,
  isSecret,
  multiline,
  showClearButton,
  showSecret,
  value,
}: {
  clearValue: () => void;
  disabled?: boolean;
  handleClickShowSecret: () => void;
  handleCopyValue: () => void;
  handleMouseDownSecret: (event: MouseEvent<HTMLButtonElement>) => void;
  isSecret?: boolean;
  multiline?: boolean;
  showClearButton?: boolean;
  showSecret?: boolean;
  value: unknown;
}) => (
  <InputAdornment position="end">
    {showClearButton ? (
      <IconButton
        aria-label="clear value"
        onClick={clearValue}
        edge="end"
        sx={{
          visibility: !disabled && !!value ? "visible" : "hidden",
        }}
      >
        <XCloseIcon />
      </IconButton>
    ) : null}

    {isSecret && !!value ? (
      <IconButton
        aria-label="copy value to clipboard"
        onClick={handleCopyValue}
        edge="end"
      >
        <ContentCopyOutlinedIcon />
      </IconButton>
    ) : null}

    {!multiline && isSecret ? (
      <IconButton
        aria-label="toggle secret visibility"
        onClick={handleClickShowSecret}
        onMouseDown={handleMouseDownSecret}
        edge="end"
      >
        {showSecret ? <VisibilityOffIcon /> : <VisibilityIcon />}
      </IconButton>
    ) : null}
  </InputAdornment>
);

type ConductorInputProps = Omit<TextFieldProps, "ref"> & {
  onTextInputChange?: (value: string) => void;
  isSecret?: boolean;
  showClearButton?: boolean;
  tooltip?: Omit<ConductorTooltipProps, "children">;
};

const ConductorInput = forwardRef(
  (
    {
      label,
      placeholder,
      autoFocus,
      required,
      onBlur,
      onChange,
      onTextInputChange,
      onFocus,
      multiline,
      isSecret,
      fullWidth, // FIXME: just fixed this the prop was not passed down this may affect modals
      error,
      helperText,
      showClearButton,
      tooltip,
      value,
      InputProps,
      disabled,
      ...rest
    }: ConductorInputProps,
    ref: Ref<HTMLDivElement>,
  ) => {
    const theme = useTheme();
    const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null);
    const { setMessage } = useContext(MessageContext);
    const [isFocused, setIsFocused] = useState(autoFocus);
    const [showSecret, setShowSecret] = useState(false);

    const handleFocus = (
      event: FocusEvent<HTMLInputElement | HTMLTextAreaElement, Element>,
    ) => {
      setIsFocused(true);

      if (onFocus) {
        onFocus(event);
      }
    };

    const handleBlur = (
      event: FocusEvent<HTMLInputElement | HTMLTextAreaElement, Element>,
    ) => {
      setIsFocused(false);

      if (onBlur) {
        onBlur(event);
      }
    };

    const handleClickShowSecret = () => {
      setShowSecret((show) => !show);

      if (inputRef.current) {
        inputRef.current.focus();
      }
    };

    const handleMouseDownSecret = (event: MouseEvent<HTMLButtonElement>) => {
      event.preventDefault();
    };

    const handleCopyValue = () => {
      const currentValue = inputRef.current?.value || "";

      if (currentValue) {
        setMessage({
          text: "Copied to Clipboard",
          severity: "success",
        });
        navigator.clipboard.writeText(currentValue);
      }
    };

    const handleChange = (
      event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
    ) => {
      const { value } = event.target;

      if (onChange) {
        onChange(event);
      }

      if (onTextInputChange) {
        onTextInputChange(value);
      }
    };

    const clearValue = () => {
      if (onChange) {
        onChange({ target: { value: "" } } as ChangeEvent<
          HTMLInputElement | HTMLTextAreaElement
        >);
      }

      if (onTextInputChange) {
        onTextInputChange("");
      }

      if (inputRef.current) {
        inputRef.current.focus();
      }
    };

    const getCustomEndAdornment = () => {
      if (InputProps?.endAdornment) {
        return InputProps.endAdornment;
      }

      if (showClearButton || isSecret) {
        return (
          <CustomEndAdornment
            value={value}
            multiline={multiline}
            showSecret={showSecret}
            showClearButton={showClearButton}
            isSecret={isSecret}
            clearValue={clearValue}
            disabled={disabled}
            handleCopyValue={handleCopyValue}
            handleClickShowSecret={handleClickShowSecret}
            handleMouseDownSecret={handleMouseDownSecret}
          />
        );
      }

      return undefined;
    };

    const isInputEmpty = _isEmpty(value) && _isEmpty(rest.defaultValue);

    return (
      <TextField
        ref={ref}
        inputRef={inputRef}
        placeholder={placeholder}
        autoFocus={autoFocus}
        required={required}
        disabled={disabled}
        multiline={multiline}
        error={error}
        fullWidth={fullWidth}
        value={value}
        onFocus={handleFocus}
        onBlur={handleBlur}
        onChange={handleChange}
        type={showSecret || !isSecret ? "text" : "password"}
        label={
          label ? (
            <MaybeTooltipLabel label={label} tooltip={tooltip} />
          ) : undefined
        }
        InputLabelProps={{
          sx: inputLabelStyle({
            theme,
            isFocused,
            isInputEmpty,
            error,
            disabled,
          }),
          shrink: true,
        }}
        InputProps={{
          ...InputProps,
          endAdornment: getCustomEndAdornment(),
          sx: [
            inputStyle({
              theme,
              error,
              multiline,
              isFocused,
              disabled,
            }),
          ],
        }}
        helperText={helperText}
        FormHelperTextProps={{
          sx: formHelperStyle({
            theme,
            isFocused,
            isInputEmpty,
            error,
          }),
        }}
        {...rest}
      />
    );
  },
);

export type { ConductorInputProps };
export default ConductorInput;
