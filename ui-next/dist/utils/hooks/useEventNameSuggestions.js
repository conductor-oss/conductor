import{useMemo as r}from"react";import{useGetIntegration as m}from"./useGetIntegrations.js";import{MESSAGE_BROKER as n}from"../constants/event.js";const g=()=>{const{data:t=[]}=m({category:n});return r(()=>t.map(({type:o,name:e})=>`${o}:${e}`),[t])};export{g as useEventNameSuggestions};
//# sourceMappingURL=useEventNameSuggestions.js.map
