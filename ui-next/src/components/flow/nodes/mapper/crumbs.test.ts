import {
  crumbsToTask,
  isTaskReferenceNestedInTaskReference,
  isTaskNext,
  previousTaskCrumb,
  isSubWorkflowChild,
} from "./crumbs";
import {
  simpleDiagram,
  populationMinMax,
  loanBanking,
  simpleLoopSample,
  nestedForkJoin,
} from "../../../../testData/diagramTests";
import { TaskDef, Crumb, TaskType } from "types";

describe("crumbsToTask", () => {
  it("Should return undefined if crumbs or task is empty", () => {
    const result1 = crumbsToTask([], []);
    expect(result1).toBeUndefined();

    const taskReferenceName = "image_convert_resize_ref";
    const result2 = crumbsToTask(
      [],
      simpleDiagram.tasks as unknown as TaskDef[],
    );
    expect(result2).toBeUndefined();

    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: taskReferenceName,
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];

    const result3 = crumbsToTask(crumbs, []);

    expect(result3).toBeUndefined();
  });
  it("Should return the task in a linear workflow", () => {
    const taskReferenceName = "image_convert_resize_ref";
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: taskReferenceName,
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      simpleDiagram.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
  it("Should return the task if task is within fork", () => {
    const taskReferenceName = "process_population_max_ref";
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "get_population_data_ref",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "fork_ref",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_ref",
        ref: "process_population_max_ref",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      populationMinMax.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
  it("Should work if task is within a switch path", () => {
    const taskReferenceName = "employment_details_verification";
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "customer_details",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "loan_type",
        refIdx: 1,
        type: TaskType.SIMPLE,
      },
      {
        parent: "loan_type",
        decisionBranch: "property",
        ref: "employment_details",
        refIdx: 0,
        type: TaskType.SWITCH,
      },
      {
        parent: "loan_type",
        decisionBranch: "property",
        ref: "employment_details_verification",
        refIdx: 1,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      loanBanking.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
  it("Should work if task is within a switch defaultPath", () => {
    const taskReferenceName = "business_details";
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "customer_details",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "loan_type",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "loan_type",
        decisionBranch: "defaultCase",
        ref: "business_details",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      loanBanking.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });

  it("Should work if task is within a switch within a switch", () => {
    const taskReferenceName = "loan_transfer_to_customer_account";
    const crumbs: Crumb[] = [
      {
        ref: "customer_details",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        ref: "loan_type",
        refIdx: 1,
        type: TaskType.SIMPLE,
      },
      {
        ref: "credit_score_risk",
        refIdx: 2,
        type: TaskType.SIMPLE,
      },
      {
        ref: "credit_result",
        refIdx: 3,
        type: TaskType.SWITCH,
      },
      {
        parent: "credit_result",
        decisionBranch: "possible",
        ref: "principal_interest_calculation",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: "credit_result",
        decisionBranch: "possible",
        ref: "customer_decision",
        refIdx: 1,
        type: TaskType.SIMPLE,
      },
      {
        parent: "customer_decision",
        decisionBranch: "yes",
        ref: "loan_transfer_to_customer_account",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      loanBanking.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
  it("Should work if task is within a WHILE", () => {
    const taskReferenceName = "loop_2_task_iter";
    const crumbs: Crumb[] = [
      {
        ref: "__start",
        refIdx: 0,
        type: TaskType.TERMINAL,
      },
      {
        ref: "my_fork_join_ref",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "my_fork_join_ref",
        forkIndex: 1,
        ref: "loop_2",
        refIdx: 0,
        type: TaskType.DO_WHILE,
      },
      {
        parent: "loop_2",
        ref: "loop_2_task_iter",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      simpleLoopSample.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
  it("Should work for nested fork join within a switch", () => {
    const taskReferenceName = "sample_task_name_join_uqholl_ref";
    const crumbs: Crumb[] = [
      {
        ref: "get_random_fact",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_fork_ytrlak_ref",
        refIdx: 1,

        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_join_fd9v1_ref",
        refIdx: 2,
        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_http_mvwvv_ref",
        refIdx: 3,
        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_join_a75or_ref",
        refIdx: 4,

        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_fork_6vg5rj_ref",
        refIdx: 5,

        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_join_6fc3tf_ref",
        refIdx: 6,

        type: TaskType.SIMPLE,
      },
      {
        ref: "sample_task_name_switch_pm7wsj_ref",
        refIdx: 7,
        type: TaskType.SWITCH,
      },
      {
        parent: "sample_task_name_switch_pm7wsj_ref",
        decisionBranch: "new_case_ms0jy",
        ref: "sample_task_name_simple_0xdkv_ref",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: "sample_task_name_switch_pm7wsj_ref",
        decisionBranch: "new_case_ms0jy",
        ref: "sample_task_name_fork_lx82h_ref",
        refIdx: 1,
        type: TaskType.SIMPLE,
      },
      {
        parent: "sample_task_name_switch_pm7wsj_ref",
        decisionBranch: "new_case_ms0jy",
        ref: taskReferenceName,
        refIdx: 2,
        type: TaskType.SIMPLE,
      },
    ];
    const result = crumbsToTask(
      crumbs,
      nestedForkJoin.tasks as unknown as TaskDef[],
    );
    expect(result!.taskReferenceName).toEqual(taskReferenceName);
  });
});

describe("isTaskReferenceNestedInTaskReference", () => {
  it("Should return true if task is nested in a switch", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "get_random_fact",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "switch_task_l1bk1_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_l1bk1_ref",
        decisionBranch: "new_case_cxt61",
        ref: "nested_http_ref",
        type: TaskType.SIMPLE,
        refIdx: 0,
      },
    ];
    const nestedTaskReferenceName = "nested_http_ref";
    const maybeParent = "switch_task_l1bk1_ref";
    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(true);
  });

  it("Should return true if task is nested in a fork", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "get_random_fact",
        type: TaskType.SIMPLE,
        refIdx: 0,
      },
      {
        parent: undefined,
        ref: "fork_task_uglok_ref",
        type: TaskType.FORK_JOIN,
        refIdx: 1,
      },
      {
        parent: "fork_task_uglok_ref",
        forkIndex: 0,
        ref: "nested_event_ref",
        refIdx: 0,
        type: TaskType.EVENT,
      },
    ];

    const nestedTaskReferenceName = "nested_event_ref";
    const maybeParent = "fork_task_uglok_ref";

    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(true);
  });

  it("Should return true if task is nested in a doWhile", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "get_random_fact",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "http_poll_task_qikye_ref",
        refIdx: 1,
        type: TaskType.HTTP,
      },
      {
        parent: undefined,
        ref: "do_while_task_iv18s_ref",
        refIdx: 2,
        type: TaskType.DO_WHILE,
      },
      {
        parent: "do_while_task_iv18s_ref",
        ref: "nested_http_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
    ];

    const nestedTaskReferenceName = "nested_http_ref";
    const maybeParent = "do_while_task_iv18s_ref";

    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(true);
  });

  it("Should return true if the task is nested within a nesgted parent for example a switch within a fork", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "get_random_fact",
        refIdx: 0,
        type: TaskType.SIMPLE,
      },
      {
        parent: undefined,
        ref: "http_poll_task_qikye_ref",
        refIdx: 1,
        type: TaskType.HTTP,
      },
      {
        parent: undefined,
        ref: "fork_task_9qlfc_ref",
        refIdx: 2,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_9qlfc_ref",
        forkIndex: 0,
        ref: "switch_task_l2pcc_ref",
        refIdx: 0,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_l2pcc_ref",
        decisionBranch: "new_case_e6vpy",
        ref: "do_while_task_rth2u_ref",
        refIdx: 0,
        type: TaskType.DO_WHILE,
      },
      {
        parent: "do_while_task_rth2u_ref",
        ref: "event_task_n2zld_ref",
        refIdx: 0,
        type: TaskType.EVENT,
      },
      {
        parent: "do_while_task_rth2u_ref",
        ref: "double_nested_event_ref",
        refIdx: 1,
        type: TaskType.DO_WHILE,
      },
    ];

    const nestedTaskReferenceName = "double_nested_event_ref";
    const maybeParent = "fork_task_9qlfc_ref";

    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(true);
  });
  it("Should return false if maybeParent is not the parent of nestedTaskReferenceName", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: undefined,
        ref: "human_task_ref",
        refIdx: 1,
        type: TaskType.HUMAN,
      },
      {
        parent: undefined,
        ref: "fork_task_ref",
        refIdx: 2,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref",
        forkIndex: 0,
        ref: "event_task_ref",
        refIdx: 0,
        type: TaskType.EVENT,
      },
      {
        parent: "fork_task_ref",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "defaultCase",
        ref: "http_poll_task_ref_1",
        refIdx: 0,
        type: TaskType.HTTP,
      },
    ];

    const nestedTaskReferenceName = "http_poll_task_ref_1";
    const maybeParent = "kafka_publish_task_ref";

    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(false);
  });
  it("Should return true. if nestedTaskReferenceName is nested in a task that is nested in maybeParent", () => {
    const testCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "http_task_ref_3",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "fork_task_ref_2",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_2",
        forkIndex: 1,
        ref: "http_task_ref_4",
        refIdx: 0,
        type: TaskType.HTTP,
      },
    ];

    const nestedTaskReferenceName = "http_task_ref_4";
    const maybeParent = "switch_task_ref";

    expect(
      isTaskReferenceNestedInTaskReference(
        testCrumbs,
        nestedTaskReferenceName,
        maybeParent,
      ),
    ).toEqual(true);
  });
});
describe("previousTaskCrumb", () => {
  it("Should return the previous task crumb", () => {
    const simpleForkJoinCrumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: undefined,
        ref: "join_task_ref_1",
        refIdx: 1,
        type: TaskType.JOIN,
      },
    ];
    const crumb = previousTaskCrumb(simpleForkJoinCrumbs, "join_task_ref_1");
    expect(crumb).toEqual(simpleForkJoinCrumbs[0]);
  });
  it("should return previous task crumb in nested tree", () => {
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "http_task_ref_3",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "fork_task_ref_2",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "join_task_ref_2",
        refIdx: 2,
        type: TaskType.JOIN,
      },
    ];

    const crumb = previousTaskCrumb(crumbs, "join_task_ref_2");
    expect(crumb).toEqual({
      parent: "switch_task_ref",
      decisionBranch: "new_case_ilm6lf",
      ref: "fork_task_ref_2",
      refIdx: 1,
      type: TaskType.FORK_JOIN,
    });
  });
  it("should return undefined if task is the first task  in tree", () => {
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "http_task_ref_3",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "fork_task_ref_2",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "join_task_ref_2",
        refIdx: 2,
        type: TaskType.JOIN,
      },
    ];

    const crumb = previousTaskCrumb(crumbs, "http_task_ref");
    expect(crumb).toBeUndefined();
  });
});

