import{jsx as i}from"react/jsx-runtime";import{ApiSearchModal as l}from"../../../components/ApiSearchModal.js";import{useParamsToSdk as d}from"../../../shared/CodeModal/hook.js";import{curlHeaders as m}from"../../../shared/CodeModal/curlHeader.js";import{buildEndpoint as u}from"./agentSearchCode.js";const p=(e,o)=>{const r=u(e),t=m(o);return`curl '${r}' \\${Object.entries(t).map(([s,a])=>`
-H '${s}: ${a}' \\`).join("")}
--compressed`},f=(e,o)=>`import { orkesConductorClient, WorkflowExecutor } from "@io-orkes/conductor-javascript";
    
async function searchExecution(
  start = ${e.start},
  size = ${e.size},
  query = "${e.query}",
  freeText = "${e.freeText}",
  sort = "${e.sort}"
) {
  const client = await orkesConductorClient({
    TOKEN: "${o}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new WorkflowExecutor(client);
  const results = await executor.search(start, size, query, freeText, sort );
      
  return results;
  }
  
  searchExecution();
      `,c={curl:p,javascript:f},k=({onClose:e,buildQueryOutput:o})=>{const{selectedLanguage:r,setSelectedLanguage:t,code:n}=d(o,c);return i(l,{displayLanguage:r,handleClose:e,code:n,onTabChange:s=>{t(s)},languages:Object.keys(c)})};export{k as ApiSearchModalIntegration};
//# sourceMappingURL=ApiSearchModalIntegration.js.map
