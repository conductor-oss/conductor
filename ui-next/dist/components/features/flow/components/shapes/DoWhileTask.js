import{jsxs as i,Fragment as f,jsx as e}from"react/jsx-runtime";import{keyframes as h,styled as u,IconButton as b}from"@mui/material";import{Plus as k,Repeat as y}from"@phosphor-icons/react";import v from"classnames";import{useDroppableNode as _}from"../../dragDrop/hooks.js";import w from"lodash/isEmpty";import{ADD_TASK_IN_DO_WHILE as I}from"../../../../../pages/definition/state/taskModifier/constants.js";import{useMemo as C}from"react";import N from"./TaskCard/CardAttemptsBadge.js";import A from"./TaskCard/CardLabel.js";import B from"./TaskCard/CardStatusBadge.js";import O from"./TaskCard/DeleteButton.js";import{getCardVariant as R}from"./styles.js";const S=h`
0% {
  background-position: left top, right bottom, left bottom, right   top;
}
100% {
  background-color:  rgba(159,220,170,0.5);
  background-position: left 15px top, right 15px bottom , left bottom 15px , right   top 15px;
}
`,L=u("div")`
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
    height: 340px;
  }

  &.dragging {
  }
  position: absolute;
  top: 60px;
  height: 340px;
  width: ${t=>t.dropIsDisabled||t.draggedNodeData==null?0:"350"}px;
`,G=({nodeData:t,onToggleTaskMenu:p,isInconsistent:d,nodeId:m="",displayDescription:a=!1})=>{const{task:r}=t,{type:g}=r,{droppableResult:{isOver:l,setNodeRef:s},draggedNodeData:o,dropIsDisabled:n}=_({nodeData:t,position:"ADD_TASK_IN_DO_WHILE",nodeId:m+"_drag_to_dowhile"}),c=C(()=>r.executionData==null&&w(r.loopOver)?i(f,{children:[e(L,{draggedNodeData:o,className:v({over:l},{dragging:o!=null}),dropIsDisabled:n,ref:s,id:"dropping_zone"}),e(b,{onClick:x=>{p(x,{id:`${r.taskReferenceName}_inner_do_while`,port:void 0,node:{data:{...t,action:I}}})},style:{backgroundColor:"#ffffff"},children:e(k,{})})]}):null,[r,t,p,s,o,n,l]);return i("div",{style:{cursor:d?"not-allowed":"pointer",display:"flex",width:"100%",minWidth:"570px",padding:"20px",border:"1px dashed black",borderRadius:"20px",textAlign:"center",alignItems:"center",justifyContent:"center",position:"relative",...R(g,t.status,t.selected),background:"rgba(0,50,100,.5)"},children:[e(B,{status:t.status}),t?.attempts>1?e(N,{attempts:t.attempts}):null,e(O,{maybeHideData:t}),i("div",{style:{position:"absolute",top:"10px",left:"10px",display:"flex",alignItems:"center",width:"93%"},children:[e("div",{style:{height:"24px",width:"24px"},children:e(y,{size:24,color:"white"})}),e("div",{style:{paddingLeft:"6px",marginTop:"-2px",color:"white",textShadow:"0 1px 2px black",display:"block",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"},children:a&&t.task.description!=null?t.task.description:t.task.name})]}),e("div",{style:{position:"absolute",top:"20px",right:"20px"},children:e(A,{type:t.task.type,displayDescription:a})}),c]})};export{G as default};
//# sourceMappingURL=DoWhileTask.js.map
