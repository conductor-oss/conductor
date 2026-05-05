import { colors } from "theme/tokens/variables";

export default {
  wrapper: {
    overflowY: "auto",
    overflowX: "hidden",
    height: "100%",
    width: "100%",
    display: "contents",
    justifyContent: "flexStart",
    backgroundColor: colors.gray14,
  },
  fullWidth: {
    width: "100%",
    height: "100%",
    overflowY: "scroll",
    backgroundColor: "white",
    paddingBottom: "400px",
  },
  padded: {
    padding: "20px",
  },
  header: {
    backgroundColor: colors.gray14,
    paddingLeft: "50px",
    paddingTop: "20px",
    "@media (min-width: 1920px)": {
      paddingLeft: "50px",
    },
  },
  tabContent: {
    marginTop: "10px",
    paddingTop: "20px",
    paddingRight: "20px",
    paddingBottom: "50px",
    paddingLeft: "20px",
    "@media (min-width: 1920px)": {
      paddingLeft: "50px",
    },
  },
  gridFlex: {
    display: "flex",
    margin: 0,
    padding: 0,
    overflow: "auto",
    width: "100%",
    flexWrap: "nowrap",
    alignItems: "stretch",
    justifyContent: "space-between",
    minWidth: "900px",
  },
  fixedDisplayHeader: {
    backgroundColor: colors.gray14,
    paddingLeft: "50px",
    paddingTop: "20px",
    "@media (min-width: 1920px)": {
      paddingLeft: "50px",
    },
    overflowY: "scroll",
    overflowX: "hidden",
    display: "block",
    justifyContent: "flexStart",
    position: "sticky",
    left: 0,
    top: 0,
    zIndex: 10,
  },
  tabContentScroll: {
    paddingTop: 0,
    paddingRight: "20px",
    paddingBottom: "50px",
    paddingLeft: "20px",
    "@media (min-width: 1920px)": {
      paddingLeft: "50px",
    },
    overflowY: "scroll",
    overflowX: "hidden",
  },
  paperMargin: {
    marginBottom: "30px",
  },
  iconButton: {
    color: "black",
    opacity: 0.3,
    paddingRight: "10px",
    fontSize: 18,
    "&:hover": {
      opacity: 0.8,
      backgroundColor: "transparent",
    },
  },
  editorLabel: {
    color: "black",
    opacity: 0.8,
    paddingLeft: "10px",
    fontSize: "12px",
    lineHeight: 3,
    fontWeight: "400",
    "& span": {
      fontSize: "13px",
      fontWeight: "bold",
    },
    "& svg": {
      fontSize: "18px",
    },
  },
  chipContainer: {
    display: "flex",
    flexWrap: "wrap",
    "& > *": {
      margin: "2px 5px 2px 0",
    },
  },
  resizer: {
    width: "10px",
    margin: "-5px",
    cursor: "col-resize",
    backgroundColor: "rgb(45, 45, 45, 0.05)",
    zIndex: 1,
    flexShrink: 0,
    resize: "horizontal",
    "&:hover": {
      backgroundColor: "rgb(45, 45, 45, 0.3)",
    },
  },
  workflowDefFirstRowMenu: {
    width: "100%",
    display: "flex",
    flexFlow: "row",
    justifyContent: "space-around",
    paddingTop: 3,
    paddingBottom: 3,
    alignItems: "center",
    // position: "sticky",
    // top: 0,
    // left: 0,
  },
  definitionEditorSecondRowMenu: {
    width: "100%",
    display: "flex",
    flexFlow: "row",
    justifyContent: "space-around",
    paddingTop: "3px",
    paddingBottom: "6px",
    borderBottom: "solid var(--backgroundLight) 2px",
    alignItems: "center",
    position: "sticky",
    top: 0,
    left: 0,
  },
  popover: {
    "& .MuiPopover-paper": {
      padding: "8px",
      backgroundColor: "rgb(45, 45, 45, 0.6)",
      color: "white",
    },
    "& .MuiTypography-root": {
      fontSize: "14px",
    },
  },
  deleteIcon: {
    "& svg": {
      color: "#cd5c5c",
      fontSize: "14px",
    },
  },
};
