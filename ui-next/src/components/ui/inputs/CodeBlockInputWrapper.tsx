import { Editor, EditorProps, Monaco, OnMount } from "@monaco-editor/react";
import {
  Box,
  BoxProps,
  InputLabel,
  Stack,
  Typography,
  useTheme,
} from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import { ArrowsInSimple } from "@phosphor-icons/react";
import IconButton from "components/ui/buttons/MuiIconButton";
import MuiTypography from "components/ui/MuiTypography";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { MaybeTooltipLabel } from "components/ui/inputs/ConductorInput";
import { labelScale } from "theme/styles";
import CopyIcon from "components/icons/CopyIcon";
import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
import _isEmpty from "lodash/isEmpty";
import {
  ReactNode,
  RefObject,
  useCallback,
  useContext,
  useRef,
  useState,
} from "react";
import {
  editor,
  defaultEditorOptions,
  type EditorOptions,
} from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors, fontSizes } from "theme/tokens/variables";
import { logger } from "utils/logger";
import ExpandIcon from "../../icons/ExpandIcon";
import { inputLabelStyle } from "theme/styles";
import { getColor } from "theme/theme";

export interface CodeBlockInputWrapperHandle {
  handleCopyValue: () => boolean;
}

const A_MARGIN_THREASHHOLD = 22;
const IDLE_MINIMUM_VALUE_IF_FAIL_TO_GET_REF = 500;

const DEFAULT_CONTAINER_PROPS = {};
const DEFAULT_CONTAINER_STYLES = {};
const DEFAULT_OPTIONS = {};
const DEFAULT_EDITOR_PROPS = {};

const MaybeLabel = ({
  label,
  required,
  tooltip,
}: {
  label?: ReactNode;
  required?: boolean;
  tooltip?: Omit<ConductorTooltipProps, "children">;
}) => (
  <MaybeTooltipLabel
    label={required ? `${label} *` : label}
    required={required}
    tooltip={tooltip}
  />
);

const smallEditorOptions: EditorOptions = {
  ...defaultEditorOptions,
  tabSize: 2,
  minimap: { enabled: false },
  lightbulb: { enabled: editor.ShowLightbulbIconMode.On },
  quickSuggestions: true,
  lineNumbers: "on",
  glyphMargin: false,
  folding: false,
  // Undocumented see https://github.com/Microsoft/vscode/issues/30795#issuecomment-410998882
  lineDecorationsWidth: 10,
  lineNumbersMinChars: 0,
  renderLineHighlight: "none",
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: false,
  scrollbar: {
    vertical: "hidden",
    // this property is added because it was not allowing us to scroll when mouse pointer is over this component
    alwaysConsumeMouseWheel: false,
  },
  overviewRulerBorder: false,
  automaticLayout: true, // Important
  scrollBeyondLastLine: false,
  wrappingStrategy: "advanced",
  wordWrap: "on",
};

interface CodeBlockInputWrapperProps {
  containerProps?: BoxProps;
  containerStyles?: SxProps<Theme>;
  label?: ReactNode;
  language?: string;
  languageLabel?: string;
  error?: boolean;
  value?: string;
  minHeight: number;
  disabled?: boolean;
  required?: boolean;
  tooltip?: Omit<ConductorTooltipProps, "children">;
  enableCopy?: boolean;
  onChange?: (value: string) => void;
  onMount?: OnMount;
  autoformat: boolean;
  autoFocus: boolean;
  options?: EditorProps["options"];
  editorProps?: Partial<EditorProps>;
  helperText?: string;
  onExpand?: () => void;
  isExpanded?: boolean;
  showLangLabel: boolean;
}

const handleUpdateHeight = (
  editor: Monaco,
  boxRef: RefObject<HTMLDivElement | null>,
  minHeight: number,
) => {
  const parentComponent = boxRef?.current;
  if (!parentComponent) return;

  const contentHeight = Math.max(minHeight, editor.getContentHeight());
  let contentWidth = IDLE_MINIMUM_VALUE_IF_FAIL_TO_GET_REF;

  if (parentComponent) {
    contentWidth = parentComponent.offsetWidth;
    const editorSectionElement = parentComponent.querySelector("section");

    if (editorSectionElement) {
      editorSectionElement.style.height = "100%";
    }
  }

  try {
    editor.layout({
      width: contentWidth - A_MARGIN_THREASHHOLD,
      height: contentHeight,
    });
  } catch (error) {
    logger.error("[handleEditorDidMount]: error", error);
  }
};

