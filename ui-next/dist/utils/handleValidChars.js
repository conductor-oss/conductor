import{TITLE_ALLOWED_CHARS as s}from"./constants/common.js";const g=(t,r=s)=>e=>{new RegExp(r).test(e)&&t(e)},o=(t,r=s)=>e=>{const n=e.target.value;new RegExp(r).test(n)&&t({...e,target:{value:n}})};export{g as handleValidChars,o as handleValidCharsForEvents};
//# sourceMappingURL=handleValidChars.js.map
