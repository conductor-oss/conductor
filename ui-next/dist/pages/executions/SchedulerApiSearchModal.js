import{jsx as i}from"react/jsx-runtime";import{ApiSearchModal as l}from"../../components/ApiSearchModal.js";import{curlHeaders as u}from"../../shared/CodeModal/curlHeader.js";import{useParamsToSdk as d}from"../../shared/CodeModal/hook.js";import{buildSchedulerSearchCli as m}from"./cliSearch.js";const p=({start:e,size:r,sort:t,freeText:o,query:s})=>`${window.location.origin}/api/scheduler/search/executions?${new URLSearchParams({start:String(e),size:String(r),sort:t,freeText:o,query:s}).toString()}`,h=(e,r)=>{const t=p(e),o=u(r);return`curl '${t}' \\${Object.entries(o).map(([n,c])=>`
-H '${n}: ${c}' \\`).join("")}
--compressed`},C=(e,r)=>{const{start:t,size:o,sort:s,freeText:n,query:c}=e;return`import { orkesConductorClient, SchedulerClient } from "@io-orkes/conductor-javascript";
    
async function searchSchedule(
  start = ${t},
  size = ${o},
  sort = "${s}",
  freeText = "${n}",
  query = "${c}",
) {
  const client = await orkesConductorClient({
    TOKEN: "${r}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new SchedulerClient(client);
  const results = await executor.search(start, size, sort, freeText, query);
      
  return results;
}
  
searchSchedule();
      `},a={curl:h,cli:m,javascript:C},x=({onClose:e,buildQueryOutput:r})=>{const{selectedLanguage:t,setSelectedLanguage:o,code:s}=d(r,a);return i(l,{displayLanguage:t,handleClose:e,code:s,onTabChange:n=>{o(n)},languages:Object.keys(a)})};export{x as SchedulerApiSearchModal};
//# sourceMappingURL=SchedulerApiSearchModal.js.map
