import{useEnv as n}from"../../plugins/env.js";import{useNavigate as u}from"react-router";import c from"url-parse";function m(){const e=u(),{stack:t,defaultStack:o}=n();return a=>{const r=new c(a,{},!0);t!==o&&(r.query.stack=t),e(r.toString(),{replace:!0})}}export{m as useReplaceHistory};
//# sourceMappingURL=useReplaceHistory.js.map
