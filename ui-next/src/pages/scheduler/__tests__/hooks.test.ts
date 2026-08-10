import { act, renderHook } from "@testing-library/react";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { useState } from "react";
import { useScheduleFormHandlers } from "../hooks/useScheduleFormHandlers";
import { useScheduleState } from "../hooks/useScheduleState";
import { useWorkflowConfig } from "../hooks/useWorkflowConfig";
import { ScheduleType } from "../Schedule";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const baseState: ScheduleType = {
  name: "",
  description: "",
  cronExpression: "",
  paused: false,
  runCatchupScheduleInstances: false,
  workflowType: null,
  workflowVersion: null,
  workflowVersions: [],
  workflowInputTemplate: "",
  taskToDomain: "",
  workflowCorrelationId: "",
  workflowIdempotencyKey: undefined,
  workflowIdempotencyStrategy: undefined,
  workflowDef: null,
  externalInputPayloadStoragePath: undefined,
  scheduleStartTime: "",
  scheduleEndTime: "",
  priority: "",
  zoneId: "UTC",
};

/** Wrapper that wires up all the state needed by useScheduleFormHandlers */
function useFormHandlersWrapper(initial: ScheduleType = baseState) {
  const [scheduleState, setScheduleState] = useState<ScheduleType>(initial);
  const [errors, setErrors] = useState<Record<string, string> | null>(null);
  const [couldNotParseJson, setCouldNotParseJson] = useState(false);
  const [highlightedPart, setHighlightedPart] = useState<number | null>(null);

  const clearError = (field: string) => {
    setErrors((prev) => {
      if (!prev) return prev;
      const updated = { ...prev };
      delete updated[field];
      return updated;
    });
  };

  const handlers = useScheduleFormHandlers(
    scheduleState,
    setScheduleState,
    setErrors,
    clearError,
    errors,
    setCouldNotParseJson,
    setHighlightedPart,
  );

  return {
    scheduleState,
    errors,
    couldNotParseJson,
    highlightedPart,
    handlers,
  };
}

// ---------------------------------------------------------------------------
// useScheduleFormHandlers
// ---------------------------------------------------------------------------

