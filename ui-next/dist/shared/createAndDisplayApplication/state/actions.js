import{assign as e}from"xstate";const i=e((t,{data:r})=>({applicationAccessKey:{id:r.id,secret:r.secret}})),o=e((t,{data:r})=>({applicationId:r.id})),n=e((t,{data:r})=>({errorCreatingAppMessage:r.message})),p=e(()=>({errorCreatingAppMessage:void 0}));export{p as clearError,o as persistApplicationId,i as persistApplicationKeys,n as persistError};
//# sourceMappingURL=actions.js.map
