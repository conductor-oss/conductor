/**
 * Integration tests — system / operator task forms
 *
 * Seeds a workflow with many wired OSS task types (definition only — no
 * workers) and clicks each node so the corresponding TaskForm modules load
 * under E2E coverage.
 */

import { expect, test } from "../coverage-fixture";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  type WorkflowDef,
} from "./api-client";
import { fitDiagramToScreen, setDiagramSnapshotViewport } from "./helpers";

const RUN_ID = Date.now();
const WF_NAME = `e2e_sys_forms_${RUN_ID}`;
const TARGET_WF = `e2e_sys_target_${RUN_ID}`;

const TARGET_DEF: WorkflowDef = {
  name: TARGET_WF,
  version: 1,
  description: "Target for START_WORKFLOW — safe to delete",
  tasks: [
    {
      name: "noop",
      taskReferenceName: "noop_ref",
      type: "SET_VARIABLE",
      inputParameters: { ok: true },
    },
  ],
};

/** One linear chain of under-exercised wired task types. */
const SYSTEM_FORMS_DEF: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: "Created by Playwright E2E — system task form tour",
  tasks: [
    {
      name: "event_task",
      taskReferenceName: "event_ref",
      type: "EVENT",
      sink: "sqs:e2e_internal_event",
      inputParameters: {},
    },
    {
      name: "dynamic_task",
      taskReferenceName: "dynamic_ref",
      type: "DYNAMIC",
      dynamicTaskNameParam: "taskToExecute",
      inputParameters: { taskToExecute: "simple_worker" },
    },
    {
      name: "fork_join_dynamic",
      taskReferenceName: "dyn_fork_ref",
      type: "FORK_JOIN_DYNAMIC",
      dynamicForkTasksParam: "dynamicTasks",
      dynamicForkTasksInputParamName: "dynamicTasksInput",
      inputParameters: { dynamicTasks: "", dynamicTasksInput: "" },
    },
    {
      name: "dyn_fork_join",
      taskReferenceName: "dyn_join_ref",
      type: "JOIN",
      joinOn: [],
    },
    {
      name: "get_workflow",
      taskReferenceName: "get_wf_ref",
      type: "GET_WORKFLOW",
      inputParameters: { id: "", includeTasks: false },
    },
    {
      name: "start_workflow",
      taskReferenceName: "start_wf_ref",
      type: "START_WORKFLOW",
      inputParameters: {
        startWorkflow: { name: TARGET_WF, input: {} },
      },
    },
    {
      name: "yield_task",
      taskReferenceName: "yield_ref",
      type: "YIELD",
    },
    {
      name: "terminate_workflow",
      taskReferenceName: "term_wf_ref",
      type: "TERMINATE_WORKFLOW",
      inputParameters: {
        workflowId: [""],
        terminationReason: "",
        triggerFailureWorkflow: false,
      },
    },
    {
      name: "http_poll",
      taskReferenceName: "http_poll_ref",
      type: "HTTP_POLL",
      inputParameters: {
        http_request: {
          uri: "https://orkes-api-tester.orkesconductor.com/api/hello/get",
          method: "GET",
          accept: "application/json",
          contentType: "application/json",
          terminationCondition: "(function(){ return true; })();",
          pollingInterval: "60",
          pollingStrategy: "FIXED",
          encode: true,
        },
      },
    },
    {
      name: "kafka_publish",
      taskReferenceName: "kafka_ref",
      type: "KAFKA_PUBLISH",
      inputParameters: {
        kafka_request: {
          topic: "e2eTopic",
          value: "e2e",
          bootStrapServers: "localhost:9092",
          key: "k",
          keySerializer:
            "org.apache.kafka.common.serialization.StringSerializer",
        },
      },
    },
    {
      name: "update_task",
      taskReferenceName: "update_task_ref",
      type: "UPDATE_TASK",
      inputParameters: {
        taskStatus: "COMPLETED",
        mergeOutput: false,
        workflowId: "${workflow.workflowId}",
        taskRefName: "event_ref",
      },
    },
    {
      name: "get_signed_jwt",
      taskReferenceName: "jwt_ref",
      type: "GET_SIGNED_JWT",
      inputParameters: {
        subject: "",
        issuer: "",
        privateKey: "",
        privateKeyId: "",
        audience: "",
        ttlInSecond: 0,
        scopes: [],
        algorithm: "RS256",
      },
    },
    {
      name: "simple_worker",
      taskReferenceName: "simple_ref",
      type: "SIMPLE",
    },
    {
      name: "business_rule",
      taskReferenceName: "biz_rule_ref",
      type: "BUSINESS_RULE",
      inputParameters: {
        ruleFileLocation: "https://example.com/rules.xlsx",
        executionStrategy: "FIRE_FIRST",
        cacheTimeoutMinutes: 60,
        inputColumns: {},
        outputColumns: [],
      },
    },
    {
      name: "query_processor",
      taskReferenceName: "query_ref",
      type: "QUERY_PROCESSOR",
      inputParameters: {
        workflowNames: [],
        statuses: [],
        correlationIds: [],
        queryType: "CONDUCTOR_API",
      },
    },
    {
      name: "ops_genie",
      taskReferenceName: "opsgenie_ref",
      type: "OPS_GENIE",
      inputParameters: {
        alias: "e2e",
        description: "e2e",
        message: "e2e",
        visibleTo: [],
        responders: [],
      },
    },
    {
      name: "jdbc_task",
      taskReferenceName: "jdbc_ref",
      type: "JDBC",
      inputParameters: {
        integrationName: "",
        statement: "SELECT 1",
        parameters: [],
        type: "SELECT",
      },
    },
  ],
};

