import{updateField as n}from"../../../../../../utils/fieldHelpers.js";const h=({task:t,onChange:a})=>({handleTaskStatusChange:e=>a(n("inputParameters.taskStatus",e,t)),handleMergeOutputChange:e=>{const r=e.target.checked;a(n("inputParameters.mergeOutput",r,t))}});export{h as useUpdateTaskHandler};
//# sourceMappingURL=common.js.map
