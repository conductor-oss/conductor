import { ExecutionTask } from "types/Execution";
import { processTasksToGroupsAndItems } from "./timelineUtils";

// Helper function to create mock tasks
const createMockTask = (overrides = {}) => ({
  taskId: "task-1",
  referenceTaskName: "task_ref",
  status: "COMPLETED",
  startTime: Date.now() - 1000,
  endTime: Date.now(),
  scheduledTime: null,
  workflowTask: {
    name: "Task Name",
    taskReferenceName: "task_ref",
    type: "SIMPLE",
  },
  inputData: {},
  iteration: 0,
  ...overrides,
});

// Helper function to create mock execution status map
const createMockExecutionStatusMap = (overrides = {}) => ({
  task_ref: {
    related: null,
  },
  ...overrides,
});

describe("Timeline Groups and Items Processing", () => {
  describe("Basic Task Processing (Ideal Case)", () => {
    it("should create groups and items for normal tasks", () => {
      const tasks = [
        createMockTask({
          taskId: "task-1",
          referenceTaskName: "task1_ref",
          workflowTask: {
            name: "Task 1",
            taskReferenceName: "task1_ref",
            type: "SIMPLE",
          },
        }),
        createMockTask({
          taskId: "task-2",
          referenceTaskName: "task2_ref",
          workflowTask: {
            name: "Task 2",
            taskReferenceName: "task2_ref",
            type: "SIMPLE",
          },
        }),
      ];

      const executionStatusMap = createMockExecutionStatusMap();
      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        executionStatusMap,
      );

      expect(groups).toHaveLength(2);
      expect(_items).toHaveLength(2);
      expect(groups[0].id).toBe("task1_ref");
      expect(groups[1].id).toBe("task2_ref");
      expect(_items[0].id).toBe("task-1");
      expect(_items[1].id).toBe("task-2");
    });

    it("should set treeLevel based on executionStatusMap", () => {
      const tasks = [
        createMockTask({
          referenceTaskName: "task1_ref",
          workflowTask: {
            taskReferenceName: "task1_ref",
            type: "SIMPLE",
          },
        }),
      ];

      const executionStatusMap = createMockExecutionStatusMap({
        task1_ref: {
          related: "some_related_task",
        },
      });

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        executionStatusMap,
      );

      expect(groups).toHaveLength(1);
      expect(groups[0].treeLevel).toBe(2);
    });
  });

  describe("FORK_JOIN_DYNAMIC - Ideal Case (Before Fix Would Work)", () => {
    it("should set nestedGroups correctly when forkedTasks match group IDs exactly", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["child1", "child2"],
          },
        }),
        createMockTask({
          taskId: "child1-task",
          referenceTaskName: "child1",
          workflowTask: {
            name: "Child 1",
            taskReferenceName: "child1",
            type: "SIMPLE",
          },
        }),
        createMockTask({
          taskId: "child2-task",
          referenceTaskName: "child2",
          workflowTask: {
            name: "Child 2",
            taskReferenceName: "child2",
            type: "SIMPLE",
          },
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(3);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      expect(forkGroup?.nestedGroups).toEqual(["child1", "child2"]);
    });
  });

  describe("FORK_JOIN_DYNAMIC - Fix Scenario (After Fix)", () => {
    it("should map forkedTasks with iteration suffix when exact match not found", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["child1", "child2"],
          },
          iteration: 2,
        }),
        createMockTask({
          taskId: "child1-task-2",
          referenceTaskName: "child1__2",
          workflowTask: {
            name: "Child 1",
            taskReferenceName: "child1",
            type: "SIMPLE",
          },
          iteration: 2,
        }),
        createMockTask({
          taskId: "child2-task-2",
          referenceTaskName: "child2__2",
          workflowTask: {
            name: "Child 2",
            taskReferenceName: "child2",
            type: "SIMPLE",
          },
          iteration: 2,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(3);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      // This is the key test - the fix should map "child1" to "child1__2" and "child2" to "child2__2"
      expect(forkGroup?.nestedGroups).toEqual(["child1__2", "child2__2"]);
    });

    it("should handle multiple iterations correctly", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["child1", "child2"],
          },
          iteration: 3,
        }),
        createMockTask({
          taskId: "child1-task-3",
          referenceTaskName: "child1__3",
          workflowTask: {
            name: "Child 1",
            taskReferenceName: "child1",
            type: "SIMPLE",
          },
          iteration: 3,
        }),
        createMockTask({
          taskId: "child2-task-3",
          referenceTaskName: "child2__3",
          workflowTask: {
            name: "Child 2",
            taskReferenceName: "child2",
            type: "SIMPLE",
          },
          iteration: 3,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(3);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      expect(forkGroup?.nestedGroups).toEqual(["child1__3", "child2__3"]);
    });
  });

  describe("FORK_JOIN_DYNAMIC - Mixed Scenario", () => {
    it("should handle mix of exact matches and iteration suffixes", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["exact_match", "needs_suffix"],
          },
          iteration: 2,
        }),
        createMockTask({
          taskId: "exact-match-task",
          referenceTaskName: "exact_match",
          workflowTask: {
            name: "Exact Match",
            taskReferenceName: "exact_match",
            type: "SIMPLE",
          },
        }),
        createMockTask({
          taskId: "needs-suffix-task",
          referenceTaskName: "needs_suffix__2",
          workflowTask: {
            name: "Needs Suffix",
            taskReferenceName: "needs_suffix",
            type: "SIMPLE",
          },
          iteration: 2,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(3);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      // Should have exact match for "exact_match" and suffixed match for "needs_suffix"
      expect(forkGroup?.nestedGroups).toEqual([
        "exact_match",
        "needs_suffix__2",
      ]);
    });
  });

  describe("FORK_JOIN_DYNAMIC - Missing Groups", () => {
    it("should fallback to original taskId when neither exact nor suffixed ID exists", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["missing_task"],
          },
          iteration: 2,
        }),
        // No matching tasks for "missing_task" or "missing_task__2"
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      // Should fallback to original taskId when neither exact nor suffixed ID exists
      expect(forkGroup?.nestedGroups).toEqual(["missing_task"]);
    });
  });

  describe("Edge Cases", () => {
    it("should handle empty tasks array", () => {
      const [groups, _items] = processTasksToGroupsAndItems([], {});

      expect(groups).toHaveLength(0);
      expect(_items).toHaveLength(0);
    });

    it("should handle tasks without startTime or endTime", () => {
      const tasks = [
        createMockTask({
          startTime: 0,
          endTime: 0,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      expect(_items).toHaveLength(0); // No items should be created when no start/end time
    });

    it("should handle FORK_JOIN_DYNAMIC without inputData.forkedTasks", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {},
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      expect(forkGroup?.nestedGroups).toBeUndefined();
    });

    it("should handle FORK_JOIN_DYNAMIC with null inputData", () => {
      const tasks = [
        createMockTask({
          taskId: "fork-task-1",
          referenceTaskName: "fork_ref",
          workflowTask: {
            name: "Fork Task",
            taskReferenceName: "fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: null,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      const forkGroup = groups.find((g) => g.id === "fork_ref");
      expect(forkGroup?.nestedGroups).toBeUndefined();
    });

    it("should handle tasks with only startTime", () => {
      const tasks = [
        createMockTask({
          startTime: Date.now() - 1000,
          endTime: 0,
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      expect(_items).toHaveLength(1);
      expect(_items[0].start).toBeInstanceOf(Date);
      expect(_items[0].end).toBeInstanceOf(Date);
    });

    it("should handle tasks with only endTime", () => {
      const tasks = [
        createMockTask({
          startTime: 0,
          endTime: Date.now(),
        }),
      ];

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        {},
      );

      expect(groups).toHaveLength(1);
      expect(_items).toHaveLength(1);
      expect(_items[0].start).toBeInstanceOf(Date);
      expect(_items[0].end).toBeInstanceOf(Date);
    });
  });

  describe("Complex Real-world Scenario", () => {
    it("should handle a complex workflow with multiple FORK_JOIN_DYNAMIC tasks and iterations", () => {
      const tasks = [
        // Main fork task
        createMockTask({
          taskId: "main-fork",
          referenceTaskName: "main_fork_ref",
          workflowTask: {
            name: "Main Fork",
            taskReferenceName: "main_fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["sub_task1", "sub_task2"],
          },
          iteration: 1,
        }),
        // Sub fork task
        createMockTask({
          taskId: "sub-fork",
          referenceTaskName: "sub_fork_ref",
          workflowTask: {
            name: "Sub Fork",
            taskReferenceName: "sub_fork_ref",
            type: "FORK_JOIN_DYNAMIC",
          },
          inputData: {
            forkedTasks: ["nested_task1", "nested_task2"],
          },
          iteration: 2,
        }),
        // Tasks with iteration suffixes
        createMockTask({
          taskId: "sub-task1-1",
          referenceTaskName: "sub_task1__1",
          workflowTask: {
            name: "Sub Task 1",
            taskReferenceName: "sub_task1",
            type: "SIMPLE",
          },
          iteration: 1,
        }),
        createMockTask({
          taskId: "sub-task2-1",
          referenceTaskName: "sub_task2__1",
          workflowTask: {
            name: "Sub Task 2",
            taskReferenceName: "sub_task2",
            type: "SIMPLE",
          },
          iteration: 1,
        }),
        createMockTask({
          taskId: "nested-task1-2",
          referenceTaskName: "nested_task1__2",
          workflowTask: {
            name: "Nested Task 1",
            taskReferenceName: "nested_task1",
            type: "SIMPLE",
          },
          iteration: 2,
        }),
        createMockTask({
          taskId: "nested-task2-2",
          referenceTaskName: "nested_task2__2",
          workflowTask: {
            name: "Nested Task 2",
            taskReferenceName: "nested_task2",
            type: "SIMPLE",
          },
          iteration: 2,
        }),
      ];

      const executionStatusMap = createMockExecutionStatusMap({
        main_fork_ref: { related: "some_parent" },
        sub_fork_ref: { related: "main_fork_ref" },
      });

      const [groups, _items] = processTasksToGroupsAndItems(
        tasks as unknown as ExecutionTask[],
        executionStatusMap,
      );

      expect(groups).toHaveLength(6);
      expect(_items).toHaveLength(6);

      // Test main fork nested groups
      const mainForkGroup = groups.find((g) => g.id === "main_fork_ref");
      expect(mainForkGroup?.nestedGroups).toEqual([
        "sub_task1__1",
        "sub_task2__1",
      ]);
      expect(mainForkGroup?.treeLevel).toBe(2);

      // Test sub fork nested groups
      const subForkGroup = groups.find((g) => g.id === "sub_fork_ref");
      expect(subForkGroup?.nestedGroups).toEqual([
        "nested_task1__2",
        "nested_task2__2",
      ]);
      expect(subForkGroup?.treeLevel).toBe(2);
    });
  });
});
