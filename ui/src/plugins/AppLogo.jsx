import React from "react";
import { makeStyles } from "@material-ui/core/styles";
import { getBasename } from "../utils/helpers";
import { cleanDuplicateSlash } from "./fetch";

const useStyles = makeStyles((theme) => ({
  logo: {
    height: 37,
    marginTop: 2,
    marginLeft: 5,
    marginRight: 30,
    verticalAlign: "text-bottom",
    lineHeight: 3
  },
  boostLogo: {
      height: 37,
      width: 102,
    },
    container:{
      height: 37,
      display: "flex",
      marginTop: -8
    }
}));

export default function AppLogo() {
  const classes = useStyles();
  const boostLogoPath = getBasename() + 'Boost_Logo_.png';

  return <div className={classes.container}>
            <img src={cleanDuplicateSlash(boostLogoPath)} alt="Boost" className={classes.boostLogo} />
            <span className={classes.logo}>Conductor</span>
         </div>
}
