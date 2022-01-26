import { WORKFLOW_SCHEMA } from "./JSONSchemaWorkflow";

export const JSON_FILE_NAME = "file:///main.json";
export const JSON_FILE_TASK_NAME = "file:///mainTask.json";

export function configureMonaco(monaco) {
  monaco.languages.typescript.javascriptDefaults.setEagerModelSync(true);
  // noinspection JSUnresolvedVariable
  monaco.languages.typescript.javascriptDefaults.setCompilerOptions({
    target: monaco.languages.typescript.ScriptTarget.ES6,
    allowNonTsExtensions: true
  });
  let modelUri = monaco.Uri.parse(JSON_FILE_NAME);
  monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
    validate: true,
    schemas: [
      {
        uri: 'http://orkes.io/workflow-schema.json', // id of the first schema
        fileMatch: [modelUri.toString()], // associate with our model
        schema: WORKFLOW_SCHEMA
      }
    ]
  });
}
