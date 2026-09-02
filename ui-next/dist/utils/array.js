const a=(c,s,t)=>Object.assign([],t,{[c]:s()}),e=(c,s,t)=>{const o=t.slice();return o.splice(c,s),o},n=(c,s,t)=>t.slice(0,c).concat(s).concat(t.slice(c)),r=(c,s)=>c.flatMap(t=>s.map(o=>[t,o]));export{a as adjust,r as cartesianProduct,n as insert,e as remove};
//# sourceMappingURL=array.js.map
