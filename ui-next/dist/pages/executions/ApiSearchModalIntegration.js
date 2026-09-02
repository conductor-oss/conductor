import{jsx as i}from"react/jsx-runtime";import{ApiSearchModal as l}from"../../components/ApiSearchModal.js";import{useParamsToSdk as d}from"../../shared/CodeModal/hook.js";import{curlHeaders as m}from"../../shared/CodeModal/curlHeader.js";import{buildWorkflowSearchCli as u}from"./cliSearch.js";const g=({start:o,size:e,sort:r,freeText:t,query:n})=>`${window.location.origin}/api/workflow/search?${new URLSearchParams({start:String(o),size:String(e),sort:r,freeText:t,query:n}).toString()}`,p=(o,e)=>{const r=g(o),t=m(e);return`curl '${r}' \\${Object.entries(t).map(([s,c])=>`
-H '${s}: ${c}' \\`).join("")}
--compressed`},f=(o,e)=>`import { orkesConductorClient, WorkflowExecutor } from "@io-orkes/conductor-javascript";
    
async function searchExecution(
  start = ${o.start},
  size = ${o.size},
  query = "${o.query}",
  freeText = "${o.freeText}",
  sort = "${o.sort}"
) {
  const client = await orkesConductorClient({
    TOKEN: "${e}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new WorkflowExecutor(client);
  const results = await executor.search(start, size, query, freeText, sort );
      
  return results;
  }
  
  searchExecution();
      `,a={curl:p,cli:u,javascript:f},k=({onClose:o,buildQueryOutput:e})=>{const{selectedLanguage:r,setSelectedLanguage:t,code:n}=d(e,a);return i(l,{displayLanguage:r,handleClose:o,code:n,onTabChange:s=>{t(s)},languages:Object.keys(a)})};export{k as ApiSearchModalIntegration};
//# sourceMappingURL=ApiSearchModalIntegration.js.map
