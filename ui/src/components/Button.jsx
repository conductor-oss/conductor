import { Button as MuiButton } from "@material-ui/core";

export default function Button({ variant = "primary", ...props }) {
  if (variant === "secondary") {
    return <MuiButton color="secondary" variant="outlined" {...props} />;
  } else {
    // primary or invalid
    return <MuiButton color="primary" variant="contained" {...props} />;
  }
}
