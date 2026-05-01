import React from "react";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  container: {
    padding: 30,
    textAlign: "center",
  },
  title: {
    fontSize: 20,
    fontWeight: 600,
    marginBottom: 12,
    color: "#333",
  },
  message: {
    fontSize: 14,
    color: "#666",
    marginBottom: 8,
    lineHeight: 1.6,
  },
  code: {
    display: "inline-block",
    background: "#f5f5f5",
    border: "1px solid #ddd",
    borderRadius: 4,
    padding: "2px 8px",
    fontFamily: "monospace",
    fontSize: 13,
  },
});

export default function SchedulerDisabledBanner() {
  const classes = useStyles();
  return (
    <div className={classes.container}>
      <div className={classes.title}>Scheduler is not enabled</div>
      <div className={classes.message}>
        To enable the scheduler, set{" "}
        <span className={classes.code}>conductor.scheduler.enabled=true</span>{" "}
        in your application properties and restart the server.
      </div>
    </div>
  );
}
