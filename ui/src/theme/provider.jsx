import React from "react";
import { MuiThemeProvider } from "@material-ui/core/styles";

import { theme } from "./";

export const Provider = ({ children, ...rest }) => {
  return (
    <MuiThemeProvider theme={theme} {...rest}>
      {children}
    </MuiThemeProvider>
  );
};
