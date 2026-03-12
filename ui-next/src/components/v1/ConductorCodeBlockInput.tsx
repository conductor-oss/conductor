import { EditorProps } from "@monaco-editor/react";
import {
  Box,
  BoxProps,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
} from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import { FunctionComponent, ReactNode, useCallback, useState } from "react";

import { ConductorTooltipProps } from "components/conductorTooltip/ConductorTooltip";

import { CodeBlockInputWrapper } from "./CodeBlockInputWrapper";
import Close from "@mui/icons-material/Close";

export type ConductorCodeBlockInputProps = {
  label?: ReactNode;
  helperText?: string;
  language?: string;
  onChange?: (value: string) => void;
  value?: string;
  containerProps?: BoxProps;
  error?: boolean;
  height?: number | "auto";
  minHeight?: number;
  autoformat?: boolean;
  labelStyle?: SxProps<Theme>;
  languageLabel?: string;
  containerStyles?: SxProps<Theme>;
  required?: boolean;
  disabled?: boolean;
  tooltip?: Omit<ConductorTooltipProps, "children">;
  enableCopy?: boolean;
  autoFocus?: boolean;
  showLangLabel?: boolean;
} & Partial<Omit<EditorProps, "onChange">>;

const MIN_HEIGHT = 120;

export const ConductorCodeBlockInput: FunctionComponent<
  ConductorCodeBlockInputProps
> = ({
  label = "Code",
  language = "json",
  onChange = () => null,
  onMount,
  value = "",
  containerProps = {},
  error = false,
  minHeight = MIN_HEIGHT,
  autoformat = true,
  languageLabel,
  containerStyles = {},
  required,
  tooltip,
  disabled,
  enableCopy = true,
  options,
  helperText,
  autoFocus = false,
  showLangLabel = true,
  ...restOfProps
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const handleExpandToggle = useCallback(() => {
    setIsExpanded(!isExpanded);
  }, [isExpanded]);

  const codeBlockWrapper = (
    <CodeBlockInputWrapper
      containerProps={containerProps}
      containerStyles={containerStyles}
      label={label}
      language={language}
      languageLabel={languageLabel}
      error={error}
      value={value}
      minHeight={minHeight}
      disabled={disabled}
      required={required}
      tooltip={tooltip}
      enableCopy={enableCopy}
      onChange={onChange}
      onMount={onMount}
      autoformat={autoformat}
      autoFocus={autoFocus}
      options={options}
      editorProps={restOfProps}
      helperText={helperText}
      onExpand={handleExpandToggle}
      isExpanded={isExpanded}
      showLangLabel={showLangLabel}
    />
  );

  return (
    <>
      {isExpanded ? (
        <Dialog
          open={isExpanded}
          onClose={handleExpandToggle}
          maxWidth="md"
          fullWidth
        >
          <DialogTitle>
            Code Editor
            <IconButton
              onClick={() => setIsExpanded(false)}
              sx={{
                position: "absolute",
                right: 8,
                top: 8,
              }}
            >
              <Close />
            </IconButton>
          </DialogTitle>
          <DialogContent>
            <Box sx={{ mt: 4 }}>{codeBlockWrapper}</Box>
          </DialogContent>
        </Dialog>
      ) : (
        codeBlockWrapper
      )}
    </>
  );
};
