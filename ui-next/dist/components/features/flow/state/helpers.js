const p=(a,t)=>({...a,data:{...a.data,...t}}),c=(a,t)=>t==null?a:a.map((e,l)=>p(e,{selected:l===t}));export{c as applyNodeSelectionHelpr,p as mergeInNodeData};
//# sourceMappingURL=helpers.js.map
