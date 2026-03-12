import { EditorProps, Monaco } from "@monaco-editor/react";
import { BoxProps } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import _keys from "lodash/keys";
import {
  invalidDollarVariables,
  undeclaredInputParameters,
} from "pages/definition/helpers";
import {
  CSSProperties,
  FunctionComponent,
  MutableRefObject,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
} from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { InlineTaskDef } from "types";
import {
  OnlyTheWordInfoProp,
  editorAddCommandAltEnter,
  editorDecorations,
} from "../../helpers";
import { smallEditorDefaultOptions } from "../editorConfig";
import { logger } from "utils/logger";

type InlineCodeBlockProps = {
  label?: ReactNode;
  language?: string;
  onChange?: (taskChanges: Partial<InlineTaskDef>) => void;
  containerProps?: BoxProps;
  error?: boolean;
  height?: number | "auto";
  minHeight?: number;
  autoformat?: boolean;
  labelStyle?: SxProps<Theme>;
  languageLabel?: string;
  containerStyles?: CSSProperties;
  autoSizeBox?: boolean;
  task: Partial<InlineTaskDef>;
} & Partial<Omit<EditorProps, "onChange">>;

const MIN_HEIGHT = 120;

const additionalEditorOptions = {
  lineNumbers: "on" as const,
  lineDecorationsWidth: 10,
};

const warnUndeclaredVariables = (
  editor: Monaco,
  monaco: any,
  task: Partial<InlineTaskDef>,
  currentDecorations: MutableRefObject<any[] | null>,
) => {
  const model = editor.getModel();
  const taskExpression = task?.inputParameters?.expression;
  if (model && taskExpression && editor) {
    const addedInputParameters = undeclaredInputParameters(
      model.getValue(),
      task?.inputParameters,
    );

    const invalidDollarVars = invalidDollarVariables(model.getValue());

    const decorations = editorDecorations(
      model,
      [...addedInputParameters, ...invalidDollarVars],
      monaco,
    );

    return editor.deltaDecorations(
      currentDecorations.current ? currentDecorations.current : [],
      decorations.flat(),
    );
  }
};

const InlineCodeBlock: FunctionComponent<InlineCodeBlockProps> = ({
  label = "Code",
  language = "json",
  onChange = () => null,
  minHeight,
  autoSizeBox = false,
  task,
  ...restOfProps
}) => {
  const taskRef = useRef<Partial<InlineTaskDef> | null>(null);
  taskRef.current = task;
  const { mode } = useContext(ColorModeContext);
  const disposeRef = useRef(null) as any;
  const currentDecorations = useRef<any[] | null>([]) as any;

  useEffect(() => {
    return () => {
      if (disposeRef.current) {
        disposeRef.current();
      }
    };
  }, []);

  const handleEditorDidMount = useCallback(
    (editor: Monaco, monaco: any) => {
      const model = editor.getModel();

      const callBackFunction = (onlyTheWordInfo: OnlyTheWordInfoProp) => {
        onChange({
          ...taskRef.current,
          inputParameters: {
            ...taskRef.current!.inputParameters,
            [onlyTheWordInfo.word]: "", // Add the original word
            expression: model.getValue(),
          },
        } as Partial<InlineTaskDef>);
        // cleanup
        currentDecorations.current = warnUndeclaredVariables(
          editor,
          monaco,
          taskRef.current!,
          currentDecorations,
        );
      };
      // editor.AddCommand function
      editorAddCommandAltEnter(editor, monaco, taskRef, callBackFunction);

      editor.onDidChangeModelContent((_event: any) => {
        // Warn on change
        currentDecorations.current = warnUndeclaredVariables(
          editor,
          monaco,
          taskRef.current!,
          currentDecorations,
        );
      });

      currentDecorations.current = warnUndeclaredVariables(
        editor,
        monaco,
        taskRef.current!,
        currentDecorations,
      );
    },
    [onChange],
  );

  const onEditorChange = useCallback(
    (editorValue: string) => {
      onChange({
        ...taskRef.current,
        inputParameters: {
          ...taskRef.current?.inputParameters,
          expression: editorValue,
        },
      } as Partial<InlineTaskDef>);
    },
    [onChange],
  );

  const minimumHeight = minHeight || MIN_HEIGHT;

  return (
    <ConductorCodeBlockInput
      label={label}
      theme={mode === "dark" ? "vs-dark" : "light"}
      onChange={onEditorChange}
      onMount={(editor, monaco) => {
        handleEditorDidMount(editor, monaco);
      }}
      beforeMount={(monaco: Monaco) => {
        if (disposeRef.current) {
          try {
            disposeRef.current();
          } catch (error) {
            logger.error("Error disposing from Ref on beforeMount", error);
          }
          disposeRef.current = null;
        }
        const disposable = monaco.languages.registerCompletionItemProvider(
          "javascript",
          {
            provideCompletionItems: () => {
              const inputVariables = _keys(taskRef?.current?.inputParameters);
              let variableSuggestions: string[] = [];
              if (inputVariables) {
                variableSuggestions = inputVariables
                  .filter(
                    (item) => item !== "expression" && item !== "evaluatorType",
                  )
                  .map((item) => `$.${item}`);
              }
              // Provide suggestions for JSON properties that start with the current text
              const propertySuggestions = variableSuggestions.map(
                (property) => ({
                  label: property,
                  kind: monaco.languages.CompletionItemKind.Value,
                  insertText: `${property}`,
                }),
              );
              // Merge custom suggestions with JSON property suggestions
              const suggestions = [...propertySuggestions];
              return { suggestions };
            },
          },
        );

        disposeRef.current = () => disposable.dispose();
      }}
      width="100%"
      height={autoSizeBox ? "auto" : minimumHeight}
      minHeight={minimumHeight}
      defaultLanguage={language}
      options={{
        ...smallEditorDefaultOptions,
        ...(autoSizeBox && { scrollBeyondLastLine: false }),
        ...additionalEditorOptions,
      }}
      value={taskRef?.current?.inputParameters?.expression || ""}
      {...restOfProps}
    />
  );
};

export default InlineCodeBlock;
