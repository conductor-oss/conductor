import sharedStyles from "pages/styles";

// @ts-ignore-line
export const style = {
  ...sharedStyles,
  paper: {
    margin: "20px",
    padding: "20px",
  },
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
  },
  fields: {
    display: "flex",
    flexDirection: "column",
    gap: "15px",
  },
  controls: {
    marginLeft: "15px",
    marginTop: "20px",
    height: "calc(100% - 83px)",
    overflowY: "scroll",
    width: "calc(100% - 15px)",
    overflowX: "hidden",
    paddingBottom: "60px",
  },
  monaco: {
    padding: "10px",
    borderColor: "rgba(128, 128, 128, 0.2)",
    borderStyle: "solid",
    borderWidth: "1px",
    borderRadius: "4px",
    backgroundColor: "rgb(255, 255, 255)",
    "&:focus-within": {
      margin: "-2px",
      borderColor: "rgb(73, 105, 228)",
      borderStyle: "solid",
      borderWidth: "2px",
    },
  },
  labelText: {
    position: "relative",
    fontSize: "13px",
    transform: "none",
    fontWeight: 600,
    paddingLeft: 0,
    paddingBottom: "8px",
  },
  inputBox: {
    marginTop: "10px",
    "& textarea": {
      minWidth: "368px",
      fontFamily: "monospace",
    },
    "& input": {
      minWidth: "368px",
    },
    "& label": {},
  },
  roBox: {
    marginTop: "10px",
    "& .MuiOutlinedInput-root": {
      background: "transparent",
      border: "none",
    },
    "& fieldset": {
      border: "none",
    },
    "& textarea": {
      minWidth: "450px",
      minHeight: "140px",
      fontFamily: "monospace",
      overflow: "none",
    },
    "& input": {
      minWidth: "368px",
    },
    "& label": {},
  },
  cronApply: {
    marginTop: "-12px",
    "& svg": {
      fontSize: "18px",
    },
  },
  toggleButton: {
    marginTop: "-12px",
    "& svg": {
      fontSize: "22px",
    },
  },
  cronSample: {
    fontSize: "12px",
    height: "50px",
  },
};
