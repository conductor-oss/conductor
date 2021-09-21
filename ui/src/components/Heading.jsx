import React from "react";
import Typography from "@material-ui/core/Typography";

const levelMap = ["h6", "h5", "h4", "h3", "h2", "h1"];

export default function ({ level = 3, ...props }) {
  return <Typography variant={levelMap[level]} {...props} />;
}
