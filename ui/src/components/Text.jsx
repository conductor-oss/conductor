import React from "react";
import Typography from "@material-ui/core/Typography";

const levelMap = ["caption", "body2", "body1"];

export default function ({ level = 1, ...props }) {
  return <Typography variant={levelMap[level]} {...props} />;
}
