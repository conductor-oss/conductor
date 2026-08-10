import {
  codeToFormData,
  formToCodeData,
  getDateFromField,
  JSONParse,
} from "../utils/scheduleTransformers";
import { ScheduleType } from "../Schedule";

const baseScheduleState: ScheduleType = {
  name: "my-schedule",
  description: "Test description",
  cronExpression: "0 0 12 * * ?",
  paused: false,
  runCatchupScheduleInstances: true,
  workflowType: "myWorkflow",
  workflowVersion: "1",
  workflowVersions: ["1", "2"],
  workflowInputTemplate: '{"key":"value"}',
  taskToDomain: '{"task1":"domain1"}',
  workflowCorrelationId: "corr-123",
  workflowIdempotencyKey: "idem-key",
  workflowIdempotencyStrategy: undefined,
  workflowDef: null,
  externalInputPayloadStoragePath: undefined,
  scheduleStartTime: "",
  scheduleEndTime: "",
  priority: "5",
  zoneId: "UTC",
};

describe("JSONParse", () => {
  it("returns null for empty string", () => {
    expect(JSONParse("")).toBeNull();
  });

  it("returns null for falsy values", () => {
    expect(JSONParse("")).toBeNull();
  });

  it("parses valid JSON object", () => {
    expect(JSONParse('{"key":"value"}')).toEqual({ key: "value" });
  });

  it("parses valid JSON array", () => {
    expect(JSONParse("[1,2,3]")).toEqual([1, 2, 3]);
  });

  it("parses valid JSON primitives", () => {
    expect(JSONParse("42")).toBe(42);
    expect(JSONParse('"hello"')).toBe("hello");
    expect(JSONParse("true")).toBe(true);
  });

  it("throws for invalid JSON", () => {
    expect(() => JSONParse("{invalid}")).toThrow();
  });
});

describe("getDateFromField", () => {
  it("returns empty string for empty string input", () => {
    expect(getDateFromField("")).toBe("");
  });

  it("returns empty string for 0", () => {
    expect(getDateFromField(0)).toBe("");
  });

  it("returns a numeric timestamp for a valid ISO date string", () => {
    const result = getDateFromField("2024-01-15T12:00:00Z");
    expect(typeof result).toBe("number");
    expect(result).toBe(new Date("2024-01-15T12:00:00Z").valueOf());
  });

  it("returns a numeric timestamp for a numeric timestamp input", () => {
    const ts = 1705320000000;
    expect(getDateFromField(ts)).toBe(ts);
  });

  it("returns a numeric timestamp for a Date object", () => {
    const d = new Date("2024-06-01T00:00:00Z");
    expect(getDateFromField(d as any)).toBe(d.valueOf());
  });
});

describe("formToCodeData", () => {
  it("returns correct body structure for valid state", () => {
    const result = formToCodeData(baseScheduleState, { id: "sched-id-1" });

    expect(result).not.toBeNull();
    expect(result!.name).toBe("my-schedule");
    expect(result!.cronExpression).toBe("0 0 12 * * ?");
    expect(result!.paused).toBe(false);
    expect(result!.runCatchupScheduleInstances).toBe(true);
    expect(result!.zoneId).toBe("UTC");
    // @ts-expect-error startWorkflowRequest is on the body
    expect(result!.startWorkflowRequest.name).toBe("myWorkflow");
    // @ts-expect-error
    expect(result!.startWorkflowRequest.version).toBe("1");
    // @ts-expect-error
    expect(result!.startWorkflowRequest.input).toEqual({ key: "value" });
    // @ts-expect-error
    expect(result!.startWorkflowRequest.taskToDomain).toEqual({
      task1: "domain1",
    });
    // @ts-expect-error
    expect(result!.startWorkflowRequest.priority).toBe("5");
    // @ts-expect-error
    expect(result!.startWorkflowRequest.correlationId).toBe("corr-123");
    // @ts-expect-error
    expect(result!.id).toBe("sched-id-1");
  });

  it("uses empty object when workflowInputTemplate is empty", () => {
    const state = { ...baseScheduleState, workflowInputTemplate: "" };
    const result = formToCodeData(state, null);
    // @ts-expect-error
    expect(result!.startWorkflowRequest.input).toEqual({});
  });

  it("uses empty object when taskToDomain is empty", () => {
    const state = { ...baseScheduleState, taskToDomain: "" };
    const result = formToCodeData(state, null);
    // @ts-expect-error
    expect(result!.startWorkflowRequest.taskToDomain).toEqual({});
  });

  it("returns null when workflowInputTemplate is invalid JSON", () => {
    const state = { ...baseScheduleState, workflowInputTemplate: "{bad json" };
    expect(formToCodeData(state, null)).toBeNull();
  });

  it("returns null when taskToDomain is invalid JSON", () => {
    const state = { ...baseScheduleState, taskToDomain: "{bad json" };
    expect(formToCodeData(state, null)).toBeNull();
  });

  it("converts scheduleStartTime and scheduleEndTime to timestamps", () => {
    const state = {
      ...baseScheduleState,
      scheduleStartTime: "2024-01-01T00:00:00Z",
      scheduleEndTime: "2025-01-01T00:00:00Z",
    };
    const result = formToCodeData(state, null);
    expect(result!.scheduleStartTime).toBe(
      new Date("2024-01-01T00:00:00Z").valueOf(),
    );
    expect(result!.scheduleEndTime).toBe(
      new Date("2025-01-01T00:00:00Z").valueOf(),
    );
  });
});

