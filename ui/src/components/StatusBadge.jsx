import React from "react";
import { Chip } from "@material-ui/core";

const colorMap = {
  success: "#41b957",
  progress: "#1f83db",
  error: "#e50914",
  warning: "#fba404",
};

export default function StatusBadge({ status, ...props }) {
  let color;
  switch (status) {
    case "RUNNING":
      color = colorMap.progress;
      break;
    case "COMPLETED":
      color = colorMap.success;
      break;
    case "PAUSED":
      color = colorMap.warning;
      break;
    default:
      color = colorMap.error;
  }

  return (
    <Chip
      {...props}
      style={color && { backgroundColor: color, color: "white" }}
      label={status}
    />
  );
}
