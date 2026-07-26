export const CONTAIN_VARIABLE_SYNTAX_REGEX = /^(?=.*?[${}]{1}).*$/;

// The backend allows everything but a semicolon
// https://github.com/orkes-io/conductor/blob/f07dc36f08dcaf91cb40ea6ee211c840de5ac8f3/common/src/main/java/com/netflix/conductor/common/metadata/workflow/WorkflowDefSummary.java#L29C24-L29C89
// Using `()` would cause errors in querys such as:
// workflowType IN (wf_name(test), wf_name2), because the
// end parenthesis would be interpreted as the end of the
// IN clause.
// `/` and `%` are additionally disallowed: although the backend accepts them,
// a name containing either cannot be reopened from the UI. The name is carried
// as a path segment (/workflowDef/{name} and GET /metadata/workflow/{name}), and
// the percent-encoded `%2F` / `%25` is rejected by the server's path handling
// (400), leaving the definition saved but unreachable.
export const WORKFLOW_NAME_REGEX =
  /^(?! )[.A-Za-z0-9!@#$^&*_<>{}[\]|+=\s-]+(?<! )$/;

export const TASK_NAME_REGEX = /^[A-Za-z0-9_<>{}#\s-]*$/;

// toString() here will escape some chars,
// the slice() will remove the trailing "/" from both ends
export const regexToString = (regex: RegExp): string =>
  regex.toString().slice(1, -1);
