import{useState as r}from"react";function f(){const[a,n]=r(!0),[s,e]=r(!1);return{isSummarized:a,confirmOpen:s,handleToggleChange:t=>{t?n(!0):e(!0)},handleConfirm:()=>{n(!1),e(!1)},handleCancel:()=>e(!1)}}export{f as useSummarize};
//# sourceMappingURL=useSummarize.js.map
