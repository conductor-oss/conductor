import{useSelector as a}from"@xstate/react";import{DefinitionMachineEventTypes as o}from"./types.js";const r=e=>{const n=()=>{e.send({type:o.HANDLE_LEFT_PANEL_EXPANDED,onSelectNode:!1})};return{leftPanelExpanded:a(e,t=>t.matches("ready.rightPanel.closed")),setLeftPanelExpanded:n}};export{r as usePanelChanges};
//# sourceMappingURL=usePanelChanges.js.map