describe("useScheduleFormHandlers", () => {
  describe("setScheduleNewState – name field validation", () => {
    it("sets an error when name is empty", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setScheduleNewState("name", "");
      });

      expect(result.current.errors?.name).toBe("Name is required");
    });

    it("sets an error when name contains invalid characters", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setScheduleNewState("name", "invalid name!");
      });

      expect(result.current.errors?.name).toMatch(
        /only contain letters, numbers, and underscores/,
      );
    });

    it("accepts names with letters, numbers, and underscores", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setScheduleNewState("name", "Valid_Name_123");
      });

      expect(result.current.errors?.name).toBeUndefined();
      expect(result.current.scheduleState.name).toBe("Valid_Name_123");
    });

    it("clears an existing name error when name becomes valid", () => {
      const { result } = renderHook(() =>
        useFormHandlersWrapper({
          ...baseState,
          name: "bad name!",
        }),
      );

      // First set invalid name to generate error
      act(() => {
        result.current.handlers.setScheduleNewState("name", "bad name!");
      });
      expect(result.current.errors?.name).toBeDefined();

      // Then fix it
      act(() => {
        result.current.handlers.setScheduleNewState("name", "good_name");
      });
      expect(result.current.errors?.name).toBeUndefined();
    });

    it("updates description without touching errors", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setScheduleNewState(
          "description",
          "My schedule",
        );
      });

      expect(result.current.scheduleState.description).toBe("My schedule");
    });
  });

  describe("setCronPausedState", () => {
    it("toggles paused from false to true", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setCronPausedState();
      });

      expect(result.current.scheduleState.paused).toBe(true);
    });

    it("toggles paused back to false", () => {
      const { result } = renderHook(() =>
        useFormHandlersWrapper({ ...baseState, paused: true }),
      );

      act(() => {
        result.current.handlers.setCronPausedState();
      });

      expect(result.current.scheduleState.paused).toBe(false);
    });
  });

  describe("setZoneId", () => {
    it("updates zoneId in state", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setZoneId("America/New_York");
      });

      expect(result.current.scheduleState.zoneId).toBe("America/New_York");
    });
  });

  describe("setWorkflowInputTemplatesState", () => {
    it("updates workflowInputTemplate for valid JSON", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setWorkflowInputTemplatesState(
          '{"param":"value"}',
        );
      });

      expect(result.current.scheduleState.workflowInputTemplate).toBe(
        '{"param":"value"}',
      );
      expect(result.current.couldNotParseJson).toBe(false);
    });

    it("sets couldNotParseJson and does NOT update state for invalid JSON", () => {
      const { result } = renderHook(() =>
        useFormHandlersWrapper({
          ...baseState,
          workflowInputTemplate: '{"a":1}',
        }),
      );

      act(() => {
        result.current.handlers.setWorkflowInputTemplatesState("{broken");
      });

      expect(result.current.couldNotParseJson).toBe(true);
      // State should not have been updated to the broken value
      expect(result.current.scheduleState.workflowInputTemplate).toBe(
        '{"a":1}',
      );
    });
  });

  describe("setWorkflowTasksToDomainState", () => {
    it("updates taskToDomain for valid JSON", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setWorkflowTasksToDomainState(
          '{"task1":"domain1"}',
        );
      });

      expect(result.current.scheduleState.taskToDomain).toBe(
        '{"task1":"domain1"}',
      );
      expect(result.current.couldNotParseJson).toBe(false);
    });

    it("sets couldNotParseJson and does NOT update state for invalid JSON", () => {
      const { result } = renderHook(() =>
        useFormHandlersWrapper({ ...baseState, taskToDomain: '{"t":"d"}' }),
      );

      act(() => {
        result.current.handlers.setWorkflowTasksToDomainState("{broken");
      });

      expect(result.current.couldNotParseJson).toBe(true);
      expect(result.current.scheduleState.taskToDomain).toBe('{"t":"d"}');
    });
  });

  describe("setWorkflowCorrelationIdState", () => {
    it("updates workflowCorrelationId", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.setWorkflowCorrelationIdState("corr-xyz");
      });

      expect(result.current.scheduleState.workflowCorrelationId).toBe(
        "corr-xyz",
      );
    });
  });

  describe("handleIdempotencyValues", () => {
    it("sets idempotencyKey and defaults strategy to RETURN_EXISTING", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.handleIdempotencyValues({
          idempotencyKey: "my-key",
          idempotencyStrategy: undefined,
        });
      });

      expect(result.current.scheduleState.workflowIdempotencyKey).toBe(
        "my-key",
      );
      expect(result.current.scheduleState.workflowIdempotencyStrategy).toBe(
        IdempotencyStrategyEnum.RETURN_EXISTING,
      );
    });

    it("uses provided idempotencyStrategy when key is set", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      act(() => {
        result.current.handlers.handleIdempotencyValues({
          idempotencyKey: "my-key",
          idempotencyStrategy: IdempotencyStrategyEnum.FAIL,
        });
      });

      expect(result.current.scheduleState.workflowIdempotencyStrategy).toBe(
        IdempotencyStrategyEnum.FAIL,
      );
    });

    it("clears strategy when idempotencyKey is empty/undefined", () => {
      const { result } = renderHook(() =>
        useFormHandlersWrapper({
          ...baseState,
          workflowIdempotencyKey: "existing-key",
          workflowIdempotencyStrategy: IdempotencyStrategyEnum.RETURN_EXISTING,
        }),
      );

      act(() => {
        result.current.handlers.handleIdempotencyValues({
          idempotencyKey: "",
          idempotencyStrategy: undefined,
        });
      });

      expect(result.current.scheduleState.workflowIdempotencyKey).toBe("");
      expect(
        result.current.scheduleState.workflowIdempotencyStrategy,
      ).toBeUndefined();
    });
  });

  describe("handleScheduleStartTime / handleScheduleEndTime", () => {
    it("updates scheduleStartTime", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());
      const ts = 1704067200000;

      act(() => {
        result.current.handlers.handleScheduleStartTime(ts);
      });

      expect(result.current.scheduleState.scheduleStartTime).toBe(ts);
    });

    it("updates scheduleEndTime", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());
      const ts = 1735689600000;

      act(() => {
        result.current.handlers.handleScheduleEndTime(ts);
      });

      expect(result.current.scheduleState.scheduleEndTime).toBe(ts);
    });
  });

  describe("getHighlightedPart", () => {
    it("sets highlightedPart to the index of the cron field at the cursor", () => {
      const { result } = renderHook(() => useFormHandlersWrapper());

      // "0 0 12 * * ?" — cursor at position 0 (inside '0') → part 0
      act(() => {
        result.current.handlers.getHighlightedPart("0 0 12 * * ?", 1);
      });
      expect(result.current.highlightedPart).toBe(0);

      // cursor at position 3 (inside second '0') → part 1
      act(() => {
        result.current.handlers.getHighlightedPart("0 0 12 * * ?", 3);
      });
      expect(result.current.highlightedPart).toBe(1);

      // cursor at position 5 (inside '12') → part 2
      act(() => {
        result.current.handlers.getHighlightedPart("0 0 12 * * ?", 5);
      });
      expect(result.current.highlightedPart).toBe(2);
    });
  });
});

