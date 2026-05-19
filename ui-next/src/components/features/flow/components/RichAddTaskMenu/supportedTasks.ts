/**
 * Supported Tasks Configuration
 *
 * This module defines the task types available in the "Add Task" menu.
 * Core OSS tasks are defined here, while enterprise tasks are registered
 * via the plugin system.
 */

import { pluginRegistry } from "plugins/registry";
import { TaskType } from "types";
import { BaseTaskMenuItem, RichAddMenuTabs } from "./state/types";

/**
 * Core OSS System Tasks
 * These are fundamental system tasks available in open source Conductor.
 */
export const SYSTEM_TASKS: BaseTaskMenuItem[] = [
  {
    name: "Event Task",
    description:
      "Publish an event to a messaging system (Kafka, AMQP, SQS, NATS, MQ).",
    type: TaskType.EVENT,
    category: "System",
  },
  {
    name: "HTTP Task",
    type: TaskType.HTTP,
    description: "Call an API / Microservice.",
    category: "System",
  },
  {
    name: "HTTP Poll Task",
    description:
      "Poll a remote endpoint periodically until a condition is met. Useful for long running jobs.",
    type: TaskType.HTTP_POLL,
    category: "System",
  },
  {
    name: "gRPC Task",
    description: "Call a gRPC service method.",
    type: TaskType.GRPC,
    category: "System",
  },
  {
    name: "Inline Task",
    description:
      "Run lightweight javascript code. Useful for data transformation.",
    type: TaskType.INLINE,
    category: "System",
  },
  {
    name: "JSON JQ Transform",
    description: "Use the power of JQ to transform JSON.",
    type: TaskType.JSON_JQ_TRANSFORM,
    category: "System",
  },
  {
    name: "Business Rule Task",
    description: "Evaluate business rules using Drools.",
    type: TaskType.BUSINESS_RULE,
    category: "System",
  },
  {
    name: "SQL Query",
    description: "Run SQL query against a database.",
    type: TaskType.JDBC,
    category: "System",
  },
  {
    name: "Get Signed JWT Task",
    description: "Get signed JWT task.",
    type: TaskType.GET_SIGNED_JWT,
    category: "System",
  },
  {
    name: "Update Task",
    description: "Update existing task with new status and properties.",
    type: TaskType.UPDATE_TASK,
    category: "System",
  },
  {
    name: "Query Processor",
    description: "Query from different data sources.",
    type: TaskType.QUERY_PROCESSOR,
    category: "System",
  },
];

/**
 * Core OSS Operator Tasks
 * These are control flow operators available in open source Conductor.
 */
export const OPERATOR_TASKS: BaseTaskMenuItem[] = [
  {
    name: "Switch",
    description: "if..then...else.",
    type: TaskType.SWITCH,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Do While",
    description: "Loop.",
    type: TaskType.DO_WHILE,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Wait",
    description:
      "Add timer in your workflow. Wait for specific duration, time or a signal.",
    type: TaskType.WAIT,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Dynamic Task",
    description: "Execute a task dynamically.",
    type: TaskType.DYNAMIC,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Set Variable",
    description: "Set a variable.",
    type: TaskType.SET_VARIABLE,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Sub Workflow",
    description: "Execute a sub workflow.",
    type: TaskType.SUB_WORKFLOW,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Terminate Workflow",
    description: "Terminate another workflow.",
    type: TaskType.TERMINATE_WORKFLOW,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Terminate",
    description: "Terminate the workflow.",
    type: TaskType.TERMINATE,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Fork Join",
    description: "Run multiple tasks in parallel.",
    type: TaskType.FORK_JOIN,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Dynamic Fork",
    description: "Spawn multiple tasks dynamically.",
    type: TaskType.FORK_JOIN_DYNAMIC,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Start Workflow Task",
    description: "Start Workflow starts another workflow.",
    type: TaskType.START_WORKFLOW,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Get Workflow",
    description: "Get workflow details",
    type: TaskType.GET_WORKFLOW,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
  {
    name: "Yield",
    description: "Yield task",
    type: TaskType.YIELD,
    category: RichAddMenuTabs.OPERATORS_TAB,
  },
];

/**
 * Core OSS Worker Tasks
 */
export const WORKER_TASKS: BaseTaskMenuItem[] = [
  {
    name: "Worker Task (Simple)",
    description: "Runs a Worker task.",
    type: TaskType.SIMPLE,
    category: RichAddMenuTabs.WORKERS_TAB,
  },
];

/**
 * Get all plugin-registered task menu items
 */
const getPluginTaskMenuItems = (): BaseTaskMenuItem[] => {
  const pluginItems = pluginRegistry.getTaskMenuItems();
  // Convert plugin items to BaseTaskMenuItem format
  return pluginItems.map((item) => ({
    name: item.name,
    description: item.description,
    type: item.type as any, // FormTaskType
    category: item.category,
    version: item.version,
    flagHidden: item.hidden,
  }));
};

/**
 * AI/LLM Tasks for Agentic Orchestration
 * These are AI-powered tasks for building intelligent workflows.
 */
export const AI_TASKS: BaseTaskMenuItem[] = [
  {
    name: "LLM Chat Complete",
    description: "Generate text using a large language model chat interface.",
    type: TaskType.LLM_CHAT_COMPLETE,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
  {
    name: "LLM Text Complete",
    description: "Generate text using a large language model completion API.",
    type: TaskType.LLM_TEXT_COMPLETE,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
  {
    name: "LLM Generate Embeddings",
    description: "Generate vector embeddings from text.",
    type: TaskType.LLM_GENERATE_EMBEDDINGS,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
  {
    name: "LLM Get Embeddings",
    description: "Retrieve stored embeddings by ID or query.",
    type: TaskType.LLM_GET_EMBEDDINGS,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
  {
    name: "LLM Index Document",
    description: "Index a document into a vector database for semantic search.",
    type: TaskType.LLM_INDEX_DOCUMENT,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
  {
    name: "LLM Search Index",
    description: "Search indexed documents using semantic similarity.",
    type: TaskType.LLM_SEARCH_INDEX,
    category: RichAddMenuTabs.AI_AGENTS_TAB,
  },
];

/**
 * @deprecated Use AI_TASKS instead
 */
export const LLM_TASKS: BaseTaskMenuItem[] = AI_TASKS;

const [simpleTask, ...remainingWorkerTasks] = WORKER_TASKS;

/**
 * Returns all available tasks including plugin-registered tasks.
 * Called at runtime so plugin items (e.g. Wait For Webhook Task) are included when the menu opens.
 */
export const getALL_TASKS = (): BaseTaskMenuItem[] => [
  simpleTask,
  ...SYSTEM_TASKS,
  ...OPERATOR_TASKS,
  ...AI_TASKS,
  ...getPluginTaskMenuItems(),
  ...remainingWorkerTasks,
];

/**
 * @deprecated Use getALL_TASKS() so plugin items are included (ALL_TASKS is computed at module load and may miss plugins).
 */
export const ALL_TASKS: BaseTaskMenuItem[] = getALL_TASKS();
