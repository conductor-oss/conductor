import React, { useMemo, useReducer, useEffect, useCallback } from "react";
import { useQueryState } from "react-router-use-location-state";
import Alert from "@material-ui/lab/Alert";
import update from "immutability-helper";
import {
  Tabs,
  Tab,
  NavLink,
  SecondaryButton,
  LinearProgress,
  Heading,
} from "../../components";
import { Tooltip } from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";
import { useRouteMatch } from "react-router-dom";
import TaskDetails from "./TaskDetails";
import ExecutionSummary from "./ExecutionSummary";
import ExecutionJson from "./ExecutionJson";
import InputOutput from "./ExecutionInputOutput";
import clsx from "clsx";
import ActionModule from "./ActionModule";
import IconButton from "@material-ui/core/IconButton";
import CloseIcon from "@material-ui/icons/Close";
import FullscreenIcon from "@material-ui/icons/Fullscreen";
import FullscreenExitIcon from "@material-ui/icons/FullscreenExit";
import RightPanel from "./RightPanel";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import StatusBadge from "../../components/StatusBadge";
import { useFetch } from "../../utils/query";
import { Helmet } from "react-helmet";
import sharedStyles from "../styles";

const maxWindowWidth = window.innerWidth;
const INIT_DRAWER_WIDTH = 650;

const useStyles = makeStyles({
  header: sharedStyles.header,
  tabContent: sharedStyles.tabContent,

  wrapper: {
    height: "100%",
  },
  drawer: {
    zIndex: 999,
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    width: (state) => (state.isFullWidth ? "100%" : state.drawerWidth),
  },
  drawerHeader: {
    display: "flex",
    alignItems: "center",
    padding: 10,
    justifyContent: "flex-end",
    height: 80,
    flexShrink: 0,
    boxShadow: "0 4px 8px 0 rgb(0 0 0 / 10%), 0 0 2px 0 rgb(0 0 0 / 10%)",
    zIndex: 1,
    backgroundColor: "#fff",
  },
  dragger: {
    display: (state) => (state.isFullWidth ? "none" : "block"),
    width: "5px",
    cursor: "ew-resize",
    padding: "4px 0 0",
    position: "absolute",
    height: "100%",
    zIndex: "100",
    backgroundColor: "#f4f7f9",
  },
  drawerMain: {
    paddingLeft: (state) => (state.isFullWidth ? 0 : 4),
    height: "100%",
    display: "flex",
    flexDirection: "column",
  },
  drawerContent: {
    flex: "1 1 auto",
    overflowY: "scroll",
    backgroundColor: "#fff",
    position: "relative",
  },
  content: {
    overflowY: "scroll",
    height: "100%",
  },
  contentShift: {
    marginRight: (state) => state.drawerWidth,
  },
  headerSubtitle: {
    marginBottom: 20,
  },

  fr: {
    display: "flex",
    position: "relative",
    float: "right",
    marginRight: 50,
    marginTop: 10,
    zIndex: 1,
  },
  frItem: {
    display: "flex",
    alignItems: "center",
    marginRight: 15,
  },
});

const actions = {
  EXPAND_FULL: 1,
  RESET_EXPAND_FULL: 2,
  MOUSE_DOWN: 3,
  MOUSE_UP: 4,
  MOUSE_MOVE: 5,
  CLOSE: 6,
  SELECT_TASK: 7,
};

const initialDrawerState = {
  drawerWidth: INIT_DRAWER_WIDTH,
  isFullWidth: false,
  isResizing: false,
  selectedTask: null,
};

function drawerReducer(state, action) {
  switch (action.type) {
    case actions.EXPAND_FULL:
      return update(state, {
        isFullWidth: {
          $set: true,
        },
      });
    case actions.RESET_EXPAND_FULL:
      return update(state, {
        isFullWidth: {
          $set: false,
        },
      });
    case actions.MOUSE_DOWN:
      return update(state, {
        isResizing: {
          $set: true,
        },
      });
    case actions.MOUSE_UP:
      return update(state, {
        isResizing: {
          $set: false,
        },
      });
    case actions.MOUSE_MOVE:
      return update(state, {
        drawerWidth: {
          $set: action.offsetRight,
        },
      });
    case actions.CLOSE:
      return update(state, {
        selectedTask: {
          $set: null,
        },
      });
    case actions.SELECT_TASK:
      return update(state, {
        selectedTask: {
          $set: action.selectedTask,
        },
      });
    default:
      return state;
  }
}

export default function Execution() {
  const [drawerState, dispatch] = useReducer(drawerReducer, initialDrawerState);
  const selectedTask = drawerState.selectedTask;

  const classes = useStyles(drawerState);
  const match = useRouteMatch();
  const url = `/workflow/${match.params.id}`;

  const { data: execution, isFetching, refetch: refresh } = useFetch(url);

  const [tabIndex, setTabIndex] = useQueryState("tabIndex", 0);

  const handleMousedown = (e) => {
    dispatch({ type: actions.MOUSE_DOWN, clientX: e.clientX });
  };

  const handleMousemove = useCallback(
    (e) => {
      // we don't want to do anything if we aren't resizing.
      if (!drawerState.isResizing) {
        return;
      }

      // Stop highlighting
      e.preventDefault();
      const offsetRight =
        document.body.offsetWidth - (e.clientX - document.body.offsetLeft);
      const minWidth = 0;
      const maxWidth = maxWindowWidth - 100;
      if (offsetRight > minWidth && offsetRight < maxWidth) {
        dispatch({ type: actions.MOUSE_MOVE, offsetRight });
      }
    },
    [drawerState.isResizing]
  );

  const handleMouseup = (e) => {
    dispatch({ type: actions.MOUSE_UP });
  };

  const handleSelectTask = (task) => {
    dispatch({ type: actions.SELECT_TASK, selectedTask: task });
  };

  const handleClose = () => {
    dispatch({ type: actions.CLOSE });
  };

  const handleFullScreen = () => {
    dispatch({ type: actions.EXPAND_FULL });
  };

  const handleFullScreenExit = () => {
    dispatch({ type: actions.RESET_EXPAND_FULL });
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

              {execution.reasonForIncompletion && (
                <Alert severity="error">
                  {execution.reasonForIncompletion}
                </Alert>
              )}

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
                  setSelectedTask={handleSelectTask}
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
      {selectedTask && (
        <div className={classes.drawer}>
          <div
            id="dragger"
            onMouseDown={(event) => handleMousedown(event)}
            className={classes.dragger}
          />
          <div className={classes.drawerMain}>
            <div className={classes.drawerHeader}>
              {drawerState.isFullWidth ? (
                <Tooltip title="Restore sidebar">
                  <IconButton onClick={() => handleFullScreenExit()}>
                    <FullscreenExitIcon />
                  </IconButton>
                </Tooltip>
              ) : (
                <Tooltip title="Maximize sidebar">
                  <IconButton onClick={() => handleFullScreen()}>
                    <FullscreenIcon />
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title="Close sidebar">
                <IconButton onClick={() => handleClose()}>
                  <CloseIcon />
                </IconButton>
              </Tooltip>
            </div>
            <div className={classes.drawerContent}>
              <RightPanel
                selectedTask={selectedTask}
                dag={dag}
                onTaskChange={handleSelectTask}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
