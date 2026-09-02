import{jsx as r}from"react/jsx-runtime";import{forwardRef as a}from"react";import{styled as c}from"@mui/system";const d=c("button")`
  position: absolute;
  left: 0;
  z-index: 3;
  display: flex;
  width: 12px;
  padding: 15px;
  align-items: center;
  border: none;
  justify-content: center;
  flex: 0 0 auto;
  touch-action: none;
  cursor: var(--cursor, pointer);
  border-radius: 5px;
  outline: none;
  appearance: none;
  background-color: transparent;
  -webkit-tap-highlight-color: transparent;

  @media (hover: hover) {
    &:hover {
      background-color: var(--action-background, rgba(0, 0, 0, 0.05));

      svg {
        fill: #6f7b88;
      }
    }
  }

  svg {
    flex: 0 0 auto;
    margin: auto;
    height: 100%;
    overflow: visible;
    fill: #919eab;
  }

  &:active {
    background-color: var(--background, rgba(0, 0, 0, 0.05));

    svg {
      fill: var(--fill, #788491);
    }
  }

  &:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 2px rgba(255, 255, 255, 0),
      0 0px 0px 2px #4c9ffe;
  }
`,s=a(({active:o,className:e,cursor:n,style:t,...i},l)=>r(d,{ref:l,...i,className:e,tabIndex:0,style:{...t,cursor:n,"--fill":o?.fill,"--background":o?.background}})),p=a((o,e)=>r(s,{ref:e,cursor:"grab","data-cypress":"draggable-handle",style:{zIndex:9},...o,children:r("svg",{viewBox:"0 0 20 20",width:"12",children:r("path",{d:"M7 2a2 2 0 1 0 .001 4.001A2 2 0 0 0 7 2zm0 6a2 2 0 1 0 .001 4.001A2 2 0 0 0 7 8zm0 6a2 2 0 1 0 .001 4.001A2 2 0 0 0 7 14zm6-8a2 2 0 1 0-.001-4.001A2 2 0 0 0 13 6zm0 2a2 2 0 1 0 .001 4.001A2 2 0 0 0 13 8zm0 6a2 2 0 1 0 .001 4.001A2 2 0 0 0 13 14z"})})}));export{s as Action,p as Handle};
//# sourceMappingURL=Handle.js.map
