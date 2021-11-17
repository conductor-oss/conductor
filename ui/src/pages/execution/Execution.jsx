import React, {
  useState,
  useMemo,
  useReducer,
  useEffect,
  useCallback,
} from "react";
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
import SettingsOverscanIcon from "@material-ui/icons/SettingsOverscan";
import RightPanel from "./RightPanel";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import StatusBadge from "../../components/StatusBadge";
import { useFetch } from "../../utils/query";
import { Helmet } from "react-helmet";

const maxWindowWidth = window.innerWidth;
const drawerWidth = maxWindowWidth > 1000 ? maxWindowWidth / 2 : 450;

const useStyles = makeStyles({
  wrapper: {
    height: "100%",
    width: "100%",
    display: "flex",
    flexDirection: "row",
  },
  drawer: {
    width: (state) => state.drawerWidth,
  },
  drawerPaper: {
    height: "100vh",
    width: (state) => state.drawerWidth,
    overflowY: "hidden",
  },
  drawerHeader: {
    display: "flex",
    alignItems: "center",
    padding: 10,
    // necessary for content to be below app bar
    //...theme.mixins.toolbar,
    justifyContent: "space-between",
  },
  drawerContent: {
    overflowY: "scroll",
    flexGrow: 1,
  },
  content: {
    flexGrow: (state) => (state.isResizing ? 0.8 : 1),
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
    float: "right",
    marginRight: 50,
    marginTop: 10,
  },
  frItem: {
    display: "flex",
    alignItems: "center",
    marginRight: 15,
  },
  dragger: {
    width: "5px",
    cursor: "ew-resize",
    padding: "4px 0 0",
    borderTop: "1px solid #ddd",
    position: "absolute",
    top: 0,
    left: 0,
    bottom: 0,
    zIndex: "100",
    backgroundColor: "#f4f7f9",
  },
});

const actions = {
  EXPAND_FULL: 1,
  RESET_EXPAND_FULL: 2,
  MOUSE_DOWN: 3,
  MOUSE_UP: 4,
  MOUSE_MOVE: 5,
};

const initialState = {
  drawerWidth: drawerWidth,
  isDrawerFullScreen: false,
  isResizing: false,
  lastDownX: 0,
  newWidth: {},
  drawerVarient: "persistent",
};

function reducer(state, action) {
  switch (action.type) {
    case actions.EXPAND_FULL:
      return { ...state, isDrawerFullScreen: true, drawerWidth: "100vw" };
    case actions.RESET_EXPAND_FULL:
      return {
        ...state,
        isDrawerFullScreen: false,
        drawerWidth: drawerWidth,
        drawerVarient: "persistent",
      };
    case actions.MOUSE_DOWN:
      return {
        ...state,
        isResizing: true,
        lastDownX: action.clientX,
        drawerVarient: "temporary",
      };
    case actions.MOUSE_UP:
      return {
        ...state,
        isResizing: false,
        drawerVarient:
          state.drawerWidth > maxWindowWidth / 2 ? "temporary" : "persistent",
      };
    case actions.MOUSE_MOVE:
      return {
        ...state,
        isDrawerFullScreen: false,
        drawerWidth: action.offsetRight,
      };
    default:
      return state;
  }
}

export default function Execution() {
  const [state, dispatch] = useReducer(reducer, initialState);

  const classes = useStyles(state);
  const match = useRouteMatch();
  const url = `/workflow/${match.params.id}`;

  const { data: execution, isFetching, refetch: refresh } = useFetch(url);

  const [tabIndex, setTabIndex] = useQueryState("tabIndex", 0);
  const [selectedTask, setSelectedTask] = useState(null);

  const handleMousedown = (e) => {
    dispatch({ type: actions.MOUSE_DOWN, clientX: e.clientX });
  };

  const handleMousemove = useCallback(
    (e) => {
      // we don't want to do anything if we aren't resizing.
      if (!state.isResizing) {
        return;
      }

      const offsetRight =
        document.body.offsetWidth - (e.clientX - document.body.offsetLeft);
      const minWidth = 0;
      const maxWidth = maxWindowWidth-100;
      if (offsetRight > minWidth && offsetRight < maxWidth) {
        dispatch({ type: actions.MOUSE_MOVE, offsetRight });
      }
    },
    [state.isResizing]
  );

  const handleMouseup = (e) => {
    dispatch({ type: actions.MOUSE_UP });
  };

  const handleSelectedTask = (task) => {
    setSelectedTask(task);
  };

  useEffect(() => {
    const mouseMove = (e) => handleMousemove(e);
    const mouseUp = (e) => handleMouseup(e);

    document.addEventListener("mousemove", mouseMove);
    document.addEventListener("mouseup", mouseUp);

    return () => {
      document.removeEventListener("mousemove", mouseMove);
      document.removeEventListener("mouseup", mouseUp);
    };
  }, [handleMousemove]);

  const handleDrawerMaximize = () => {
    if (state.isDrawerFullScreen) dispatch({ type: actions.RESET_EXPAND_FULL });
    else dispatch({ type: actions.EXPAND_FULL });
  };

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
        variant={state.drawerVarient}
        anchor="right"
        open={!!selectedTask}
        transitionDuration={0}
        classes={{
          root: classes.drawer,
          paper: classes.drawerPaper,
        }}
      >
        <div
          id="dragger"
          onMouseDown={(event) => handleMousedown(event)}
          className={classes.dragger}
        />
        <>
          <div className={classes.drawerHeader}>
            <IconButton onClick={() => handleDrawerMaximize()}>
              <SettingsOverscanIcon />
            </IconButton>
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
        </>
      </Drawer>
    </div>
  );
}
