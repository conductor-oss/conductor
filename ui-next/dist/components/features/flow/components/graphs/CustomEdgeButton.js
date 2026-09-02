import{jsx as o,Fragment as A,jsxs as g}from"react/jsx-runtime";import{useMemo as M}from"react";import{BOTTOM_PORT_MARGIN as j}from"../../nodes/mapper/layout.js";import c from"../shapes/TaskCard/icons/PlusIcon.js";import m from"../shapes/TaskCard/icons/MinusIcon.js";import{keyframes as u,styled as h}from"@mui/system";import{isSafari as L}from"../../../../../utils/utils.js";import"@dnd-kit/core";import"@xstate/react";import"../shapes/TaskShape/Shape.js";import{useDroppableNode as P}from"../../dragDrop/hooks.js";import"../../dragDrop/Handle.js";import R from"classnames";const S=u`
0% {
  background-position: left top, right bottom, left bottom, right   top;
}
100% {
  background-color:  rgba(159,220,170,0.5);
  background-position: left 15px top, right 15px bottom , left bottom 15px , right   top 15px;
}
`,T=u`
  0% {
    box-shadow: 0 0 8px 2px rgba(33, 150, 243, 0.5);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 12px 4px rgba(33, 150, 243, 0.7);
    transform: scale(1.02);
  }
  100% {
    box-shadow: 0 0 8px 2px rgba(33, 150, 243, 0.5);
    transform: scale(1);
  }
`,x=h("div")`
  &.active {
    animation: ${T} 1.5s ease-in-out infinite;
    background-color: #e3f2fd;
    border: 2px solid #2196f3;
  }
`,B=h("div")`
  &.over {
    background-image:
      linear-gradient(90deg, silver 50%, transparent 50%),
      linear-gradient(90deg, silver 50%, transparent 50%),
      linear-gradient(0deg, silver 50%, transparent 50%),
      linear-gradient(0deg, silver 50%, transparent 50%);
    background-repeat: repeat-x, repeat-x, repeat-y, repeat-y;
    background-size:
      15px 2px,
      15px 2px,
      2px 15px,
      2px 15px;
    background-position:
      left top,
      right bottom,
      left bottom,
      right top;
    animation: ${S} 1s infinite linear;
  }

  &.dragging {
  }
  position: absolute;
  top: 10px;
  height: ${r=>r.dropIsDisabled||r.draggedNodeData==null?0:80}px;
  width: ${r=>r.dropIsDisabled||r.draggedNodeData==null?0:r.draggedNodeData.width}px;
`,z=({activeEdgeId:r,x:s,y:d,size:e=20,hidden:b=!0,variant:i="ADD",onEnter:l=()=>{},onLeave:p=()=>{},onClick:n=()=>{},onDeleteClick:D=()=>{},data:w,nodeId:$,port:a})=>{const{droppableResult:{isOver:y,setNodeRef:v},draggedNodeData:f,dropIsDisabled:k}=P({nodeData:w,position:a.side==="NORTH"?"ABOVE":"BELOW",nodeId:$}),{translateX:N,translateY:C,offset:E}=M(()=>{const t=e/2,I=s-t,O=d-(t+(a.side==="SOUTH"?j:0));return{translateX:I,translateY:O,offset:L?15:0}},[a.side,e,s,d]);return b?null:o(A,{children:o("g",{transform:`translate(${N}, ${C+E})`,children:o("foreignObject",{style:{overflow:"visible",cursor:"pointer"},onClick:t=>{t.preventDefault(),t.stopPropagation(),n(t)},width:e+20,height:e+20,children:i==="ADD"||i==="DELETE"?g(x,{className:r===a.id?"active":"",style:{cursor:"pointer",display:"flex",width:`${e}px`,height:`${e}px`,backgroundColor:"#ffffff",alignItems:"center",justifyContent:"center",borderRadius:`${e}px`,boxShadow:"0 0 10px rgba(0, 0, 0, 0.5)",whiteSpace:"nowrap",overflow:"hidden"},id:`${i}-${a.id}`,onClick:t=>{t.preventDefault(),t.stopPropagation(),n(t)},onMouseEnter:l,onMouseLeave:p,children:[o(B,{draggedNodeData:f,className:R({over:y},{dragging:f!=null}),dropIsDisabled:k,ref:v,id:"dropping_zone"}),i==="ADD"?o(c,{size:14}):o(m,{size:14})]}):g(x,{className:r===a.id?"active":"",style:{display:"flex",width:`${e*2+10}px`,height:`${e}px`,marginLeft:`-${e/2+5}px`,backgroundColor:"#ffffff",alignItems:"center",justifyContent:"center",borderRadius:`${e}px`,boxShadow:"0 0 10px rgba(0, 0, 0, 0.5)",whiteSpace:"nowrap",overflow:"hidden"},onMouseEnter:l,onMouseLeave:p,children:[o("div",{style:{cursor:"pointer",height:`${e}px`,width:"100%",display:"flex",alignItems:"center",justifyContent:"center"},id:`ADD-${a.id}`,onClick:t=>{t.preventDefault(),t.stopPropagation(),n(t)},children:o(c,{size:14})}),o("div",{style:{cursor:"pointer",height:`${e}px`,marginLeft:"-1px",borderLeft:"1px solid rgba(0,0,0,.3)",width:"100%",display:"flex",alignItems:"center",justifyContent:"center"},id:`DELETE-${a.id}`,onClick:t=>{t.preventDefault(),t.stopPropagation(),D(t)},children:o(m,{size:14})})]})})})})};export{z as CustomEdgeButton,z as default};
//# sourceMappingURL=CustomEdgeButton.js.map
