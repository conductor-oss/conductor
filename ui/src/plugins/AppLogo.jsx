import React from "react";
import { makeStyles } from "@material-ui/core/styles";

const useStyles = makeStyles((theme) => ({
  logo: {
    height: 55,
    marginRight: 30,
  },
}));

export default function AppLogo() {
  const classes = useStyles();
  return <img src="/logo.png" alt="Conductor" className={classes.logo} />;
}
