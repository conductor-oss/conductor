import { workflowToNodeEdges } from "./core";
import {
  simpleDiagram,
  populationMinMax,
  loanBanking,
  simpleLoopSample,
  nestedForkJoin,
} from "../../../../../testData/diagramTests";

const nodesToMap = (nodes) =>
  nodes.reduce((acc, node) => ({ ...acc, [node.id]: node }), {});

const allEdgesAreConnectedToNodes = (edges, nodeMap) =>
  edges.every((edge) => {
    if (nodeMap[edge.from] && nodeMap[edge.to]) {
      return true;
    }
    //console.log(JSON.stringify(edge, null, 2));
    return false;
  });

describe("workflowToNodeEdges", () => {
  it("should convert a workflow to a list of edges", async () => {
    const simpleDiagramNodesEdges = await workflowToNodeEdges(simpleDiagram);
    const nodeMap = nodesToMap(simpleDiagramNodesEdges.nodes);

    expect(
      allEdgesAreConnectedToNodes(simpleDiagramNodesEdges.edges, nodeMap),
    ).toBe(true);
  });

  it("should convert a workflow with population min/max to a list of edges", async () => {
    const populationMinMaxNodesEdges =
      await workflowToNodeEdges(populationMinMax);
    const nodeMap = nodesToMap(populationMinMaxNodesEdges.nodes);

    expect(
      allEdgesAreConnectedToNodes(populationMinMaxNodesEdges.edges, nodeMap),
    ).toBe(true);
  });

  it("should convert a workflow with a loop to a list of edges", async () => {
    const simpleLoopSampleNodesEdges =
      await workflowToNodeEdges(simpleLoopSample);
    const nodeMap = nodesToMap(simpleLoopSampleNodesEdges.nodes);
    expect(
      allEdgesAreConnectedToNodes(simpleLoopSampleNodesEdges.edges, nodeMap),
    ).toBe(true);
  });

  it("should convert a workflow with a nested fork join to a list of edges", async () => {
    const loanBankingNodesAndEdges = await workflowToNodeEdges(loanBanking);
    const nodeMap = nodesToMap(loanBankingNodesAndEdges.nodes);
    expect(
      allEdgesAreConnectedToNodes(loanBankingNodesAndEdges.edges, nodeMap),
    ).toBe(true);
  });

  it("should convert a workflow with a loan banking to a list of edges", async () => {
    const nestedForkJoinNodesAndEdges =
      await workflowToNodeEdges(nestedForkJoin);
    const nodeMap = nodesToMap(nestedForkJoinNodesAndEdges.nodes);
    expect(
      allEdgesAreConnectedToNodes(nestedForkJoinNodesAndEdges.edges, nodeMap),
    ).toBe(true);
  });
});
