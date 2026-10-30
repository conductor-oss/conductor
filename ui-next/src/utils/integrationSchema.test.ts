import { describe, expect, it } from "vitest";
import {
  integrationUISchema,
  removeInactiveIntegrationFields,
} from "./integrationSchema";
import schema from "pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaInvokeSchema.json";

describe("conditional integration forms", () => {
  it("preserves ordinary schemas and edits which do not change mode", () => {
    const ordinary = {
      type: "object",
      properties: { name: { type: "string" } },
    };
    expect(integrationUISchema(ordinary).elements[0].rule).toBeUndefined();
    const input = { name: "value" };
    expect(removeInactiveIntegrationFields(ordinary, {}, input)).toBe(input);
    expect(removeInactiveIntegrationFields(schema, {}, input)).toBe(input);
    const async = { invocationType: "Event", resultQueueArn: "queue" };
    expect(removeInactiveIntegrationFields(schema, async, async)).toBe(async);
  });
  it("does not assign rules to unrelated controls and removes only excluded fields", () => {
    const ui = integrationUISchema(schema);
    expect(
      ui.elements.find((c: any) => c.scope === "#/properties/functionName")
        .rule,
    ).toBeUndefined();
    expect(
      removeInactiveIntegrationFields(
        schema,
        { invocationType: "Event" },
        {
          invocationType: "RequestResponse",
          resultQueueArn: "queue",
          qualifier: "7",
        },
      ),
    ).toEqual({ invocationType: "RequestResponse", qualifier: "7" });
    expect(integrationUISchema({ type: "string" })).toBeDefined();
    expect(
      integrationUISchema({
        type: "object",
        properties: { flag: { type: "string" } },
        else: { not: { anyOf: [{}] } },
      }),
    ).toBeDefined();
  });
});

it("replaces package controls with the dedicated package input without hiding ordinary fields", async () => {
  const { default: create } =
    await import("pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaCreateSchema.json");
  const controls = integrationUISchema(create).elements.map(
    (c: any) => c.scope,
  );
  expect(controls).toContain("#/properties/functionName");
  for (const field of ["zipFileBase64", "s3Bucket", "s3Key", "s3ObjectVersion"])
    expect(controls).not.toContain(`#/properties/${field}`);
});
