import{useRef as f,useEffect as n,useLayoutEffect as o}from"react";const s=typeof window<"u"?o:n;function i(t,e){const r=f(t);s(()=>{r.current=t},[t]),n(()=>{if(!e&&e!==0)return;const u=setInterval(()=>r.current(),e);return()=>clearInterval(u)},[e])}export{i as default};
//# sourceMappingURL=useInterval.js.map
