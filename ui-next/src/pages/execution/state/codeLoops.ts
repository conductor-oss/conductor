import { TaskStatus, TaskType } from "types";

/**
 * A native workflow's Java loop is ordinary code: each call it makes is its own step, flat in the
 * execution's definition, listing the loops it ran in (`metadata.loops`, outermost first). These
 * helpers show such a loop the way a DO_WHILE is shown: one loop node whose body has each step of
 * the code once, and whose runs are its iterations. Nothing here changes the execution on the
 * server; the steps keep their reference names.
 *
 * An iteration is a pass through the loop's code. A pass begins again when a step comes back to a
 * place in the code (its call site) that the current pass has already been through.
 */
const loopsOf = (task: any): string[] => task?.metadata?.loops ?? [];

type Group = { group: true; id: string; passes: any[][] };
type Item = any;

const isGroup = (item: Item): item is Group => item?.group === true;

/** Where a step is in the code: its call site, or its name without the number a repeat got. */
/** Entries named after their reference, which a repeat numbers and a preview changes. */
const NAMED_BY_REFERENCE = new Set<string>([
  TaskType.SWITCH,
  TaskType.FORK_JOIN,
  TaskType.JOIN,
  TaskType.DO_WHILE,
  TaskType.TERMINATE,
]);

const keyOf = (item: Item): string => {
  if (isGroup(item)) return `loop:${item.id}`;
  // a switch or fork is named after its reference (if_x_2, if_x_if_false): its call site and
  // type are the place; a task is also known by its name
  const name = NAMED_BY_REFERENCE.has(item.type) ? "" : item.name;
  return `${item.metadata?.site ?? withoutRepeat(item.taskReferenceName)}|${item.type}|${name}`;
};

const withoutRepeat = (ref: unknown): string =>
  String(ref).replace(/_\d+$/, "");

/** Consecutive steps of one loop become a group of passes; what a step contains is grouped too. */
const groupList = (list: any[] = [], depth: number): Item[] => {
  const out: Item[] = [];
  let current: { id: string; members: any[] } | undefined;
  const close = () => {
    if (current) {
      out.push({
        group: true,
        id: current.id,
        passes: splitPasses(groupList(current.members, depth + 1)),
      });
      current = undefined;
    }
  };
  for (const task of list) {
    const id = loopsOf(task)[depth];
    if (id === undefined) {
      close();
      out.push(withGroupedChildren(task, depth));
    } else if (current?.id === id) {
      current.members.push(task);
    } else {
      close();
      current = { id, members: [task] };
    }
  }
  close();
  return out;
};

const mapChildren = (task: any, f: (list: any[]) => any[]) => {
  const copy = { ...task };
  if (task.forkTasks) copy.forkTasks = task.forkTasks.map(f);
  if (task.decisionCases) {
    copy.decisionCases = Object.fromEntries(
      Object.entries(task.decisionCases).map(([k, v]) => [k, f(v as any[])]),
    );
  }
  if (task.defaultCase) copy.defaultCase = f(task.defaultCase);
  if (task.loopOver) copy.loopOver = f(task.loopOver);
  return copy;
};

const withGroupedChildren = (task: any, depth: number) =>
  mapChildren(task, (l) => groupList(l, depth));

const splitPasses = (items: Item[]): Item[][] => {
  const passes: Item[][] = [];
  let pass: Item[] = [];
  let seen = new Set<string>();
  for (const item of items) {
    const key = keyOf(item);
    if (seen.has(key)) {
      passes.push(pass);
      pass = [];
      seen = new Set();
    }
    pass.push(item);
    seen.add(key);
  }
  if (pass.length > 0) passes.push(pass);
  return passes;
};

type LoopRun = { ref: string; def: any; outer?: number; passes: Item[][] };
type Context = {
  seen: Map<string, number>;
  /** A step's reference name to the body entry it is a run of, and the iteration it ran in. */
  runs: Map<string, { body: string; iteration: number }>;
  bodies: Map<string, any>;
  loops: LoopRun[];
};

/** The definition entries of groups outside any loop. */
const materialize = (items: Item[], ctx: Context): any[] =>
  items.map((item) =>
    isGroup(item)
      ? loopNode([{ group: item }], ctx)
      : mapChildren(item, (l) => materialize(l, ctx)),
  );

