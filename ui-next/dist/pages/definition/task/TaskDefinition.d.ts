/**
 * NOTE:
 * 1. Single mode: After POST successfully will redirect to task detail page
 * 2. Bulk mode or Save and Create New: Stay at the same page with current state
 * 3. Test task: execute a workflow with current task
 * 4. Form mode doesn't have bulk creation
 */
export default function TaskDefinition(): import("react").JSX.Element;
