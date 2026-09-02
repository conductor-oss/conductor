import{useState as l,useMemo as u}from"react";import{useWorkflowNames as f}from"./query.js";const w=(e=o=>!0)=>{const[o,r]=l(!1),t=f({enabled:o}),s=u(()=>t.filter(e).sort((a,m)=>a.toLowerCase().localeCompare(m.toLowerCase())),[t,e]);return[()=>r(!0),s]};export{w as useLazyWorkflowNameAutoComplete};
//# sourceMappingURL=useLazyWorkflowNameAutoComplete.js.map
