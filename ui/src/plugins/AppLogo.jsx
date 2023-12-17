import React from "react";
import { makeStyles } from "@material-ui/core/styles";
import { getBasename } from "../utils/helpers";
import { cleanDuplicateSlash } from "./fetch";

const useStyles = makeStyles((theme) => ({
  logo: {
    height: 37,
    width: 175,
    marginRight: 30,
  },
}));

export default function AppLogo() {
  const classes = useStyles();
  const logoPath = getBasename() + 'logo.svg';
  return <img src={cleanDuplicateSlash(logoPath)} alt="Conductor" className={classes.logo} />;
}
