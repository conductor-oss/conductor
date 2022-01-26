import { colors } from "../theme/variables";

export default {
  wrapper: {
    overflowY: "scroll",
    overflowX: "hidden",
    height: "100%",
  },
  padded: {
    padding: 30,
  },
  header: {
    backgroundColor: colors.gray14,
    padding: "20px 30px 0 30px",
    zIndex: 1,
  },
  tabContent: {
    padding: 30,
  },
  resizer: {
    width: 10,
    margin: "0 -5px",
    marginTop: -1,
    cursor: "col-resize",
    backgroundColor: "rgb(45, 45, 45, 0.05)",
    zIndex: 1,
    resize: "horizontal",
    "&:hover" : {
      backgroundColor: "rgb(45, 45, 45, 0.3)",
    }
  },
  definitionEditorParentBox: {
    height: "100%",
    flex: "1 1 0%",
    position: "relative",
    paddingTop: 3,
  },
  definitionEditorBox: {
    height: "100%",
    width: "100%",
    overflow: "visible",
    display: "flex",
    flexDirection: "column",
    backgroundColor: "transparent",
    fontSize: "13px",
    position: "relative",
    zIndex: 1
  },
  definitionEditorBoxPanel: {
    display: "flex",
    height: "100%",
    position: "absolute",
    overflow: "visible",
    userSelect: "text",
    flexDirection: "row",
    left: "0px",
    right: "0px"
  },
  workflowDefFirstRowMenu: {
    width: "100%",
    display: "flex",
    flexFlow: "row",
    justifyContent: "space-around",
    paddingTop: 3,
    paddingBottom: 3,
    alignItems: "center",
    backgroundColor: "ghostwhite",
    position: "sticky",
    top: 0,
    left: 0
  },
  definitionEditorSecondRowMenu: {
    width: "100%",
    display: "flex",
    flexFlow: "row",
    justifyContent: "space-around",
    paddingTop: 3,
    paddingBottom: 6,
    borderBottom: "solid rgba(73, 105, 228, 0.3) 2px",
    alignItems: "center",
    backgroundColor: "ghostwhite",
    position: "sticky",
    top: 0,
    left: 0
  },
  popover: {
    "& .MuiPopover-paper": {
      padding: "8px",
      backgroundColor: "rgb(45, 45, 45, 0.6)",
      color: "white"
    },
    "& .MuiTypography-root": {
      fontSize: "14px"
    }
  },
  editorLabel: {
    color: "black",
    opacity: 0.8,
    paddingLeft: 10,
    fontSize: 12,
    lineHeight: 3,
    fontWeight: "400",
    "& span": {
      fontSize: 13,
      fontWeight: "bold"
    },
    "& svg": {
      fontSize: 18,
    }
  },
  iconButton: {
    color: "black",
    opacity: 0.3,
    paddingRight: 10,
    fontSize: 18,
    "&:hover": {
      opacity: 0.8,
      backgroundColor: "transparent",
    }
  },
};
