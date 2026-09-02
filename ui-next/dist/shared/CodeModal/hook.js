import{useState as r}from"react";import n from"lodash/property";const m=(o,t)=>{const[e,c]=r("curl"),s=n(e)(t);return{selectedLanguage:e,setSelectedLanguage:c,code:s(o,"")}};export{m as useParamsToSdk};
//# sourceMappingURL=hook.js.map
