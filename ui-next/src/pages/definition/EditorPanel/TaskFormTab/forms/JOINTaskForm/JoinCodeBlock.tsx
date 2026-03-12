import { EditorProps, Monaco } from "@monaco-editor/react";
import { BoxProps } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import _keys from "lodash/keys";

import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
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
import { JoinTaskDef } from "types";
import { editorDecorations } from "../../helpers";
import { smallEditorDefaultOptions } from "../editorConfig";

type JoinCodeBlockProps = {
  label?: ReactNode;
  language?: string;
  onChange?: (taskChanges: Partial<JoinTaskDef>) => void;
  containerProps?: BoxProps;
  error?: boolean;
  height?: number | "auto";
  minHeight?: number;
  autoformat?: boolean;
  labelStyle?: SxProps<Theme>;
  languageLabel?: string;
  containerStyles?: CSSProperties;
  autoSizeBox?: boolean;
  task: Partial<JoinTaskDef>;
} & Partial<Omit<EditorProps, "onChange">>;

const MIN_HEIGHT = 120;

const additionalEditorOptions = {
  lineNumbers: "on" as const,
  lineDecorationsWidth: 10,
};

const warnUndeclaredVariables = (
  editor: Monaco,
  monaco: any,
  task: Partial<JoinTaskDef>,
  currentDecorations: MutableRefObject<any[] | null>,
) => {
  const model = editor.getModel();
  const taskExpression = task?.expression;
  if (model && taskExpression && editor) {
    const addedInputParameters = undeclaredInputParameters(
      model.getValue(),
      task?.inputParameters,
    );
    let filteredInputParameters = [...addedInputParameters];

    if (addedInputParameters.includes("joinOn")) {
      filteredInputParameters = addedInputParameters.filter(
        (item) => item !== "joinOn",
      );
    }

    const invalidDollarVars = invalidDollarVariables(model.getValue());

    const decorations = editorDecorations(
      model,
      [...filteredInputParameters, ...invalidDollarVars],
      monaco,
    );

    return editor.deltaDecorations(
      currentDecorations.current ? currentDecorations.current : [],
      decorations.flat(),
    );
  }
};
const VARIABLE_DEFINER = "$.";
const EXEMPTED_KEYS = ["$.joinOn"];

export const JoinCodeBlock: FunctionComponent<JoinCodeBlockProps> = ({
  language = "json",
  onChange = () => null,
  minHeight,
  autoSizeBox = false,
  task,
  ...restOfProps
}) => {
  const taskRef = useRef<Partial<JoinTaskDef> | null>(null);
  taskRef.current = task;
  const { mode } = useContext(ColorModeContext);
  const disposeRef = useRef<null | (() => void)>(null);
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
      editor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.Enter, () => {
        const position = editor.getPosition(); // Get the current cursor position
        const model = editor.getModel();

        if (model) {
          const onlyTheWordInfo = model.getWordAtPosition(position); // This only selects the word

          const startColumn = onlyTheWordInfo?.startColumn;
          if (startColumn > VARIABLE_DEFINER.length) {
            // Avoid blowing up because of wrong position.
            const newStart = Math.max(startColumn - VARIABLE_DEFINER.length, 1); // We select a new start
            let word = null;
            // Create a new range from th new start including $.
            const wordRange = new monaco.Range(
              position.lineNumber,
              newStart,
              position.lineNumber,
              onlyTheWordInfo.endColumn,
            );
            word = model.getValueInRange(wordRange);

            if (
              word &&
              word?.includes(VARIABLE_DEFINER) &&
              !EXEMPTED_KEYS.includes(word)
            ) {
              const maybeNewVariable = word.word;
              const currentVariables = _keys(
                taskRef.current?.inputParameters || {},
              );

              if (!currentVariables.includes(maybeNewVariable)) {
                onChange({
                  ...taskRef.current,
                  inputParameters: {
                    ...taskRef.current!.inputParameters,
                    [onlyTheWordInfo.word]: "", // Add the original word
                  },
                } as Partial<JoinTaskDef>);
                // cleanup
                currentDecorations.current = warnUndeclaredVariables(
                  editor,
                  monaco,
                  taskRef.current!,
                  currentDecorations,
                );
              }
            }
          }
        }
      });
      editor.onDidChangeModelContent((_event: any) => {
        // Warn on change
        currentDecorations.current = warnUndeclaredVariables(
          editor,
          monaco,
          taskRef.current!,
          currentDecorations,
        );
      });

      // Warn on mount
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
        expression: editorValue,
      } as Partial<JoinTaskDef>);
    },
    [onChange],
  );

  const minimumHeight = minHeight || MIN_HEIGHT;

  return (
    <ConductorCodeBlockInput
      theme={mode === "dark" ? "vs-dark" : "light"}
      onChange={onEditorChange}
      onMount={(editor: Monaco, monaco: any) => {
        handleEditorDidMount(editor, monaco);
      }}
      beforeMount={(monaco: Monaco) => {
        if (disposeRef.current) {
          disposeRef.current();
          disposeRef.current = null;
        }
        const disposable = monaco.languages.registerCompletionItemProvider(
          "javascript",
          {
            provideCompletionItems: () => {
              const inputVariables = _keys(taskRef?.current?.inputParameters);
              let variableSuggestions: string[] = [];
              if (inputVariables) {
                variableSuggestions = inputVariables.map((item) => `$.${item}`);
              }
              variableSuggestions.push("$.joinOn");
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
        // IMPORTANT: keep `dispose()` bound to its disposable context.
        // Destructuring `dispose` can lose `this` and throw "Unbound disposable context".
        disposeRef.current = () => disposable.dispose();
      }}
      width="100%"
      height={autoSizeBox ? "auto" : minimumHeight}
      defaultLanguage={language}
      options={{
        ...smallEditorDefaultOptions,
        ...(autoSizeBox && { scrollBeyondLastLine: false }),
        ...additionalEditorOptions,
      }}
      value={taskRef?.current?.expression || ""}
      {...restOfProps}
    />
  );
};