const FORM_CLICKS: Array<{ label: string; type: string }> = [
  { label: "event_ref", type: "EVENT" },
  { label: "dynamic_ref", type: "DYNAMIC" },
  { label: "dyn_fork_ref", type: "FORK_JOIN_DYNAMIC" },
  { label: "get_wf_ref", type: "GET_WORKFLOW" },
  { label: "start_wf_ref", type: "START_WORKFLOW" },
  { label: "yield_ref", type: "YIELD" },
  { label: "term_wf_ref", type: "TERMINATE_WORKFLOW" },
  { label: "http_poll_ref", type: "HTTP_POLL" },
  { label: "kafka_ref", type: "KAFKA_PUBLISH" },
  { label: "update_task_ref", type: "UPDATE_TASK" },
  { label: "jwt_ref", type: "GET_SIGNED_JWT" },
  { label: "simple_ref", type: "SIMPLE" },
  { label: "biz_rule_ref", type: "BUSINESS_RULE" },
  { label: "query_ref", type: "QUERY_PROCESSOR" },
  { label: "opsgenie_ref", type: "OPS_GENIE" },
  { label: "jdbc_ref", type: "JDBC" },
];

test.beforeAll(async () => {
  await createWorkflowDef(TARGET_DEF);
  await createWorkflowDef(SYSTEM_FORMS_DEF);
});

test.afterAll(async () => {
  await deleteWorkflowDef(WF_NAME).catch(() => {});
  await deleteWorkflowDef(TARGET_WF).catch(() => {});
});

test("system task form tour opens each wired task type", async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto(`/workflowDef/${WF_NAME}/1`);
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#workflow-name-display")).toBeVisible({
    timeout: 15_000,
  });

  await setDiagramSnapshotViewport(page);
  await fitDiagramToScreen(page);

  for (const { label, type } of FORM_CLICKS) {
    await fitDiagramToScreen(page);
    const node = page.getByText(label, { exact: true }).first();
    await node.scrollIntoViewIfNeeded();
    await node.click({ force: true });
    await expect(
      page.locator("#maybe-task-form").getByText(type, { exact: true }),
    ).toBeVisible({ timeout: 15_000 });
  }
});
