import{jsx as a}from"react/jsx-runtime";import{MaybeVariable as l}from"./MaybeVariable.js";function f(t){return function(e){const r=n=>e.onChange?e.onChange(n):e.onChangeHeaders?e.onChangeHeaders(n):()=>{};return e.taskType&&e.path?a(l,{value:e.value,onChange:r,taskType:e.taskType,path:e?.path,children:a(t,{...e})}):null}}export{f as default};
//# sourceMappingURL=maybeVariableHOC.js.map
