import{useSelector as e}from"@xstate/react";const A=o=>{const[r,s,c,a,i,l]=e(o,t=>t.context.editableFieldActors),n=e(o,t=>t.hasTag("editingEnabled"));return[{inputParametersActor:r,outputParametersActors:s,restartableActors:c,timeoutSecondsActors:a,timeoutPolicyActors:i,failureWorkflowActors:l,isReady:n}]};export{A as useWorkflowMetadataEditorActor};
//# sourceMappingURL=hook.js.map
