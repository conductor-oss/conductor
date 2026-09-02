import{extractExecutionDataOrEmpty as m}from"./common.js";import{taskToSize as n,BOTTOM_PORT_MARGIN as c}from"./layout.js";const d=(t,e=[])=>{const{taskReferenceName:o,name:r}=t,{width:i,height:a}=n(t);return{id:o,text:r,data:{task:t,crumbs:e,...m(t)},width:i,height:a+c}};export{d as taskToTerminateNode};
//# sourceMappingURL=terminate.js.map
