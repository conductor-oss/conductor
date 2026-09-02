import{getColumnId as i}from"../helpers.js";import e from"lodash/isNil";const c=o=>e(o.localStorageKey),m=({columnOrderAndVisibility:o},{data:r})=>{const n=o.map(i);return r.every(t=>!e(t)&&n.includes(t?.id))};export{m as isLocalStorageContentTrusted,c as noLocalStorageKey};
//# sourceMappingURL=guards.js.map