describe("codeToFormData", () => {
  const scheduleJson = JSON.stringify({
    name: "my-schedule",
    description: "A description",
    cronExpression: "0 0 8 * * ?",
    paused: true,
    runCatchupScheduleInstances: false,
    scheduleStartTime: 1704067200000,
    scheduleEndTime: 1735689600000,
    zoneId: "America/New_York",
    startWorkflowRequest: {
      name: "workflowA",
      version: "3",
      input: { param1: "val1" },
      correlationId: "corr-abc",
      idempotencyKey: "idem-abc",
      taskToDomain: { taskA: "domainA" },
      priority: "2",
    },
  });

  it("maps all top-level fields correctly", () => {
    const result = codeToFormData(scheduleJson, {
      ...baseScheduleState,
      workflowVersions: ["1", "2", "3"],
    });

    expect(result.name).toBe("my-schedule");
    expect(result.description).toBe("A description");
    expect(result.cronExpression).toBe("0 0 8 * * ?");
    expect(result.paused).toBe(true);
    expect(result.runCatchupScheduleInstances).toBe(false);
    expect(result.zoneId).toBe("America/New_York");
  });

  it("maps startWorkflowRequest fields correctly", () => {
    const result = codeToFormData(scheduleJson, baseScheduleState);

    expect(result.workflowType).toBe("workflowA");
    expect(result.workflowVersion).toBe("3");
    expect(result.workflowCorrelationId).toBe("corr-abc");
    expect(result.workflowIdempotencyKey).toBe("idem-abc");
    expect(result.priority).toBe("2");
  });

  it("serializes input and taskToDomain back to JSON strings", () => {
    const result = codeToFormData(scheduleJson, baseScheduleState);

    expect(JSON.parse(result.workflowInputTemplate)).toEqual({
      param1: "val1",
    });
    expect(JSON.parse(result.taskToDomain)).toEqual({ taskA: "domainA" });
  });

  it("preserves workflowVersions from scheduleState", () => {
    const state = { ...baseScheduleState, workflowVersions: ["v1", "v2"] };
    const result = codeToFormData(scheduleJson, state);
    expect(result.workflowVersions).toEqual(["v1", "v2"]);
  });

  it("returns empty strings for missing name and description", () => {
    const result = codeToFormData("{}", baseScheduleState);
    expect(result.name).toBe("");
    expect(result.description).toBe("");
    expect(result.cronExpression).toBe("");
  });

  it("coerces paused and runCatchupScheduleInstances to boolean", () => {
    const result = codeToFormData(
      JSON.stringify({ paused: 0, runCatchupScheduleInstances: 1 }),
      baseScheduleState,
    );
    expect(result.paused).toBe(false);
    expect(result.runCatchupScheduleInstances).toBe(true);
  });

  it("converts timestamps to local date strings", () => {
    const result = codeToFormData(scheduleJson, baseScheduleState);
    // timestampRendererLocal returns "yyyy-MM-dd'T'HH:mm" format
    expect(result.scheduleStartTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(result.scheduleEndTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it("returns empty string for missing timestamps", () => {
    const result = codeToFormData("{}", baseScheduleState);
    expect(result.scheduleStartTime).toBe("");
    expect(result.scheduleEndTime).toBe("");
  });
});
