import {
  simpleDiagram,
  subWorkflowWithinAFork,
  wfWithWhileWithSubWorkflow,
} from "../../../../../testData/diagramTests";
import { tasksAsNodes } from "./core";
import { processSubWorkflow } from "./subWorkflow";

const name = "testing_Errors";

describe("processSubWorkflow", () => {
  const subWorkflowTask = {
    name: "sub_workflow_x",
    taskReferenceName: "wf4",
    inputParameters: {
      mod: "${task_1.output.mod}",
      oddEven: "${task_1.output.oddEven}",
    },
    type: "SUB_WORKFLOW",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    subWorkflowParam: {
      name,
      version: 1,
    },
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
  };

  it("Should include tasks provided by workflowFetcher to workflow", async () => {
    const result = await processSubWorkflow(
      subWorkflowTask,
      [],
      tasksAsNodes,
      (__name, __version) => Promise.resolve(simpleDiagram),
    );
    // Will include the node for subWorkflowTask
    expect(
      result.nodes.find(({ id }) => id === subWorkflowTask.taskReferenceName),
    ).not.toBeUndefined();
    expect(result.nodes.length).toEqual(simpleDiagram.tasks.length + 1);
  });

  it("Should return empty if fetching failed", async () => {
    const result = await processSubWorkflow(
      subWorkflowTask,
      [],
      tasksAsNodes,
      (__name, __version) => Promise.reject("Something Failed"),
    );
    expect(result.nodes.length).toEqual(1);
    expect(result.nodes[0].id).toEqual(subWorkflowTask.taskReferenceName);
  });

  it("Should not fetch if name provided is empty", async () => {
    const result = await processSubWorkflow(
      { ...subWorkflowTask, subWorkflowParam: { name: "" } },
      [],
      tasksAsNodes,
      (__name, __version) => Promise.resolve([{ rubish: "true" }]),
    );
    expect(result.nodes.length).toEqual(1);
    expect(result.nodes[0].id).toEqual(subWorkflowTask.taskReferenceName);
  });

  it("Should add a suffix to every id within the sub-workflow created nodes", async () => {
    const { nodes, edges } = await processSubWorkflow(
      subWorkflowTask,
      [],
      tasksAsNodes,
      (__name, __version) => Promise.resolve({ ...simpleDiagram, name }),
    );

    const [subWorkflowNode, ...subWorkflowNodes] = nodes;

    expect(subWorkflowNode.id).toBe(subWorkflowTask.taskReferenceName);
    expect(subWorkflowNodes.every(({ id }) => id.includes(name))).toBeTruthy();

    expect(
      subWorkflowNodes.every(({ ports }) =>
        ports.every(({ id }) => id.includes(name)),
      ),
    ).toBeTruthy();

    expect(
      edges.every(({ from, to }) => from.includes(name) && to.includes(name)),
    ).toBeTruthy();

    expect(edges.every(({ id }) => id.includes(name))).toBeTruthy();
  });

  it("Should expand the sub-workflow if the workflow is within a WHILE", async () => {
    const { nodes } = await tasksAsNodes(wfWithWhileWithSubWorkflow.tasks, {
      expandSubWorkflow: true,
      subWorkFlowFetcher: (__name, __version) => Promise.resolve(simpleDiagram),
    });
    const expandedSubWorkflowNodes = nodes.filter(
      ({ parent }) => parent === "sample_task_name_sub_workflow_ref",
    );
    expect(expandedSubWorkflowNodes.length > 1).toBeTruthy();
  });

  it("Should expand the sub-workflow if the workflow is within a FORK", async () => {
    const { nodes } = await tasksAsNodes(subWorkflowWithinAFork.tasks, {
      expandSubWorkflow: true,
      subWorkFlowFetcher: (__name, __version) => Promise.resolve(simpleDiagram),
    });
    const expandedSubWorkflowNodes = nodes.filter(
      ({ parent }) => parent === "sample_task_name_sub_workflow_ref",
    );
    expect(expandedSubWorkflowNodes.length > 1).toBeTruthy();
  });
});
