import{useSelector as i}from"@xstate/react";const a=t=>{const[n,o]=i(t,e=>e.context.editableFieldActors);return[{ownerEmail:i(t,e=>e.context.metadataChanges?.ownerEmail),updateTime:i(t,e=>e.context.metadataChanges?.updateTime),isDisabled:i(t,e=>e.matches("editingDisabled")),nameFieldActor:n,descriptionFieldActor:o}]};export{a as useWorkflowMetadataEditorActor};
//# sourceMappingURL=hook.js.map
