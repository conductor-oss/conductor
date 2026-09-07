import { interpret } from "xstate";
import { executionMachine } from "./machine";
import { ExecutionActionTypes, ExecutionTabs } from "./types";
import { isAgentWorkflowExecution } from "./guards";

const agentExecution = {
  workflowId: "237f23c8-2337-4174-823e-addbebee76ef",
  workflowType: "classifier_greeter",
  workflowName: "classifier_greeter",
  status: "FAILED",
  version: 1,
  tasks: [],
  input: {},
  output: {},
  startTime: 1,
  endTime: 2,
  workflowDefinition: {
    name: "classifier_greeter",
    version: 1,
    tasks: [],
    metadata: {
      agent_capabilities: ["simple"],
      agentDef: { name: "classifier_greeter" },
      agent_sdk: "conductor",
    },
  },
};

const plainExecution = {
  ...agentExecution,
  workflowType: "some_plain_workflow",
  workflowName: "some_plain_workflow",
  workflowDefinition: {
    name: "some_plain_workflow",
    version: 1,
    tasks: [],
    metadata: {},
  },
};

/** Interprets the real executionMachine end-to-end with a mocked fetch, and
 * resolves once the machine has settled into its parallel `init` state. */
function landOnInitWith(execution: unknown) {
  return new Promise<{ currentTab: ExecutionTabs }>((resolve) => {
    const machine = executionMachine.withConfig({
      services: {
        fetchExecution: async () => execution,
      } as any,
    });
    const service = interpret(machine)
      .onTransition((state) => {
        if (state.matches("init")) {
          service.stop();
          resolve({ currentTab: state.context.currentTab });
        }
      })
      .start();
    service.send({
      type: ExecutionActionTypes.UPDATE_EXECUTION,
      executionId: (execution as any).workflowId,
    });
  });
}

describe("isAgentWorkflowExecution guard", () => {
  it("is true when the workflow definition carries the AgentSpan stamp (agentDef/agent_sdk)", () => {
    expect(isAgentWorkflowExecution({ execution: agentExecution } as any)).toBe(
      true,
    );
  });

  it("is false for a plain, untagged workflow", () => {
    expect(isAgentWorkflowExecution({ execution: plainExecution } as any)).toBe(
      false,
    );
  });

  it("is false when there is no execution loaded yet", () => {
    expect(isAgentWorkflowExecution({} as any)).toBe(false);
  });
});

describe("executionMachine default tab", () => {
  // Regression test: initDiagram's `always` transition used to route the
  // *state node* to "agentExecution" without updating context.currentTab
  // (which LeftPanelTabs/Execution.tsx actually read), so agent executions
  // silently rendered the Diagram tab instead of the debugger.
  it("defaults to AGENT_EXECUTION_TAB — and keeps context.currentTab in sync — for an agent-classified execution", async () => {
    const { currentTab } = await landOnInitWith(agentExecution);
    expect(currentTab).toBe(ExecutionTabs.AGENT_EXECUTION_TAB);
  });

  it("defaults to DIAGRAM_TAB for a plain workflow execution", async () => {
    const { currentTab } = await landOnInitWith(plainExecution);
    expect(currentTab).toBe(ExecutionTabs.DIAGRAM_TAB);
  });
});
