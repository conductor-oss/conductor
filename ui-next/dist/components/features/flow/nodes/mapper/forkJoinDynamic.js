import{forkJoinTaskToNode as i,processForkJoinTasks as k}from"./forkJoin.js";const t=async(o,n,e)=>{const s=i(o,n),{nodes:r,edges:d}=await k(o,n,e);return{nodes:[s,...r],edges:d}};export{t as taskToForkJoinDynamicNodesEdges};
//# sourceMappingURL=forkJoinDynamic.js.map
