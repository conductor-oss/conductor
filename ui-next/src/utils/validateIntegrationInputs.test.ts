import { describe, expect, it, vi } from "vitest";
import { TaskType, WorkflowDef } from "types";
import { validateIntegrationInputs } from "./validateIntegrationInputs";
import schema from "pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaInvokeSchema.json";

import createSchema from "pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaCreateSchema.json";

const input = {
  integrationName: "lambda",
  method: "aws-lambda-Invoke Function",
  functionName: "function",
  payload: "{}",
};
const task = (inputParameters: any) => ({
  name: "invoke",
  taskReferenceName: "invoke_ref",
  type: TaskType.CONNECTED_APP,
  inputParameters,
});
const workflow = (inputParameters: any) =>
  ({ tasks: [task(inputParameters)] }) as WorkflowDef;
const fetchSchema = () =>
  vi.fn(async () => ({ inputSchema: { data: schema } }));

describe("Connected App definition validation", () => {
  it("handles empty workflows, missing input objects and operations without schemas", async () => {
    await expect(
      validateIntegrationInputs({} as WorkflowDef, fetchSchema()),
    ).resolves.toBeUndefined();
    await expect(
      validateIntegrationInputs(workflow(undefined), fetchSchema()),
    ).rejects.toMatchObject({ status: 400 });
    await expect(
      validateIntegrationInputs(
        workflow(input),
        vi.fn(async () => ({})),
      ),
    ).resolves.toBeUndefined();
    await expect(
      validateIntegrationInputs(
        workflow(input),
        vi.fn(async () => ({
          inputSchema: { data: { if: { required: ["flag"] } } },
        })),
      ),
    ).resolves.toBeUndefined();
  });
  it.each(["functionName", "payload", "integrationName", "method"])(
    "rejects missing, null and blank %s before save",
    async (field) => {
      for (const value of [undefined, null, "", "   "]) {
        const invalid = { ...input, [field]: value };
        await expect(
          validateIntegrationInputs(workflow(invalid), fetchSchema()),
        ).rejects.toMatchObject({
          status: 400,
          validationErrors: expect.arrayContaining([
            expect.objectContaining({ path: expect.stringContaining(field) }),
          ]),
        });
      }
    },
  );

  it("requires the async queue but allows optional timings to be absent", async () => {
    await expect(
      validateIntegrationInputs(
        workflow({ ...input, invocationType: "Event" }),
        fetchSchema(),
      ),
    ).rejects.toMatchObject({ status: 400 });
    await expect(
      validateIntegrationInputs(
        workflow({
          ...input,
          invocationType: "Event",
          resultQueueArn: "arn:aws:sqs:us-east-1:123456789012:results",
        }),
        fetchSchema(),
      ),
    ).resolves.toBeUndefined();
  });

  it("allows expressions, qualifiers and dynamic routing without treating them as literals", async () => {
    for (const values of [
      input,
      {
        ...input,
        functionName: "${workflow.input.functionName}",
        pollIntervalSeconds: "${workflow.input.poll}",
        invocationType: "${workflow.input.mode}",
      },
      { ...input, qualifier: "prod" },
      "${workflow.input.task}",
      { ...input, method: "${workflow.input.method}" },
    ]) {
      await expect(
        validateIntegrationInputs(workflow(values), fetchSchema()),
      ).resolves.toBeUndefined();
    }
  });

  it("validates other operations' compulsory inputs from their schema, not a Lambda field list", async () => {
    const other = vi.fn(async () => ({
      inputSchema: {
        data: {
          type: "object",
          required: ["bucket", "key"],
          properties: { bucket: { type: "string" }, key: { type: "string" } },
        },
      },
    }));
    await expect(
      validateIntegrationInputs(
        workflow({ integrationName: "storage", method: "get", bucket: "data" }),
        other,
      ),
    ).rejects.toMatchObject({
      validationErrors: expect.arrayContaining([
        expect.objectContaining({ path: "tasks[0].inputParameters.key" }),
      ]),
    });
  });

  it("walks switch, fork and loop children and reuses discovery per operation", async () => {
    const fetch = fetchSchema();
    const nested = {
      tasks: [
        {
          type: TaskType.SWITCH,
          decisionCases: { branch: [task(input)] },
          defaultCase: [task(input)],
          forkTasks: [[task(input)]],
          loopOver: [task({ ...input, functionName: undefined })],
        },
      ],
    } as unknown as WorkflowDef;
    await expect(
      validateIntegrationInputs(nested, fetch),
    ).rejects.toMatchObject({
      validationErrors: expect.arrayContaining([
        expect.objectContaining({
          path: "tasks[0].loopOver[0].inputParameters.functionName",
        }),
      ]),
    });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("does not fetch schemas for other task types or dynamically chosen connections", async () => {
    const fetch = fetchSchema();
    await validateIntegrationInputs(
      {
        tasks: [
          { type: TaskType.SIMPLE },
          task({ ...input, integrationName: "${workflow.input.connection}" }),
        ],
      } as WorkflowDef,
      fetch,
    );
    expect(fetch).not.toHaveBeenCalled();
  });
});

it("validates the actual discovered Create Function package alternatives and expressions", async () => {
  const base = {
    integrationName: "lambda",
    method: "Create Function",
    functionName: "f",
    runtime: "nodejs22.x",
    role: "role",
    handler: "index.handler",
  };
  const fetch = async () => ({ inputSchema: { data: createSchema } });
  for (const role of [
    undefined,
    "",
    " ",
    "arn:aws:iam::123456789012:role/existing",
    "${workflow.input.role}",
  ]) {
    await expect(
      validateIntegrationInputs(
        workflow({ ...base, role, zipFileBase64: "UEsDBA==" }),
        fetch,
      ),
    ).resolves.toBeUndefined();
  }
  for (const source of [
    { zipFileBase64: "UEsDBA==" },
    { zipFileBase64: "${workflow.input.package}" },
    { s3Bucket: "bucket", s3Key: "code.zip" },
    {
      s3Bucket: "${workflow.input.bucket}",
      s3Key: "code.zip",
      s3ObjectVersion: "v7",
    },
  ])
    await expect(
      validateIntegrationInputs(workflow({ ...base, ...source }), fetch),
    ).resolves.toBeUndefined();
  for (const source of [
    {},
    { zipFileBase64: "" },
    { zipFileBase64: " " },
    { zipFileBase64: null },
    { s3Bucket: "bucket" },
    { s3Key: "code.zip" },
    { s3ObjectVersion: "v7" },
    { s3Bucket: "bucket", s3Key: " " },
    { s3Bucket: "", s3Key: "code.zip" },
    { s3Bucket: "bucket", s3Key: "code.zip", s3ObjectVersion: "" },
    { zipFileBase64: "UEsDBA==", s3Bucket: "bucket", s3Key: "code.zip" },
    { zipFileBase64: "UEsDBA==", s3ObjectVersion: "v7" },
  ])
    await expect(
      validateIntegrationInputs(workflow({ ...base, ...source }), fetch),
    ).rejects.toMatchObject({ status: 400 });
});
