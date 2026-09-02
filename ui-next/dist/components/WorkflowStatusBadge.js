import{jsx as l}from"react/jsx-runtime";import{getChipStatusColor as e}from"../utils/helpers.js";import{humanizeStatus as i}from"../utils/utils.js";import m from"./ui/TagChip.js";const u=({status:o})=>{const t=e(o),a=t==null?{}:{backgroundColor:t},r=i(o);return l(m,{style:a,label:r,id:`${r}-chip`})};export{u as default};
//# sourceMappingURL=WorkflowStatusBadge.js.map
