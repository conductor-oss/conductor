import { toMaybeQueryString } from "./toMaybeQueryString";
import { featureFlags, FEATURES } from "utils/flags";

export const maybeTriggerFailureWorkflow = () =>
  toMaybeQueryString(
    featureFlags.isEnabled(FEATURES.TRIGGER_WORKFLOW)
      ? { triggerFailureWorkflow: true }
      : {},
  );
