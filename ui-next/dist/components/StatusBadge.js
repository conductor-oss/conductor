import{jsx as i}from"react/jsx-runtime";import{getChipStatusColor as l}from"../utils/helpers.js";import{humanizeStatus as m}from"../utils/utils.js";import s from"./ui/TagChip.js";const f=({status:t,labelConcat:a=""})=>{const o=l(t),e=o==null?{}:{backgroundColor:o},r=m(t);return i(s,{style:e,label:`${r}${a}`,id:`${r}-chip`})};export{f as default};
//# sourceMappingURL=StatusBadge.js.map
