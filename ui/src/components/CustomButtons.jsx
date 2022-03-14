import Button from "@material-ui/core/Button";
import { styled } from "@material-ui/core";

export const fontFamilyList = [
  "-apple-system",
  "BlinkMacSystemFont",
  '"Segoe UI"',
  "Roboto",
  '"Helvetica Neue"',
  "Arial",
  "sans-serif",
  '"Apple Color Emoji"',
  '"Segoe UI Emoji"',
  '"Segoe UI Symbol"',
].join(",");

const hoverCss = {
  backgroundColor: "#857aff",
  borderColor: "#857aff",
  boxShadow: "none",
  "&> .MuiButton-label": {
    color: "white",
  },
};

const buttonBaseStyle = {
  boxShadow: "none",
  textTransform: "none",
  fontSize: 16,
  padding: "6px 12px",
  border: "1px solid",
  lineHeight: 1.3,
  color: "#ffffff",
  backgroundColor: "#6558F5",
  borderColor: "#6558F5",
  fontFamily: fontFamilyList,
  "&:hover": hoverCss,
  "&:active": hoverCss,
  "&:focus": {
    boxShadow: "0 0 0 0.2rem rgba(0,123,255,.5)",
  },
  "&> .MuiButton-label": {
    color: "#ffffff",
  },
};

export const BootstrapButton = styled(Button)(buttonBaseStyle);

const outlineHoverCss = {
  ...hoverCss,
  "&> .MuiButton-label": {
    color: "ghostwhite",
  },
};

const actionHoverCss = {
  ...hoverCss,
  backgroundColor: "#30499f",
  borderColor: "#30499f",
};

export const BootstrapOutlineButton = styled(Button)({
  ...buttonBaseStyle,
  color: "#ffffff",
  backgroundColor: "ghostwhite",
  borderColor: "#6558F5",
  "&> .MuiButton-label": {
    color: "#6558F5",
  },
  "&:hover": outlineHoverCss,
  "&:active": outlineHoverCss,
});

export const BootstrapOutlineActionButton = styled(Button)({
  ...buttonBaseStyle,
  color: "#ffffff",
  backgroundColor: "ghostwhite",
  borderColor: "#30499f",
  "&> .MuiButton-label": {
    color: "#30499f",
  },
  "&:hover": actionHoverCss,
  "&:active": actionHoverCss,
});

export const BootstrapTextButton = styled(Button)({
  ...buttonBaseStyle,
  color: "#ffffff",
  backgroundColor: "ghostwhite",
  borderColor: "transparent",
  "&> .MuiButton-label": {
    color: "#6558F5",
  },
  "&:hover": outlineHoverCss,
  "&:active": outlineHoverCss,
});

export const BootstrapActionButton = styled(Button)({
  ...buttonBaseStyle,
  fontSize: 14,
  lineHeight: 1.5,
  backgroundColor: "#4969e4",
  borderColor: "#4969e4",
  "&> .MuiButton-label": {
    color: "#ffffff",
  },
  "&:hover": actionHoverCss,
  "&:active": actionHoverCss,
});