export const CodeBlockInputWrapper = ({
  containerProps = DEFAULT_CONTAINER_PROPS,
  containerStyles = DEFAULT_CONTAINER_STYLES,
  label,
  language = "json",
  languageLabel,
  error = false,
  value = "",
  minHeight,
  disabled = false,
  required = false,
  tooltip,
  enableCopy = true,
  onChange,
  onMount,
  autoformat = true,
  autoFocus = false,
  options = DEFAULT_OPTIONS,
  editorProps = DEFAULT_EDITOR_PROPS,
  helperText,
  onExpand,
  isExpanded = false,
  showLangLabel,
}: CodeBlockInputWrapperProps) => {
  const theme = useTheme();
  const { mode } = useContext(ColorModeContext);
  const [isFocused, setIsFocused] = useState(false);
  const [showCopyAlert, setShowCopyAlert] = useState(false);

  const boxRef = useRef<HTMLDivElement | null>(null);
  const editorRef = useRef<Monaco>(null);

  const handleEditorDidMount = useCallback(
    (editor: Monaco, monaco?: unknown) => {
      editorRef.current = editor;

      if (onMount) {
        onMount(editor, monaco);
      }

      if (autoformat) {
        editor.onDidBlurEditorWidget(() => {
          editor.getAction("editor.action.formatDocument").run();
        });
      }

      if (autoFocus) {
        editor.focus();
        setIsFocused(true);
      }
      const updateHeight = () => handleUpdateHeight(editor, boxRef, minHeight);

      editor.onDidContentSizeChange(updateHeight);
      updateHeight();

      editor.onDidFocusEditorText(() => setIsFocused(true));
      editor.onDidBlurEditorText(() => setIsFocused(false));
    },
    [onMount, autoformat, autoFocus, minHeight],
  );

  const handleCopyValue = () => {
    const editorValue = editorRef?.current?.getValue();
    if (editorValue) {
      setShowCopyAlert(true);
      navigator.clipboard.writeText(editorValue);
    }
  };

  const handleEditorChange = useCallback(() => {
    const editorValue = editorRef?.current?.getValue();
    onChange?.(editorValue);
  }, [onChange]);

  return (
    <>
      {showCopyAlert && (
        <SnackbarMessage
          message="Copied to Clipboard"
          severity="success"
          onDismiss={() => setShowCopyAlert(false)}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        />
      )}

      <Box
        {...containerProps}
        ref={boxRef}
        sx={{
          position: "relative",
          borderWidth: 1,
          borderStyle: "solid",
          borderRadius: "4px",
          borderColor: getColor({ theme, isFocused, error }),
          border: label ? "none" : null,
          background: disabled ? colors.lightGrey : colors.white,
          minHeight: isExpanded ? "40vh" : "fit-content",
          "&:hover": disabled
            ? null
            : {
                color: theme.palette.input.focus,
                borderColor: theme.palette.input.focus,
              },

          "& > section": {
            mt: "-6px",
            pl: "8px",
            borderRadius: "4px",
            resize: "vertical",
            overflow: "visible",
            minHeight: `${minHeight}px`,
          },

          ".monaco-editor": {
            ".scroll-decoration": {
              boxShadow: "none",
            },
            ".suggest-widget": {
              zIndex: 99999,
            },
          },
          ...containerStyles,
        }}
      >
        {label && (
          <>
            <fieldset
              style={{
                fontSize: fontSizes.fontSize3,
                position: "absolute",
                top: -8,
                right: -3,
                bottom: -1,
                left: -2,
                border: "1px solid black",
                borderColor: getColor({ theme, isFocused, error }),
                borderRadius: "4px",
                padding: "0 8px",
                overflow: "hidden",
              }}
            >
              <legend
                style={{
                  display: "block",
                  maxWidth: "100%",
                  maxHeight: "100%",
                  visibility: "hidden",
                  color: getColor({
                    theme,
                    isFocused,
                    error,
                    isLabel: true,
                    isInputEmpty: _isEmpty(value),
                  }),
                  fontSize: `${labelScale}em`,
                  fontWeight: isFocused ? 500 : "unset",
                  transformOrigin: "top left",
                  transform: "translate(0px, 0px)",
                }}
              >
                <MaybeLabel
                  label={label}
                  required={required}
                  tooltip={tooltip}
                />
              </legend>
            </fieldset>

            <InputLabel
              error={error}
              sx={{
                ...inputLabelStyle({
                  theme,
                  isFocused,
                  isInputEmpty: _isEmpty(value),
                  error,
                }),
              }}
            >
              <MaybeLabel label={label} required={required} tooltip={tooltip} />
            </InputLabel>
          </>
        )}
        {showLangLabel && (
          <Stack
            sx={{
              position: "absolute",
              top: "-8px",
              right: "4px",
              zIndex: 1,
            }}
            gap={1}
            flexDirection="row"
          >
            <MuiTypography
              sx={{
                padding: "2px",
                background:
                  "linear-gradient(to top, #ffffff 56%, transparent 50%)",
                fontSize: ".6rem",
                color: theme.palette.input.label,
              }}
            >
              {languageLabel
                ? languageLabel.toUpperCase()
                : language.toUpperCase()}
            </MuiTypography>
          </Stack>
        )}
        <Stack
          sx={{
            position: "absolute",
            bottom: "4px",
            right: "4px",
            zIndex: 1,
          }}
          gap={2}
          flexDirection="row"
        >
          <IconButton
            sx={{ padding: "1px" }}
            onClick={onExpand}
            title={isExpanded ? "Collapse" : "Expand"}
          >
            {isExpanded ? <ArrowsInSimple size={20} /> : <ExpandIcon />}
          </IconButton>
          {enableCopy && (
            <IconButton sx={{ padding: "1px" }} onClick={handleCopyValue}>
              <CopyIcon />
            </IconButton>
          )}
        </Stack>
        <Editor
          theme={mode === "dark" ? "vs-dark" : "light"}
          onChange={handleEditorChange}
          onMount={handleEditorDidMount}
          width="100%"
          defaultLanguage={language}
          options={{
            ...smallEditorOptions,
            ...options,
          }}
          value={value}
          {...editorProps}
        />
      </Box>

      {helperText && (
        <Typography
          fontSize={11}
          py={1}
          px={2}
          sx={{
            color: (theme) => (error ? theme.palette.input.error : "unset"),
          }}
        >
          {helperText}
        </Typography>
      )}
    </>
  );
};
