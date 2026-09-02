import{jsx as l}from"react/jsx-runtime";import{useCallback as t}from"react";import f from"../../components/ui/dialogs/ConfirmChoiceDialog.js";const c=({onConfirm:o,onCancel:r,shouldPrompt:i,title:a="Confirmation",message:e})=>{const m=t(n=>(n?o:r)(),[o,r]);return i?l(f,{handleConfirmationValue:m,message:e,header:a}):null};export{c as ConfirmDialog};
//# sourceMappingURL=ConfirmDialog.js.map