/** One loop node for every run of a loop at this place, outer iteration by outer iteration. */
const loopNode = (
  runs: { outer?: number; group: Group }[],
  ctx: Context,
): any => {
  const id = runs[0].group.id;
  // the same loop can run again later, and each drawing needs its own reference
  const times = (ctx.seen.get(id) ?? 0) + 1;
  ctx.seen.set(id, times);
  const ref = times === 1 ? id : `${id}_${times}`;
  const def: any = {
    name: ref,
    taskReferenceName: ref,
    type: TaskType.DO_WHILE,
    inputParameters: {},
    // the code decides when it repeats, as it decides its switches
    evaluatorType: "native-code",
    loopCondition: "",
    metadata: { codeLoop: true },
  };
  runs.forEach((r) =>
    ctx.loops.push({ ref, def, outer: r.outer, passes: r.group.passes }),
  );
  def.loopOver = mergeLists(
    runs.flatMap((r) =>
      r.group.passes.map((pass, i) => ({ iteration: i + 1, list: pass })),
    ),
    ctx,
  );
  return def;
};

/** The body of a loop: each place in the code once, in the order the passes first reached it. */
const mergeLists = (
  lists: { iteration: number; list: Item[] }[],
  ctx: Context,
): any[] => {
  const order: string[] = [];
  const byKey = new Map<string, { iteration: number; item: Item }[]>();
  for (const { iteration, list } of lists) {
    for (const item of list) {
      const key = keyOf(item);
      if (!byKey.has(key)) {
        byKey.set(key, []);
        order.push(key);
      }
      byKey.get(key)!.push({ iteration, item });
    }
  }
  return order.map((key) => mergeOne(byKey.get(key)!, ctx));
};

const mergeOne = (
  runs: { iteration: number; item: Item }[],
  ctx: Context,
): any => {
  if (isGroup(runs[0].item)) {
    return loopNode(
      runs.map((r) => ({ outer: r.iteration, group: r.item })),
      ctx,
    );
  }
  // a step that ran stands for the place, rather than a preview of it
  const rep = (runs.find((r) => !r.item.metadata?.preview) ?? runs[0]).item;
  runs.forEach((r) =>
    ctx.runs.set(r.item.taskReferenceName, {
      body: rep.taskReferenceName,
      iteration: r.iteration,
    }),
  );
  const of = (pick: (item: any) => any[] | undefined) =>
    mergeLists(
      runs.map((r) => ({ iteration: r.iteration, list: pick(r.item) ?? [] })),
      ctx,
    );
  const def = { ...rep };
  if (rep.decisionCases) {
    const cases = new Set(
      runs.flatMap((r) => Object.keys(r.item.decisionCases ?? {})),
    );
    def.decisionCases = Object.fromEntries(
      [...cases].map((c) => [c, of((item) => item.decisionCases?.[c])]),
    );
  }
  if (rep.defaultCase) def.defaultCase = of((item) => item.defaultCase);
  if (rep.forkTasks) {
    def.forkTasks = rep.forkTasks.map((_: any, i: number) =>
      of((item) => item.forkTasks?.[i]),
    );
  }
  if (rep.loopOver) def.loopOver = of((item) => item.loopOver);
  ctx.bodies.set(def.taskReferenceName, def);
  return def;
};

/** Every reference name a pass reached, inside its switches, forks and loops too. */
const refsIn = (items: Item[]): string[] =>
  items.flatMap((item) =>
    isGroup(item)
      ? item.passes.flatMap(refsIn)
      : [
          item.taskReferenceName,
          ...(item.forkTasks ?? []).flatMap(refsIn),
          ...Object.values(item.decisionCases ?? {}).flatMap((l: any) =>
            refsIn(l),
          ),
          ...refsIn(item.defaultCase ?? []),
          ...refsIn(item.loopOver ?? []),
        ],
  );

