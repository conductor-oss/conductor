import{fetchExecutionFull as e}from"../../../commonServices/execution.js";import{useQuery as f}from"react-query";function i(r,l,o){return f(["workflow-full",r],()=>e({authHeaders:l,executionId:r}),{enabled:o&&!!r,staleTime:1/0})}export{i as useFullWorkflowQuery};
//# sourceMappingURL=useFullWorkflowQuery.js.map
