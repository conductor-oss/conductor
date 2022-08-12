import { MuiThemeProvider } from "@material-ui/core/styles";

import theme from "./theme";

export default ({ children, ...rest }) => {
  return (
    <MuiThemeProvider theme={theme} {...rest}>
      {children}
    </MuiThemeProvider>
  );
};
