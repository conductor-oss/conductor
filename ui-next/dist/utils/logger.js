import{flipObject as c}from"./object.js";const t=["debug","info","log","warn","error"],s=Object.assign({},t),e=c(s),g=e.warn,O=o=>{const r=e[o];return(...n)=>{r>=g&&console[o](...n)}},b=t.reduce((o,r)=>({...o,[r]:O(r)}),{});export{b as logger};
//# sourceMappingURL=logger.js.map
