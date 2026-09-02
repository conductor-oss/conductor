import{useEnv as s}from"../../plugins/env.js";import{useNavigate as u}from"react-router";import i from"url-parse";function m(){const o=u(),{stack:t,defaultStack:e}=s();return n=>{const r=new i(n,{},!0);t!==e&&(r.query.stack=t),o(r.toString())}}export{m as usePushHistory};
//# sourceMappingURL=usePushHistory.js.map
