import { Box, IconButton, Tooltip } from "@mui/material";
import {
  ReactNode,
  useState,
  useMemo,
  cloneElement,
  isValidElement,
} from "react";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { useCoerceToObject } from "utils/utils";
import type { ConductorAutocompleteVariablesProps } from "./ConductorAutocompleteVariables";
import { TextTIcon } from "@phosphor-icons/react";
import ConductorInput from "components/ui/inputs/ConductorInput";

export type ConductorStringOrJsonInputProps = Omit<
  ConductorAutocompleteVariablesProps,
  "onChange" | "value"
> & {
  value: string | Record<string, unknown>;
  onChange: (value: string | number | boolean | null) => void;
  helperText?: string;
  error?: boolean;
  customInput?: ReactNode;
  showFieldTypes: boolean;
  placeholder?: string;
};

const JsonIcon = ({ size = 14 }: { size?: number }) => (
  <Box
    component="span"
    sx={{
      fontSize: `${size}px`,
      fontWeight: 500,
      fontFamily: "monospace",
      lineHeight: 1,
      display: "inline-block",
    }}
  >
    {"{}"}
  </Box>
);

type ToggleButtonProps = {
  isJsonMode: boolean;
  onToggle: () => void;
};

const ToggleButton = ({ isJsonMode, onToggle }: ToggleButtonProps) => (
  <Box
    sx={{
      position: "absolute",
      top: "-10px",
      right: "8px",
    }}
  >
    <Tooltip title={isJsonMode ? "Switch to text" : "Switch to JSON"}>
      <IconButton
        size="small"
        onClick={onToggle}
        sx={{
          padding: "2px",
          minWidth: "20px",
          width: "20px",
          height: "20px",
          backgroundColor: "background.paper",
          border: "1px solid",
          borderColor: "divider",
          "&:hover": {
            backgroundColor: "background.paper",
            borderColor: "primary.main",
          },
          "&:active": {
            backgroundColor: "action.selected",
          },
        }}
      >
        {isJsonMode ? (
          <TextTIcon weight="bold" size={20} />
        ) : (
          <JsonIcon size={13} />
        )}
      </IconButton>
    </Tooltip>
  </Box>
);

export const ConductorStringOrJsonInput = ({
  value,
  onChange,
  label,
  helperText,
  error = false,
  customInput,
  showFieldTypes,
  placeholder = "e.g.: max-age=...",
}: ConductorStringOrJsonInputProps) => {
  // Determine if value is JSON/object
  const isValueJson = useMemo(() => {
    if (value == null || value === "") return false;
    if (typeof value === "object") return true;
    return false;
  }, [value]);

  const [isJsonMode, setIsJsonMode] = useState(isValueJson);

  const stringifyJson = (value: string | Record<string, unknown>) => {
    return JSON.stringify(value, null, 2);
  };

  // Get string value for text input
  const stringValue = useMemo(() => {
    if (typeof value === "object") {
      return stringifyJson(value);
    }
    const strValue = value == null ? "" : String(value);

    // If the value is a JSON stringified string (starts and ends with quotes), parse it
    if (strValue.startsWith('"') && strValue.endsWith('"')) {
      try {
        const parsed = JSON.parse(strValue);
        if (typeof parsed === "string") {
          // Display with quotes to indicate it's a string
          return `"${parsed}"`;
        }
      } catch {
        // Not valid JSON, return as-is
      }
    }

    // For numbers and booleans, display without quotes
    // For other strings, display as-is
    return strValue;
  }, [value]);

  const [onObjChange, objValue, cantCoerce] = useCoerceToObject(
    onChange,
    value,
  );

  const handleStringChange = (newValue: string | number) => {
    const strValue = String(newValue).trim();

    // Empty string
    if (strValue === "") {
      onChange("");
      return;
    }

    // If the value is quoted (starts and ends with quotes), treat as string
    if (
      (strValue.startsWith('"') && strValue.endsWith('"')) ||
      (strValue.startsWith("'") && strValue.endsWith("'"))
    ) {
      // Remove quotes and pass as JSON stringified string
      const unquoted = strValue.slice(1, -1);
      onChange(unquoted);
      return;
    }

    // Try to parse as boolean (case-insensitive)
    const lowerValue = strValue.toLowerCase();
    if (lowerValue === "true" || lowerValue === "false") {
      onChange(lowerValue === "true");
      return;
    }
    if (strValue === "null") {
      onChange(null);
      return;
    }

    // Try to parse as number
    // Use a regex to check if it's a valid number format (integers, decimals, scientific notation)
    const numberRegex = /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/;
    if (numberRegex.test(strValue)) {
      const numValue = Number(strValue);
      if (!isNaN(numValue) && isFinite(numValue)) {
        onChange(numValue); // Pass as number string (valid JSON)
        return;
      }
    }

    if (strValue === "{}" || strValue === "[]") {
      onChange(JSON.parse(strValue));
      setIsJsonMode(true);
      return;
    }
    onChange(strValue);
  };

  const handleToggleMode = () => {
    onChange("");
    setIsJsonMode(!isJsonMode);
  };

  if (isJsonMode) {
    const jsonValue = stringifyJson(value);
    const isEmptyJsonValue = jsonValue === "{}" || jsonValue === "[]";
    return (
      <Box sx={{ position: "relative" }}>
        <ConductorCodeBlockInput
          label={label}
          value={isEmptyJsonValue ? jsonValue : objValue}
          onChange={onObjChange}
          error={error || cantCoerce}
          helperText={helperText}
          language="json"
          minHeight={120}
          autoformat={true}
          showLangLabel={false}
        />
        {showFieldTypes && (
          <ToggleButton isJsonMode={isJsonMode} onToggle={handleToggleMode} />
        )}
      </Box>
    );
  }

  const renderCustomInput = () => {
    if (!customInput) return null;

    if (isValidElement(customInput)) {
      const existingProps = customInput.props || {};
      return cloneElement(customInput, {
        ...existingProps,
        onChange: handleStringChange,
        onTextInputChange: handleStringChange,
        value: stringValue,
      } as Record<string, unknown>);
    }

    return customInput;
  };

  return (
    <Box sx={{ position: "relative" }}>
      {customInput ? (
        renderCustomInput()
      ) : (
        <ConductorInput
          fullWidth
          onTextInputChange={handleStringChange}
          value={stringValue}
          helperText={helperText}
          label={label}
          showClearButton
          error={error}
          placeholder={placeholder}
        />
      )}

      {showFieldTypes && (
        <ToggleButton isJsonMode={isJsonMode} onToggle={handleToggleMode} />
      )}
    </Box>
  );
};
