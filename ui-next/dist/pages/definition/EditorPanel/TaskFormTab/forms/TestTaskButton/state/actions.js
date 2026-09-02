import{assign as s}from"xstate";const o=s((e,{domain:t})=>({taskDomain:t})),a=s((e,{inputParameters:t})=>({taskChanges:t})),i=s({testExecutionId:(e,{data:t})=>t}),c=s((e,{data:t})=>({testedTaskExecutionResult:t}));export{i as persistExecutionId,a as persistTaskChanges,c as persistTestedTaskExecutionResult,o as setTaskDomain};
//# sourceMappingURL=actions.js.map
