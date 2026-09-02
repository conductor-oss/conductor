import{jsx as g}from"react/jsx-runtime";import{ApiSearchModal as w}from"../../components/ApiSearchModal.js";import{curlHeaders as f}from"../../shared/CodeModal/curlHeader.js";import{useParamsToSdk as y}from"../../shared/CodeModal/hook.js";const k=(o,e)=>{const{correlationId:t,name:r,version:n,input:a,taskToDomain:i,idempotencyKey:s,idempotencyStrategy:c}=o,d={...f(e),"Content-Type":"application/json"},u={name:r,version:n,input:a,correlationId:t,idempotencyKey:s,...c&&{idempotencyStrategy:c},...i&&{taskToDomain:i}};return`curl '${window.location.origin}/api/workflow' \\${Object.entries(d).map(([m,p])=>`
-H '${m}: ${p}' \\`).join("")}
--data-raw '${JSON.stringify(u)}'`},$=(o,e)=>{const{correlationId:t,name:r,version:n,input:a,taskToDomain:i,idempotencyKey:s,idempotencyStrategy:c}=o;return`import { orkesConductorClient, WorkflowExecutor } from "@io-orkes/conductor-javascript";
    
async function runWorkflow() {
  const client = await orkesConductorClient({
    TOKEN: "${e}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new WorkflowExecutor(client);

  const data = ${`{
    name: "${r}",
    version: "${n}",
    input: ${JSON.stringify(a)},
    correlationId: "${t}",
    idempotencyKey:"${s}",
    ${c?`idempotencyStrategy:"${c}",`:""}
    ${i?`taskToDomain: ${JSON.stringify(i)},`:""}
  };`.replace(/^\s*[\r\n]/gm,"")}

  const result = await executor.startWorkflow(data);
      
  return result;
}
  
runWorkflow();
      `},l={curl:k,javascript:$},x=({onClose:o,buildQueryOutput:e})=>{const{selectedLanguage:t,setSelectedLanguage:r,code:n}=y(e,l);return g(w,{displayLanguage:t,handleClose:o,code:n,onTabChange:a=>{r(a)},dialogTitle:"Run Workflow API",dialogHeaderText:"Here is the code for the run workflow.",languages:Object.keys(l)})};export{x as RunWorkflowApiSearchModal};
//# sourceMappingURL=RunWorkflowApiSearchModal.js.map