// ---------------------------------------------------------------------------
// useScheduleState
// ---------------------------------------------------------------------------

describe("useScheduleState", () => {
  it("initializes with default state when no execution or schedule is provided", () => {
    const { result } = renderHook(() => useScheduleState(null, null));

    expect(result.current.scheduleState.name).toBe("");
    expect(result.current.scheduleState.cronExpression).toBe("");
    expect(result.current.scheduleState.paused).toBe(false);
    expect(result.current.scheduleState.zoneId).toBe("UTC");
    expect(result.current.scheduleState.workflowType).toBeNull();
  });

  it("pre-populates state from latestExecution", () => {
    const execution = {
      workflowName: "myWorkflow",
      workflowVersion: 2,
      workflowDefinition: { inputParameters: ["param1", "param2"] },
      taskToDomain: { task1: "domain1" },
    };

    const { result } = renderHook(() => useScheduleState(execution, null));

    expect(result.current.scheduleState.workflowType).toBe("myWorkflow");
    expect(result.current.scheduleState.workflowVersion).toBe("2");
    expect(result.current.scheduleState.taskToDomain).toContain("task1");
  });

  it("initializeFromSchedule populates state from a full schedule object", () => {
    const { result } = renderHook(() => useScheduleState(null, null));

    const schedule = {
      name: "test-schedule",
      description: "A test",
      cronExpression: "0 0 10 * * ?",
      paused: true,
      runCatchupScheduleInstances: false,
      scheduleStartTime: 1704067200000,
      scheduleEndTime: 1735689600000,
      zoneId: "Asia/Tokyo",
      startWorkflowRequest: {
        name: "workflowA",
        version: 3,
        input: { key: "val" },
        correlationId: "corr-1",
        taskToDomain: { t1: "d1" },
        priority: "3",
      },
    };

    act(() => {
      result.current.initializeFromSchedule(schedule);
    });

    expect(result.current.scheduleState.name).toBe("test-schedule");
    expect(result.current.scheduleState.description).toBe("A test");
    expect(result.current.scheduleState.cronExpression).toBe("0 0 10 * * ?");
    expect(result.current.scheduleState.paused).toBe(true);
    expect(result.current.scheduleState.zoneId).toBe("Asia/Tokyo");
    expect(result.current.scheduleState.workflowType).toBe("workflowA");
    expect(result.current.scheduleState.workflowVersion).toBe("3");
    expect(result.current.scheduleState.workflowCorrelationId).toBe("corr-1");
    expect(result.current.scheduleState.priority).toBe("3");
  });

  it("initializeFromSchedule treats null cronExpression as empty string", () => {
    const { result } = renderHook(() => useScheduleState(null, null));

    act(() => {
      result.current.initializeFromSchedule({
        name: "s",
        cronExpression: null,
        startWorkflowRequest: {},
      });
    });

    expect(result.current.scheduleState.cronExpression).toBe("");
  });

  it("initializeFromSchedule does nothing when called with null", () => {
    const { result } = renderHook(() => useScheduleState(null, null));
    const stateBefore = result.current.scheduleState;

    act(() => {
      result.current.initializeFromSchedule(null);
    });

    expect(result.current.scheduleState).toStrictEqual(stateBefore);
  });

  it("initializeFromExecution does nothing when workflowName is missing", () => {
    const { result } = renderHook(() => useScheduleState(null, null));
    const stateBefore = result.current.scheduleState;

    act(() => {
      result.current.initializeFromExecution({});
    });

    expect(result.current.scheduleState).toStrictEqual(stateBefore);
  });

  it("original state is set correctly after initializeFromSchedule", () => {
    const { result } = renderHook(() => useScheduleState(null, null));

    act(() => {
      result.current.initializeFromSchedule({
        name: "my-sched",
        cronExpression: "0 */5 * * * ?",
        paused: false,
        runCatchupScheduleInstances: true,
        startWorkflowRequest: {
          name: "wf",
          version: 1,
          input: {},
          taskToDomain: {},
        },
      });
    });

    expect(result.current.original.name).toBe("my-sched");
    expect(result.current.original.cronExpression).toBe("0 */5 * * * ?");
  });
});

