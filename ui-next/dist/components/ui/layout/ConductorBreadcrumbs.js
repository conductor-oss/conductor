import{jsx as e,Fragment as i,jsxs as l}from"react/jsx-runtime";import{Breadcrumbs as a}from"@mui/material";import p from"@mui/material/Typography";import{styled as s}from"@mui/system";import{Link as f}from"react-router";import{blue15 as m}from"../../../theme/tokens/colors.js";const d=s(f)`
  text-decoration: none;
  color: ${o=>o.color?o.color:m};
  font-size: 12px;
  font-weight: 300;
  line-height: 16px;
  display: "flex",
  alignItems: "center",
`,g={fontSize:"12px",fontWeight:300,color:o=>o.palette.input.text,lineHeight:"16px",display:"flex",alignItems:"center"},h={".MuiBreadcrumbs-separator":{color:"#161616",".MuiSvgIcon-root":{fontSize:"28px"}}},B=({items:o,color:n,...c})=>e(i,{children:e(a,{...c,sx:h,children:o&&o.map((t,r)=>r!==o.length-1?l(d,{color:n,to:t.to,children:[t.label,t.icon]},r):l(p,{sx:g,children:[t.label,t.icon]},r))})});export{B as default};
//# sourceMappingURL=ConductorBreadcrumbs.js.map
