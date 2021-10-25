import React, { useState, useMemo } from "react";
import { useQueryState } from "react-router-use-location-state";
import { Drawer, Divider } from "@material-ui/core";

import {
  Tabs,
  Tab,
  NavLink,
  SecondaryButton,
  LinearProgress,
  Heading,
} from "../../components";

import { makeStyles } from "@material-ui/core/styles";
import { useRouteMatch } from "react-router-dom";
import TaskDetails from "./TaskDetails";
import ExecutionSummary from "./ExecutionSummary";
import ExecutionJson from "./ExecutionJson";
import InputOutput from "./ExecutionInputOutput";
import { colors } from "../../theme/variables";
import clsx from "clsx";
import ActionModule from "./ActionModule";
import IconButton from "@material-ui/core/IconButton";
import CloseIcon from "@material-ui/icons/Close";
import RightPanel from "./RightPanel";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import StatusBadge from "../../components/StatusBadge";
import { useFetch } from "../../utils/query";
import { Helmet } from "react-helmet";

const drawerWidth = 650;

const useStyles = makeStyles({
  wrapper: {
    height: "100%",
    width: "100%",
    display: "flex",
    flexDirection: "row",
  },
  drawer: {
    width: drawerWidth,
  },
  drawerPaper: {
    height: "100vh",
    width: drawerWidth,
    overflowY: "hidden",
  },
  drawerHeader: {
    display: "flex",
    alignItems: "center",
    padding: 10,
    // necessary for content to be below app bar
    //...theme.mixins.toolbar,
    justifyContent: "flex-end",
  },
  drawerContent: {
    overflowY: "scroll",
    flexGrow: 1,
  },
  content: {
    flexGrow: 1,
    paddingBottom: 50,
    /*transition: theme.transitions.create("margin", {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    */
    marginRight: -drawerWidth,
    overflowY: "scroll",
  },
  contentShift: {
    /*
    transition: theme.transitions.create("margin", {
      easing: theme.transitions.easing.easeOut,
      duration: theme.transitions.duration.enteringScreen,
    }),
    */
    marginRight: 0,
  },

  header: {
    backgroundColor: colors.gray14,
    paddingLeft: 50,
    paddingTop: 20,
    "@media (min-width: 1920px)": {
      paddingLeft: 200,
    },
  },
  headerSubtitle: {
    marginBottom: 20,
  },
  tabContent: {
    paddingTop: 20,
    paddingRight: 50,
    paddingLeft: 50,
    "@media (min-width: 1920px)": {
      paddingLeft: 200,
    },
  },
  fr: {
    display: "flex",
    position: "relative",
    zIndex: 9999,
    float: "right",
    marginRight: 50,
    marginTop: 10,
  },
  frItem: {
    display: "flex",
    alignItems: "center",
    marginRight: 15,
  },
});

export default function Execution() {
  const classes = useStyles();
  const match = useRouteMatch();
  const url = `/workflow/${match.params.id}`;

  const { data: execution, isFetching, refetch: refresh } = useFetch(url);

  const [tabIndex, setTabIndex] = useQueryState("tabIndex", 0);
  const [selectedTask, setSelectedTask] = useState(null);  
  
  const handleSelectedTask = (task) => {
    if(task){
      const { taskToDomain } = execution;
      let domain;
      if(taskToDomain['*']){
        domain = taskToDomain['*'];
      }
      else if(task.taskType){
        domain = taskToDomain[task.taskType];
      }
      
      setSelectedTask({
        ...task,
        domain: domain
      });
    }
    else {
      setSelectedTask(null);
    }
  }

  const dag = useMemo(
    () => (execution ? new WorkflowDAG(execution) : null),
    [execution]
  );

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Execution - {match.params.id}</title>
      </Helmet>
      <div
        className={clsx(classes.content, {
          [classes.contentShift]: !!selectedTask,
        })}
      >
        {isFetching && <LinearProgress />}
        {execution && (
          <>
            <div className={classes.header}>
              <div className={classes.fr}>
                {execution.parentWorkflowId && (
                  <div className={classes.frItem}>
                    <NavLink
                      newTab
                      path={`/execution/${execution.parentWorkflowId}`}
                    >
                      Parent Workflow
                    </NavLink>
                  </div>
                )}
                <SecondaryButton onClick={refresh} style={{ marginRight: 10 }}>
                  Refresh
                </SecondaryButton>
                <ActionModule execution={execution} triggerReload={refresh} />
              </div>
              <Heading level={3} gutterBottom>
                {execution.workflowType || execution.workflowName}{" "}
                <StatusBadge status={execution.status} />
              </Heading>
              <Heading level={0} className={classes.headerSubtitle}>
                {execution.workflowId}
              </Heading>
              <Tabs value={tabIndex} style={{ marginBottom: 0 }}>
                <Tab label="Tasks" onClick={() => setTabIndex(0)} />
                <Tab label="Summary" onClick={() => setTabIndex(1)} />
                <Tab
                  label="Workflow Input/Output"
                  onClick={() => setTabIndex(2)}
                />
                <Tab label="JSON" onClick={() => setTabIndex(3)} />
              </Tabs>
            </div>
            <div className={classes.tabContent}>
              {tabIndex === 0 && (
                <TaskDetails
                  dag={dag}
                  execution={execution}
                  setSelectedTask={handleSelectedTask}
                  selectedTask={selectedTask}
                />
              )}
              {tabIndex === 1 && <ExecutionSummary execution={execution} />}
              {tabIndex === 2 && <InputOutput execution={execution} />}
              {tabIndex === 3 && <ExecutionJson execution={execution} />}
            </div>
          </>
        )}
      </div>
      <Drawer
        className={classes.drawer}
        variant="persistent"
        anchor="right"
        open={!!selectedTask}
        classes={{
          root: classes.drawer,
          paper: classes.drawerPaper,
        }}
      >
        <div className={classes.drawerHeader}>
          <IconButton onClick={() => handleSelectedTask(null)}>
            <CloseIcon />
          </IconButton>
        </div>
        <Divider />
        <RightPanel
          className={classes.drawerContent}
          selectedTask={selectedTask}
          dag={dag}
          onTaskChange={handleSelectedTask}
        />
      </Drawer>
    </div>
  );
}
