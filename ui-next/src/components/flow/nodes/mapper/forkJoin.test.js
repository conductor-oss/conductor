import { tasksAsNodes } from "./core";
import { processForkJoinTasks } from "./forkJoin";
import { forkJoinTask } from "../../../../testData/diagramTests";

describe("processForkJoin", () => {
  it("Should return nodes and edges for processForkJoin", async () => {
    const result = await processForkJoinTasks(forkJoinTask, [], tasksAsNodes);
    expect(result.edges.length).toEqual(forkJoinTask.forkTasks.length); // given that there is only one task per array
    expect(result.nodes.length).toEqual(forkJoinTask.forkTasks.flat().length);
  });
});
