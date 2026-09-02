import{toMaybeQueryString as r}from"./toMaybeQueryString.js";import{featureFlags as e,FEATURES as o}from"./flags.js";const a=()=>r(e.isEnabled(o.TRIGGER_WORKFLOW)?{triggerFailureWorkflow:!0}:{});export{a as maybeTriggerFailureWorkflow};
//# sourceMappingURL=maybeTriggerWorkflow.js.map
