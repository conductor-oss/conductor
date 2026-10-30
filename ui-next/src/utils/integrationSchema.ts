import Ajv from "ajv";
import { generateDefaultUISchema, RuleEffect } from "@jsonforms/core";

import { hasDeploymentPackage, packageFields } from "./deploymentPackage";

type Schema = Record<string, any>;

// Respect a tool's conditional exclusions in the generated form, without
// hard-coding provider or operation names in the task editor.
const conditionalFields = (schema: Schema): string[] =>
  (schema.else?.not?.anyOf ?? []).flatMap(
    (entry: Schema) => entry.required ?? [],
  );

export const integrationUISchema = (schema: Schema) => {
  // Package oneOf describes mutually exclusive data sources, not a choice of
  // whole-object editors. Keep it in validation but generate ordinary controls.
  const layoutSchema = { ...schema };
  if (hasDeploymentPackage(schema)) delete layoutSchema.oneOf;
  const ui = generateDefaultUISchema(layoutSchema) as any;
  if (hasDeploymentPackage(schema)) {
    ui.elements = ui.elements.filter(
      (control: any) =>
        !packageFields.some(
          (field) => control.scope === `#/properties/${field}`,
        ),
    );
  }
  const fields = conditionalFields(schema);
  for (const control of ui.elements) {
    if (hasDeploymentPackage(schema) && control.scope === "#/properties/role") {
      control.options = { showUnfocusedDescription: true };
    }
    if (fields.some((field) => control.scope === `#/properties/${field}`)) {
      control.rule = {
        effect: RuleEffect.SHOW,
        condition: { scope: "#", schema: schema.if },
      };
    }
  }
  return ui;
};

export const removeInactiveIntegrationFields = (
  schema: Schema,
  previous: Schema | undefined,
  next: Schema,
) => {
  if (!schema.if) return next;
  const validate = new Ajv({ strict: false }).compile(schema.if);
  const matches = (value: Schema | undefined): boolean => validate(value);
  // Only clear fields on a deliberate mode transition, not when loading an
  // existing definition or editing an unrelated field.
  if (!matches(previous) || matches(next)) return next;
  const result = { ...next };
  for (const field of conditionalFields(schema)) delete result[field];
  return result;
};
