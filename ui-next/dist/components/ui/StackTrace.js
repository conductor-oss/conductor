import{jsxs as o,jsx as l}from"react/jsx-runtime";import{useState as h}from"react";function y({stacktrace:s}){const e=s.split(`
`),i=e.slice(0,3),n=e.slice(3),[t,c]=h(!0),r=()=>{c(!t)},a={cursor:"pointer",color:"#1976d2"},p=o("span",{style:{display:t?"none":"inline"},children:[n.join(`
`),l("br",{})]}),d=l("span",{onClick:r,style:{display:e.length>3?"inherit":"none",...a},children:t?`${n.length} more lines`:`Hide ${n.length} lines`});return o("code",{style:{margin:0,whiteSpace:"pre"},children:[i.join(`
`),l("br",{}),p,"	",d]})}export{y as StackTraceComponent};
//# sourceMappingURL=StackTrace.js.map
