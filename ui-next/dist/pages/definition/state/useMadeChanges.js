import{useMemo as c}from"react";import{useSelector as s}from"@xstate/react";import u from"fast-deep-equal";const l=t=>{const e=s(t,o=>o.context.isNewWorkflow),r=s(t,o=>o.context.currentWf),n=s(t,o=>o.context.workflowChanges),f=c(()=>e?!0:!u(n,r),[n,r,e]);return{isNewWorkflow:e,currentWf:r,workflowChanges:n,madeChanges:f}};export{l as useWorkflowChanges};
//# sourceMappingURL=useMadeChanges.js.map
