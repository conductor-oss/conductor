import React from "react";
import { Alert, AlertTitle } from "@material-ui/lab";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  banner: {
    margin: 30,
  },
  code: {
    fontFamily: "monospace",
    backgroundColor: "#f5f5f5",
    padding: "2px 6px",
    borderRadius: 3,
  },
});

export function isSchedulerDisabled(error) {
  if (!error) return false;
  try {
    const parsed = typeof error === "string" ? JSON.parse(error) : error;
    return parsed.status === 404;
  } catch {
    return typeof error === "string" && error.includes("404");
  }
}

export default function SchedulerDisabledBanner() {
  const classes = useStyles();

  return (
    <Alert severity="info" className={classes.banner}>
      <AlertTitle>Scheduler is not enabled</AlertTitle>
      The scheduler module is not active on this Conductor server. To enable it,
      add the following property to your server configuration:
      <pre className={classes.code}>conductor.scheduler.enabled=true</pre>
      Then restart the Conductor server.
    </Alert>
  );
}