const statusOf = (statuses: (TaskStatus | undefined)[]): TaskStatus => {
  if (statuses.every((s) => s === undefined || s === TaskStatus.PENDING)) {
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

const CODE_LOOPS_SHOWN = "codeLoopsShown";

/** Whether an entry, or one it holds, ran in a Java loop. */
const tagged = (t: any): boolean =>
  loopsOf(t).length > 0 ||
  (t.forkTasks ?? []).some((b: any[]) => b.some(tagged)) ||
  Object.values(t.decisionCases ?? {}).some((l: any) => l.some(tagged)) ||
  (t.defaultCase ?? []).some(tagged) ||
  (t.loopOver ?? []).some(tagged);

const newContext = (): Context => ({
  seen: new Map(),
  runs: new Map(),
  bodies: new Map(),
  loops: [],
});

/**
 * The definition as the editor draws it: a native workflow's with its Java loops as loop nodes,
 * any other as it is. A selected node is looked up in this one, the one it was drawn from.
 */
export const definitionAsDrawn = <W extends { tasks?: any[]; metadata?: any }>(
  workflow: W,
): W =>
  workflow?.metadata?.native != null
    ? codeLoopsInDefinition(workflow)
    : workflow;

/**
 * A native workflow's registered definition with its Java loops shown as loops: one loop node
 * per loop, over the steps it holds. For drawing only: the definition itself is unchanged.
 */
export const codeLoopsInDefinition = <W extends { tasks?: any[] }>(
  workflow: W,
): W => {
  const tasks: any[] = workflow?.tasks ?? [];
  if (!tasks.some(tagged)) return workflow;
  return { ...workflow, tasks: materialize(groupList(tasks, 0), newContext()) };
};

/**
 * The execution with its Java loops shown as loops: the definition gets one loop node per loop,
 * each step that ran becomes an iteration of its body entry, and each loop gets a DO_WHILE task
 * whose output lists, per iteration, what its steps returned.
 */
export const codeLoopsAsIterations = <
  E extends { tasks?: any[]; workflowDefinition?: any },
>(
  execution: E,
): E => {
  if (!execution || (execution as any)[CODE_LOOPS_SHOWN]) return execution;
  const definition = execution.workflowDefinition;
  const defined: any[] = definition?.tasks ?? [];
  if (!defined.some(tagged)) {
    return execution;
  }
  const ctx = newContext();
  const tasksDef = materialize(groupList(defined, 0), ctx);

  const tasks: any[] = execution.tasks ?? [];
  const runsOf = new Map<string, number[]>();
  tasks.forEach((t, i) => {
    const ref = t.workflowTask?.taskReferenceName ?? t.referenceTaskName;
    runsOf.set(ref, [...(runsOf.get(ref) ?? []), i]);
  });

  const before = new Map<number, any[]>();
  for (const loop of ctx.loops) {
    const output: Record<string, any> = {};
    const members: number[] = [];
    loop.passes.forEach((pass, i) => {
      const reached: Record<string, any> = {};
      for (const ref of refsIn(pass)) {
        const indexes = runsOf.get(ref) ?? [];
        if (indexes.length === 0) continue;
        members.push(...indexes);
        reached[ctx.runs.get(ref)?.body ?? ref] =
          tasks[indexes[indexes.length - 1]].outputData ?? {};
      }
      if (Object.keys(reached).length > 0) output[i + 1] = reached;
    });
    if (members.length === 0) continue;
    const first = Math.min(...members);
    const memberTasks = members.map((i) => tasks[i]);
    const starts = memberTasks.map((t) => t.startTime).filter((x) => x > 0);
    // a step retried after a failure is as its latest attempt
    const latest = new Map<string, any>();
    memberTasks.forEach((t) =>
      latest.set(t.workflowTask?.taskReferenceName ?? t.referenceTaskName, t),
    );
    const status = statusOf([...latest.values()].map((t) => t.status));
    const ends = memberTasks.map((t) => t.endTime ?? 0);
    const ref = loop.outer != null ? `${loop.ref}__${loop.outer}` : loop.ref;
    const synthetic = {
      taskId: `code-loop:${ref}`,
      taskType: TaskType.DO_WHILE,
      taskDefName: loop.ref,
      referenceTaskName: ref,
      status,
      iteration: loop.outer ?? loop.passes.length,
      loopOverTask: loop.outer != null,
      inputData: {},
      outputData: { iteration: loop.passes.length, ...output },
      workflowTask: loop.def,
      seq: tasks[first].seq,
      scheduledTime: tasks[first].scheduledTime,
      startTime: starts.length > 0 ? Math.min(...starts) : 0,
      endTime:
        status === TaskStatus.IN_PROGRESS || status === TaskStatus.PENDING
          ? 0
          : Math.max(...ends),
    };
    before.set(first, [...(before.get(first) ?? []), synthetic]);
  }

  const rewritten: any[] = [];
  tasks.forEach((t, i) => {
    rewritten.push(...(before.get(i) ?? []));
    const run = ctx.runs.get(
      t.workflowTask?.taskReferenceName ?? t.referenceTaskName,
    );
    rewritten.push(
      run && ctx.bodies.has(run.body)
        ? {
            ...t,
            workflowTask: ctx.bodies.get(run.body),
            iteration: run.iteration,
            loopOverTask: true,
          }
        : t,
    );
  });

  return {
    ...execution,
    [CODE_LOOPS_SHOWN]: true,
    workflowDefinition: { ...definition, tasks: tasksDef },
    tasks: rewritten,
  };
};

export const isCodeLoop = (task: any): boolean =>
  task?.metadata?.codeLoop === true;

/** A code loop has no task of its own: its status is that of its steps. */
export const codeLoopStatus = (steps: any[] = []): TaskStatus =>
  statusOf(steps.map((s) => s.executionData?.status ?? TaskStatus.PENDING));
