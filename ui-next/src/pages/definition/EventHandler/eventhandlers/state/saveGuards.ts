import _isEmpty from "lodash/isEmpty";

/**
 * Save is blocked when the request would be invalid or a no-op.
 * Name and event are required by the API; an empty description is not.
 */
export const isFormSaveDisabled = ({
  name,
  event,
  noChanges,
  isTrialExpired,
}: {
  name?: string | null;
  event?: string | null;
  noChanges: boolean;
  isTrialExpired: boolean;
}) => {
  const missingRequired = [event?.trim(), name?.trim()].some((value) =>
    _isEmpty(value),
  );
  return missingRequired || noChanges || isTrialExpired;
};

/**
 * Code-tab Save also requires parseable JSON whose name/event are non-empty.
 * When there is no editor string yet, required-field checks are skipped (same
 * as the previous inline HOC: empty editorChanges → isEmptyValue false).
 */
export const isEditorSaveDisabled = ({
  name,
  event,
  noChanges,
  invalidJson,
  isTrialExpired,
  hasEditorContent,
}: {
  name?: string | null;
  event?: string | null;
  noChanges: boolean;
  invalidJson: boolean;
  isTrialExpired: boolean;
  hasEditorContent: boolean;
}) => {
  const missingRequired = hasEditorContent
    ? [event?.trim(), name?.trim()].some((value) => _isEmpty(value))
    : false;
  return noChanges || invalidJson || missingRequired || isTrialExpired;
};
