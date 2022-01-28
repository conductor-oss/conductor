import React from "react";
import { Tab, Tabs, NavLink, LinearProgress, Heading } from "../../components";
import { makeStyles } from "@material-ui/styles";
import sharedStyles from "../styles";

const useStyles = makeStyles(sharedStyles);

export default function Header({ tabIndex, loading }) {
  const classes = useStyles();

  return (
    <div>
      {loading && <LinearProgress />}
      <div className={classes.header}>
        <Heading level={3} gutterBottom>
          Definitions
        </Heading>
        <Tabs value={tabIndex}>
          <Tab label="Workflows" component={NavLink} path="/workflowDef" />
          <Tab label="Tasks" component={NavLink} path="/taskDef" />
          <Tab
            label="Event Handlers"
            component={NavLink}
            path="/eventHandlerDef"
          />
        </Tabs>
      </div>
    </div>
  );
}
