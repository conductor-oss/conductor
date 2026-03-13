export const modalStyles = {
  dialog: {
    ".MuiPaper-root": {
      width: " 420px",
      "& input:-webkit-autofill, & input:-webkit-autofill:hover, & input:-webkit-autofill:focus, & input:-webkit-autofill:active":
        {
          WebkitBoxShadow: "0 0 0px 1000px white inset",
        },
      ".MuiAlert-root": {
        width: "100%",
      },
    },
  },
  title: {
    background: "transparent",
    borderBottom: "none",
    display: "flex",
    gap: 2,
    position: "relative",
    left: "-10px",
  },
  closeIcon: {
    position: "absolute",
    right: 10,
    cursor: "pointer",
    border: "2px solid",
    borderRadius: "50%",
    color: "rgba(175, 175, 175, 1)",
  },
  groupDescInput: {
    minHeight: "96px",
    marginTop: "-10px",
    "& textArea": { padding: 0, border: "none" },
    "& .MuiOutlinedInput-notchedOutline": { border: "none" },
    "& p": { margin: "0px 12px 4px" },
  },
};
