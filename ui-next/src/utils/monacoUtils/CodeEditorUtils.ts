import {
  workflowDefinitionSchemaWithDeps,
  workflowSchema,
} from "types/Schemas";
import _initial from "lodash/initial";
// @ts-ignore
import { registerJQLanguageDefinition } from "monaco-languages-jq";
import { registerPromQlLangauge } from "./promql";

export const JSON_FILE_NAME = "file:///main.json";
export const JSON_FILE_TASK_NAME = "file:///mainTask.json";

export function configureMonaco(monaco: any) {
  const modelUri = monaco.Uri.parse(JSON_FILE_NAME);

  const workflowSchemaUri = {
    uri: workflowSchema.$id,
    fileMatch: [modelUri.toString()], // associate with our model
    schema: workflowSchema,
  };

  const schemasWithURI = _initial(workflowDefinitionSchemaWithDeps).map(
    (original) => ({
      uri: original.$id,
      schema: original,
    }),
  );
  // @ts-ignore
  const result = [workflowSchemaUri].concat(schemasWithURI);

  monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
    validate: true,
    schemas: result,
  });
}

export const configurePromQl = (monaco: any) => {
  registerPromQlLangauge(monaco);
};

export const configureJQLanguage = (monaco: any) => {
  registerJQLanguageDefinition(monaco);
};