// ---------------------------------------------------------------------------
// useWorkflowConfig
// ---------------------------------------------------------------------------

function buildWorkflowDefByVersions(
  workflows: Record<string, { versions: string[]; inputParams?: string[] }>,
) {
  type WorkflowDef = { inputParameters: string[] };

  const lookups = new Map<string, string[]>();
  const values = new Map<string, Map<string, WorkflowDef>>();

  for (const [name, { versions, inputParams }] of Object.entries(workflows)) {
    lookups.set(name, versions);
    const versionMap = new Map<string, WorkflowDef>();
    for (const v of versions) {
      versionMap.set(v, {
        inputParameters: inputParams ?? [],
      });
    }
    values.set(name, versionMap);
  }

  return new Map<
    string,
    Map<string, string[]> | Map<string, Map<string, WorkflowDef>>
  >([
    ["lookups", lookups],
    ["values", values],
  ]);
}

describe("useWorkflowConfig", () => {
  const defsByVersions = buildWorkflowDefByVersions({
    WorkflowA: { versions: ["1", "2", "3"], inputParams: ["paramA", "paramB"] },
    WorkflowB: { versions: ["1"], inputParams: ["paramX"] },
  });

  it("returns all workflow names", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, null, [], ""),
    );

    expect(result.current.workflowNames).toContain("WorkflowA");
    expect(result.current.workflowNames).toContain("WorkflowB");
    expect(result.current.workflowNames).toHaveLength(2);
  });

  it("returns empty array when workflowDefByVersions is undefined", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(undefined, null, [], ""),
    );

    expect(result.current.workflowNames).toEqual([]);
  });

  it("returns versions for the current workflow type", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, "WorkflowA", [], ""),
    );

    expect(result.current.workflowVersions).toEqual(["1", "2", "3"]);
  });

  it("falls back to currentWorkflowVersions when no type is set", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, null, ["v1", "v2"], ""),
    );

    expect(result.current.workflowVersions).toEqual(["v1", "v2"]);
  });

  it("setWorkflowType returns correct versions and input template", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, null, [], ""),
    );

    const output = result.current.setWorkflowType("WorkflowA");

    expect(output.workflowVersions).toEqual(["1", "2", "3"]);
    // input template built from inputParameters: paramA and paramB
    const parsed = JSON.parse(output.workflowInputTemplate);
    expect(parsed).toHaveProperty("paramA");
    expect(parsed).toHaveProperty("paramB");
  });

  it("setWorkflowType falls back to currentWorkflowInputTemplate when inputParameters is absent", () => {
    // buildWorkflowDefByVersions always sets inputParameters; create the map
    // manually so the def has no inputParameters key at all.
    const lookups = new Map([["NoParamsWorkflow", ["1"]]]);
    const values = new Map([
      ["NoParamsWorkflow", new Map([["1", {}]])], // no inputParameters key
    ]);
    const defsNoParams = new Map<string, unknown>([
      ["lookups", lookups],
      ["values", values],
    ]);

    const { result } = renderHook(() =>
      useWorkflowConfig(defsNoParams, null, [], "existing-template"),
    );

    const output = result.current.setWorkflowType("NoParamsWorkflow");
    // getTemplateFromInputParams(undefined) returns "" which is falsy,
    // so the hook falls back to currentWorkflowInputTemplate.
    expect(output.workflowInputTemplate).toBe("existing-template");
  });

  it("setWorkflowVersion with 'Latest version' resolves to last entry", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, "WorkflowA", ["1", "2", "3"], ""),
    );

    const output = result.current.setWorkflowVersion(
      "Latest version",
      "WorkflowA",
    );
    const parsed = JSON.parse(output.workflowInputTemplate);
    expect(parsed).toHaveProperty("paramA");
  });

  it("setWorkflowVersion with a specific version returns template for that version", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(defsByVersions, "WorkflowB", ["1"], ""),
    );

    const output = result.current.setWorkflowVersion("1", "WorkflowB");
    const parsed = JSON.parse(output.workflowInputTemplate);
    expect(parsed).toHaveProperty("paramX");
  });

  it("setWorkflowVersion with null returns existing template", () => {
    const { result } = renderHook(() =>
      useWorkflowConfig(
        defsByVersions,
        "WorkflowA",
        ["1", "2", "3"],
        "fallback",
      ),
    );

    const output = result.current.setWorkflowVersion(null, "WorkflowA");
    expect(output.workflowInputTemplate).toBe("fallback");
  });
});
