import Editor, { EditorProps } from "@monaco-editor/react";
import { Box, BoxProps, InputLabel, Tooltip } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import {
  CSSProperties,
  FunctionComponent,
  ReactNode,
  useCallback,
  useContext,
  useRef,
} from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { inputLabelIdleStyles } from "theme/material/components/formControls";
import { SMALL_EDITOR_DEFAULT_OPTIONS } from "utils/constants";
import Text from "./Text";

type CodeBlockInputProps = {
  label?: ReactNode;
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
  containerStyles?: CSSProperties;
} & Partial<Omit<EditorProps, "onChange">>;

const MIN_HEIGHT = 120;

const CodeBlockInput: FunctionComponent<CodeBlockInputProps> = ({
  label = "Code",
  language = "json",
  onChange = () => null,
  value = "",
  containerProps = {},
  error = false,
  minHeight,
  autoformat = true,
  labelStyle,
  languageLabel,
  containerStyles = {},
  ...restOfProps
}) => {
  const { mode } = useContext(ColorModeContext);
  const editorRef = useRef(null) as any;

  const handleEditorDidMount = useCallback(
    (editor: any) => {
      editorRef.current = editor;
      if (autoformat) {
        editor.onDidBlurEditorWidget(() => {
          editor.getAction("editor.action.formatDocument").run();
        });
      }
    },
    [editorRef, autoformat],
  );

  const onEditorChange = useCallback(() => {
    const editorValue = editorRef?.current?.getValue();
    onChange(editorValue);
  }, [onChange]);

  const minimumHeight = minHeight || MIN_HEIGHT;

  return (
    <Box {...containerProps}>
      {label && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          {typeof label === "string" && label.length > 30 ? (
            <Tooltip title={label}>
              <InputLabel sx={inputLabelIdleStyles}>{label}</InputLabel>
            </Tooltip>
          ) : (
            <InputLabel
              sx={
                {
                  ...inputLabelIdleStyles,
                  ...labelStyle,
                } as SxProps<Theme>
              }
            >
              {label}
            </InputLabel>
          )}

          <Text
            sx={{
              padding: "4px 10px",
              borderRadius: "5px",
              background: "rgba(0,0,0,.15)",
              fontSize: ".6rem",
              marginTop: "-10px",
            }}
          >
            {languageLabel
              ? languageLabel.toUpperCase()
              : language.toUpperCase()}
          </Text>
        </Box>
      )}
      <Box
        sx={{
          borderColor: error ? "red" : "rgba(128, 128, 128, 0.2)",
          borderStyle: "solid",
          borderWidth: "1px",
          borderRadius: "4px",
          backgroundColor: mode === "dark" ? "#1e1e1e" : "#fff",
          "&:focus-within": {
            margin: "-2px",
            borderColor: error ? "red" : "rgb(73, 105, 228)",
            borderStyle: "solid",
            borderWidth: "2px",
          },
          // Targeting first div in Monaco
          "& > section": {
            resize: "vertical",
            overflow: "auto",
            minHeight: minimumHeight,
          },
          ...containerStyles,
        }}
      >
        <Editor
          theme={mode === "dark" ? "vs-dark" : "light"}
          onChange={onEditorChange}
          onMount={handleEditorDidMount}
          width="100%"
          height={`${minimumHeight}px`}
          defaultLanguage={language}
          options={SMALL_EDITOR_DEFAULT_OPTIONS}
          value={value}
          {...restOfProps}
        />
      </Box>
    </Box>
  );
};

export default CodeBlockInput;
