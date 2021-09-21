import React from "react";

import { Route, Switch } from "react-router-dom";
import { makeStyles } from "@material-ui/core/styles";
import { Button, AppBar, Toolbar } from "@material-ui/core";
import AppLogo from "./plugins/AppLogo";
import NavLink from "./components/NavLink";

import WorkflowSearch from "./pages/executions/WorkflowSearch";
import TaskSearch from "./pages/executions/TaskSearch";

import Execution from "./pages/execution/Execution";
import WorkflowDefinitions from "./pages/definitions/Workflow";
import WorkflowDefinition from "./pages/definition/Workflow";
import TaskDefinitions from "./pages/definitions/Task";
import TaskDefinition from "./pages/definition/Task";
import EventHandlerDefinitions from "./pages/definitions/EventHandler";
import EventHandlerDefinition from "./pages/definition/EventHandler";
import TaskQueue from "./pages/misc/TaskQueue";
import KitchenSink from "./pages/kitchensink/KitchenSink";
import DiagramTest from "./pages/kitchensink/DiagramTest";
import Examples from "./pages/kitchensink/Examples";
import Gantt from "./pages/kitchensink/Gantt";

import AppBarModules from "./plugins/AppBarModules";

const useStyles = makeStyles((theme) => ({
  root: {
    backgroundColor: "#efefef", // TODO: Use theme var
    display: "flex",
  },
  body: {
    width: "100vw",
    height: "100vh",
    paddingTop: theme.mixins.toolbar.minHeight,
  },
  toolbarRight: {
    marginLeft: "auto",
    display: "flex",
    flexDirection: "row",
  },
}));

export default function App() {
  const classes = useStyles();

  return (
    // Provide context for backward compatibility with class components
    <div className={classes.root}>
      <AppBar position="fixed">
        <Toolbar>
          <AppLogo />
          <Button component={NavLink} path="/">
            Executions
          </Button>
          <Button component={NavLink} path="/workflowDef">
            Definitions
          </Button>
          <Button component={NavLink} path="/taskQueue">
            Task Queues
          </Button>

          <div className={classes.toolbarRight}>
            <AppBarModules />
          </div>
        </Toolbar>
      </AppBar>
      <div className={classes.body}>
        <Switch>
          <Route exact path="/">
            <WorkflowSearch />
          </Route>
          <Route exact path="/search/by-tasks">
            <TaskSearch />
          </Route>
          <Route path="/execution/:id/:taskId?">
            <Execution />
          </Route>
          <Route exact path="/workflowDef">
            <WorkflowDefinitions />
          </Route>
          <Route exact path="/workflowDef/:name/:version?">
            <WorkflowDefinition />
          </Route>
          <Route exact path="/taskDef">
            <TaskDefinitions />
          </Route>
          <Route exact path="/taskDef/:name">
            <TaskDefinition />
          </Route>
          <Route exact path="/eventHandlerDef">
            <EventHandlerDefinitions />
          </Route>
          <Route exact path="/eventHandlerDef/:name">
            <EventHandlerDefinition />
          </Route>
          <Route exact path="/taskQueue/:name?">
            <TaskQueue />
          </Route>
          <Route exact path="/kitchen">
            <KitchenSink />
          </Route>
          <Route exact path="/kitchen/diagram">
            <DiagramTest />
          </Route>
          <Route exact path="/kitchen/examples">
            <Examples />
          </Route>
          <Route exact path="/kitchen/gantt">
            <Gantt />
          </Route>
        </Switch>
      </div>
    </div>
  );
}
