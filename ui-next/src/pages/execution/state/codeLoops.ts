import { TaskStatus, TaskType } from "types";

/**
 * A native workflow's Java loop is ordinary code: its steps are flat in the execution's definition,
 * each listing the loops it ran in (`metadata.loops`, outermost first). These helpers draw
 * consecutive steps of one loop as a single loop node, so a switch, fork or sub workflow inside a
 * loop shows inside it. Nothing here changes the execution.
 */
const loopsOf = (task: any): string[] => task?.metadata?.loops ?? [];

type Seen = Map<string, number>;

const withChildren = (task: any, depth: number, seen: Seen) => {
  const copy = { ...task };
  if (task.forkTasks) {
    copy.forkTasks = task.forkTasks.map((b: any[]) => groupAt(b, depth, seen));
  }
  if (task.decisionCases) {
    copy.decisionCases = Object.fromEntries(
      Object.entries(task.decisionCases).map(([k, v]) => [
        k,
        groupAt(v as any[], depth, seen),
      ]),
    );
  }
  if (task.defaultCase) {
    copy.defaultCase = groupAt(task.defaultCase, depth, seen);
  }
  if (task.loopOver) {
    copy.loopOver = groupAt(task.loopOver, depth, seen);
  }
  return copy;
};

function groupAt(tasks: any[], depth: number, seen: Seen): any[] {
  const out: any[] = [];
  let current: { id: string; members: any[] } | undefined;
  const close = () => {
    if (current) {
      // the same loop can run again later, and each drawing needs its own reference
      const times = (seen.get(current.id) ?? 0) + 1;
      seen.set(current.id, times);
      const ref = times === 1 ? current.id : `${current.id}_${times}`;
      out.push({
        name: ref,
        taskReferenceName: ref,
        type: TaskType.DO_WHILE,
        inputParameters: {},
        loopCondition: "",
        loopOver: groupAt(current.members, depth + 1, seen),
        metadata: { codeLoop: true },
      });
      current = undefined;
    }
  };
  for (const task of tasks) {
    const id = loopsOf(task)[depth];
    if (id === undefined) {
      close();
      out.push(withChildren(task, depth, seen));
    } else if (current?.id === id) {
      current.members.push(task);
    } else {
      close();
      current = { id, members: [task] };
    }
  }
  close();
  return out;
}

export const groupCodeLoops = <T>(tasks: T[] = []): T[] =>
  groupAt(tasks as any[], 0, new Map()) as T[];

export const isCodeLoop = (task: any): boolean =>
  task?.metadata?.codeLoop === true;

/** A code loop has no task of its own: its status is that of its steps. */
export const codeLoopStatus = (steps: any[] = []): TaskStatus => {
  const statuses = steps.map((s) => s.executionData?.status);
  if (statuses.every((s) => s === TaskStatus.PENDING)) {
    return TaskStatus.PENDING;
  }
  if (
    statuses.some(
      (s) => s === TaskStatus.IN_PROGRESS || s === TaskStatus.SCHEDULED,
    )
  ) {
    return TaskStatus.IN_PROGRESS;
  }
  for (const failed of [
    TaskStatus.FAILED,
    TaskStatus.TIMED_OUT,
    TaskStatus.CANCELED,
  ]) {
    if (statuses.includes(failed)) {
      return failed;
    }
  }
  return TaskStatus.COMPLETED;
};
