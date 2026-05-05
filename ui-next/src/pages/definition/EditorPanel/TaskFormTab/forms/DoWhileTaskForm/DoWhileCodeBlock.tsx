import { EditorProps, Monaco } from "@monaco-editor/react";
import { BoxProps } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import _keys from "lodash/keys";
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

import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import {
  invalidDollarVariables,
  undeclaredInputParameters,
} from "pages/definition/helpers";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { DoWhileTaskDef } from "types";
import {
  OnlyTheWordInfoProp,
  editorAddCommandAltEnter,
  editorDecorations,
} from "../../helpers";
import { smallEditorDefaultOptions } from "../editorConfig";

type DoWhileCodeBlockProps = {
  label?: ReactNode;
  language?: string;
  onChange?: (taskChanges: Partial<DoWhileTaskDef>) => void;
  containerProps?: BoxProps;
  error?: boolean;
  height?: number | "auto";
  minHeight?: number;
  autoformat?: boolean;
  labelStyle?: SxProps<Theme>;
  languageLabel?: string;
  containerStyles?: CSSProperties;
  autoSizeBox?: boolean;
  task: Partial<DoWhileTaskDef>;
} & Partial<Omit<EditorProps, "onChange">>;

const additionalEditorOptions = {
  lineNumbers: "on" as const,
  lineDecorationsWidth: 10,
};

const warnUndeclaredVariables = (
  editor: Monaco,
  monaco: any,
  task: Partial<DoWhileTaskDef>,
  currentDecorations: MutableRefObject<any[] | null>,
) => {
  const model = editor.getModel();
  const taskExpression = task?.loopCondition;
  const taskReferenceName = task?.taskReferenceName ?? "";
  const loopOverTasks =
    task?.loopOver?.map((item) => item.taskReferenceName) ?? [];
  if (model && taskExpression && editor) {
    const addedInputParameters = undeclaredInputParameters(
      model.getValue(),
      task?.inputParameters,
    );
    if (addedInputParameters.includes(taskReferenceName)) {
      addedInputParameters.splice(
        addedInputParameters.indexOf(taskReferenceName),
        1,
      );
    }
    const filteredAddedInputParameters = addedInputParameters.filter(
      (element) => !loopOverTasks.includes(element),
    );

    const invalidDollarVars = invalidDollarVariables(model.getValue());

    const decorations = editorDecorations(
      model,
      [...filteredAddedInputParameters, ...invalidDollarVars],
      monaco,
    );

    return editor.deltaDecorations(
      currentDecorations.current ? currentDecorations.current : [],
      decorations.flat(),
    );
  }
};

export const DoWhileCodeBlock: FunctionComponent<DoWhileCodeBlockProps> = ({
  language = "json",
  onChange = () => null,
  autoSizeBox = false,
  task,
  ...restOfProps
}) => {
  const taskRef = useRef<Partial<DoWhileTaskDef> | null>(null);
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
      const callBackFunction = (onlyTheWordInfo: OnlyTheWordInfoProp) => {
        onChange({
          ...taskRef.current,
          inputParameters: {
            ...taskRef.current!.inputParameters,
            [onlyTheWordInfo.word]: "", // Add the original word
          },
        } as Partial<DoWhileTaskDef>);
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
        loopCondition: editorValue,
      } as Partial<DoWhileTaskDef>);
    },
    [onChange],
  );

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
              const loopOverTasks =
                taskRef?.current?.loopOver?.map(
                  (item) => `$.${item.taskReferenceName}`,
                ) ?? [];

              let variableSuggestions: string[] = [];
              if (inputVariables) {
                variableSuggestions = inputVariables
                  .filter(
                    (item) => item !== "expression" && item !== "evaluatorType",
                  )
                  .map((item) => `$.${item}`);
              }
              // Provide suggestions for JSON properties that start with the current text
              const propertySuggestions = [
                ...variableSuggestions,
                ...loopOverTasks,
              ].map((property) => ({
                label: property,
                kind: monaco.languages.CompletionItemKind.Value,
                insertText: `${property}`,
              }));

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
      defaultLanguage={language}
      options={{
        ...smallEditorDefaultOptions,
        ...(autoSizeBox && { scrollBeyondLastLine: false }),
        ...additionalEditorOptions,
      }}
      value={taskRef?.current?.loopCondition || ""}
      {...restOfProps}
    />
  );
};