describe("isTaskNext", () => {
  it("Should return true if the the second task is next in the tree", () => {
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "http_task_ref_3",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "fork_task_ref_2",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "join_task_ref_2",
        refIdx: 2,
        type: TaskType.JOIN,
      },
    ];
    expect(isTaskNext(crumbs, "http_task_ref", "switch_task_ref")).toBeTruthy();
  });
  it("Should return false if the second task is not next in the tree", () => {
    const crumbs: Crumb[] = [
      {
        parent: undefined,
        ref: "fork_task_ref_1",
        refIdx: 0,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "http_task_ref",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "fork_task_ref_1",
        forkIndex: 0,
        ref: "switch_task_ref",
        refIdx: 1,
        type: TaskType.SWITCH,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "http_task_ref_3",
        refIdx: 0,
        type: TaskType.HTTP,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "fork_task_ref_2",
        refIdx: 1,
        type: TaskType.FORK_JOIN,
      },
      {
        parent: "switch_task_ref",
        decisionBranch: "new_case_ilm6lf",
        ref: "join_task_ref_2",
        refIdx: 2,
        type: TaskType.JOIN,
      },
    ];
    expect(isTaskNext(crumbs, "switch_task_ref", "http_task_ref")).toBeFalsy();
  });
});

describe("disable drag for subworkflow child nodes", () => {
  const crumbs: any = [
    {
      parent: null,
      ref: "http_task_ref",
      refIdx: 0,
      type: "HTTP",
    },
    {
      parent: null,
      ref: "sub_workflow_task_ref",
      refIdx: 1,
      type: "SUB_WORKFLOW",
    },
    {
      parent: "sub_workflow_task_ref",
      ref: "do_while_task_ref",
      refIdx: 0,
      type: "DO_WHILE",
    },
    {
      parent: "do_while_task_ref",
      ref: "some_task_ref",
      refIdx: 0,
      type: "DO_WHILE",
    },
    {
      parent: "some_task_ref",
      ref: "event_task_ref_2",
      refIdx: 0,
      type: "EVENT",
    },
    {
      parent: "sub_workflow_task_ref",
      ref: "get_random_fact",
      refIdx: 0,
      type: "HTTP",
    },
    {
      parent: "sub_workflow_task_ref",
      ref: "http_task_qlcyu_ref",
      refIdx: 1,
      type: "HTTP",
    },
  ];

  it("If a task is direct child to subworkflow", () => {
    const result = isSubWorkflowChild(crumbs, "http_task_qlcyu_ref");
    expect(result).toBe(true);
  });
  it("If a task is nested child to subworkflow", () => {
    const result = isSubWorkflowChild(crumbs, "event_task_ref_2");
    expect(result).toBe(true);
  });
});
