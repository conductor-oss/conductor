/**
 * Supported Tasks Configuration
 *
 * This module defines the task types available in the "Add Task" menu.
 * Core OSS tasks are defined here, while enterprise tasks are registered
 * via the plugin system.
 */
import { BaseTaskMenuItem } from "./state/types";
/**
 * Core OSS System Tasks
 * These are fundamental system tasks available in open source Conductor.
 */
export declare const SYSTEM_TASKS: BaseTaskMenuItem[];
/**
 * Core OSS Operator Tasks
 * These are control flow operators available in open source Conductor.
 */
export declare const OPERATOR_TASKS: BaseTaskMenuItem[];
/**
 * Core OSS Worker Tasks
 */
export declare const WORKER_TASKS: BaseTaskMenuItem[];
/**
 * AI/LLM Tasks for Agentic Orchestration
 * These are AI-powered tasks for building intelligent workflows.
 */
export declare const AI_TASKS: BaseTaskMenuItem[];
/**
 * @deprecated Use AI_TASKS instead
 */
export declare const LLM_TASKS: BaseTaskMenuItem[];
/**
 * Returns all available tasks including plugin-registered tasks.
 * Called at runtime so plugin items (e.g. Wait For Webhook Task) are included when the menu opens.
 */
export declare const getALL_TASKS: () => BaseTaskMenuItem[];
/**
 * @deprecated Use getALL_TASKS() so plugin items are included (ALL_TASKS is computed at module load and may miss plugins).
 */
export declare const ALL_TASKS: BaseTaskMenuItem[];
