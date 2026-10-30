import Ajv from "ajv";
import { TaskDef, TaskType, WorkflowDef } from "types";
import { downgradeSchemaToDraft7 } from "utils/json";

type Schema = Record<string, any>;
const expression = (value: unknown) =>
  typeof value === "string" && value.includes("${");

// Definition-time validation cannot evaluate workflow expressions. Preserve
// their presence while validating static inputs against the discovered schema.
const authoringSchema = (schema: Schema, data: any): Schema => {
  if (expression(data)) return {};
  const result = { ...schema };
  if (schema.properties) {
    result.properties = Object.fromEntries(
      Object.entries(schema.properties).map(([key, child]) => {
        const property = authoringSchema(child as Schema, data?.[key]);
        return [
          key,
          schema.required?.includes(key) && !expression(data?.[key])
            ? { allOf: [property, { not: { type: "string", pattern: "^\\s*$" } }] }
            : property,
        ];
      }),
    );
  }
  if (schema.if) {
    const dynamicCondition = Object.keys(schema.if.properties ?? {}).some(
      (key) => expression(data?.[key]),
    );
    if (dynamicCondition) {
      delete result.if;
      delete result.then;
      delete result.else;
    } else {
      if (schema.then) result.then = authoringSchema(schema.then, data);
      if (schema.else) result.else = authoringSchema(schema.else, data);
    }
  }
  return result;
};

export async function validateIntegrationInputs(
  workflow: WorkflowDef,
  fetchSchema: (path: string) => Promise<{ inputSchema?: { data?: Schema } }>,
) {
  const ajv = new Ajv({
    strict: false,
    allErrors: true,
    validateFormats: false,
  });
  const schemas = new Map<string, Schema | undefined>();
  const errors: { message: string; path: string }[] = [];
  const visit = async (tasks: TaskDef[], path: string) => {
    for (const [index, task] of tasks.entries()) {
      const taskPath = `${path}[${index}]`;
      if (task.type === TaskType.CONNECTED_APP) {
        const input = task.inputParameters ?? {};
        // A runtime expression may supply the complete input object.
        if (!expression(input)) {
          const missing = ["integrationName", "method"].filter(
            (key) => typeof input[key] !== "string" || !input[key].trim(),
          );
          for (const key of missing)
            errors.push({
              message: `${key} is required`,
              path: `${taskPath}.inputParameters.${key}`,
            });
          if (
            !missing.length &&
            !expression(input.integrationName) &&
            !expression(input.method)
          ) {
            const url = `/integrations/${encodeURIComponent(input.integrationName)}/def/api/${encodeURIComponent(input.method)}`;
            if (!schemas.has(url))
              schemas.set(url, (await fetchSchema(url))?.inputSchema?.data);
            const schema = schemas.get(url);
            if (schema) {
              const validate = ajv.compile(
                authoringSchema(downgradeSchemaToDraft7(schema), input),
              );
              if (!validate(input)) {
                for (const error of validate.errors!)
                  errors.push({
                    message: `${task.taskReferenceName}: ${error.message}`,
                    path: `${taskPath}.inputParameters${error.instancePath.replaceAll("/", ".")}${error.keyword === "required" ? `.${error.params.missingProperty}` : ""}`,
                  });
              }
            }
          }
        }
      }
      for (const [key, children] of Object.entries(task.decisionCases ?? {})) {
        await visit(children, `${taskPath}.decisionCases.${key}`);
      }
      for (const [branch, children] of (task.forkTasks ?? []).entries()) {
        await visit(children, `${taskPath}.forkTasks[${branch}]`);
      }
      await visit(task.defaultCase ?? [], `${taskPath}.defaultCase`);
      await visit(task.loopOver ?? [], `${taskPath}.loopOver`);
    }
  };
  await visit(workflow.tasks ?? [], "tasks");
  if (errors.length)
    throw {
      text: "Required or invalid Connected App inputs must be corrected before saving.",
      severity: "error",
      status: 400,
      validationErrors: errors,
    };
}
