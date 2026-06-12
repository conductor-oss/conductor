import { useImperativeHandle, useState, forwardRef } from "react";
import { useLocalStorage } from "../../utils/localstorage";
import { Text } from "../../components";
import {
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Toolbar,
  IconButton,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import { immutableReplaceAt } from "../../utils/helpers";
import { formatRelative } from "date-fns";
import DeleteIcon from "@material-ui/icons/DeleteForever";
import { colors } from "../../theme/variables";
import CloseIcon from "@material-ui/icons/Close";
import _ from "lodash";
import { useEnv } from "../../plugins/env";

const useStyles = makeStyles({
  sidebar: {
    width: 300,
    border: "0px solid rgba(0, 0, 0, 0)",
    zIndex: 1,
    boxShadow: "0 2px 4px 0 rgb(0 0 0 / 10%), 0 0 2px 0 rgb(0 0 0 / 10%)",
    background: "#fff",
    display: "flex",
    flexDirection: "column",
  },
  toolbar: {
    backgroundColor: colors.gray14,
  },
  title: {
    fontWeight: "bold",
    flex: 1,
  },
  list: {
    overflowY: "auto",
    cursor: "pointer",
    flex: 1,
  },
});
type RunPayload = any;
type RunEntry = {
  runPayload: RunPayload;
  workflowRecords: WorkflowRecord[];
  createTime: number;
};
type WorkflowRecord = {
  workflowId: string;
};

type RunHistoryProps = {
  onRunSelected: (run: RunEntry | undefined) => void;
};

const RUN_HISTORY_SCHEMA_VER = 1;

const RunHistory = forwardRef((props: RunHistoryProps, ref) => {
  const { onRunSelected } = props;
  const { stack } = useEnv();
  const classes = useStyles();
  const [selectedCreateTime, setSelectedCreateTime] = useState<
    number | undefined
  >(undefined);
  const [runHistory, setRunHistory]: readonly [
    RunEntry[],
    (v: RunEntry[]) => void
  ] = useLocalStorage(`runHistory_${stack}_${RUN_HISTORY_SCHEMA_VER}`, []);

  useImperativeHandle(ref, () => ({
    pushNewRun: (runPayload: RunPayload) => {
      const createTime = new Date().getTime();
      const newRun = {
        runPayload: runPayload,
        workflowRecords: [],
        createTime: createTime,
      };
      setRunHistory([newRun, ...runHistory]);
      setSelectedCreateTime(createTime);

      return newRun;
    },
    updateRun: (createTime: number, workflowId: string) => {
      const idx = runHistory.findIndex((v) => v.createTime === createTime);
      const currRun = runHistory[idx];
      const oldRecords = currRun.workflowRecords;
      const updatedRun = {
        runPayload: currRun.runPayload,
        workflowRecords: [
          {
            workflowId: workflowId,
          },
          ...oldRecords,
        ],
        createTime: currRun.createTime,
      };

      setRunHistory(immutableReplaceAt(runHistory, idx, updatedRun));
      onRunSelected(updatedRun);
    },
  }));

  function handleSelectRun(run: RunEntry) {
    if (onRunSelected) onRunSelected(run);
    setSelectedCreateTime(run.createTime);
  }

  function handleDeleteAll() {
    if (window.confirm("Delete all run history in this browser?")) {
      setRunHistory([]);
    }
  }

  function handleDeleteItem(run: RunEntry) {
    const newHistory = runHistory.filter(
      (v) => v.createTime !== run.createTime
    );
    if (newHistory.length > 0) {
      setSelectedCreateTime(newHistory[0].createTime);
      onRunSelected(newHistory[0]);
    } else {
      console.log("Empty history");
      setSelectedCreateTime(undefined);
      onRunSelected(undefined);
    }
    setRunHistory(newHistory);
  }

  return (
    <div className={classes.sidebar}>
      <Toolbar className={classes.toolbar}>
        <Text level={0} className={classes.title}>
          Run History
        </Text>
        <IconButton onClick={handleDeleteAll}>
          <DeleteIcon />
        </IconButton>
      </Toolbar>
      <List className={classes.list}>
        {runHistory.map((run) => (
          <ListItem
            key={run.createTime}
            selected={selectedCreateTime === run.createTime}
            onClick={() => handleSelectRun(run)}
          >
            <ListItemText
              primary={run.runPayload.name}
              secondary={formatRelative(new Date(run.createTime), new Date())}
            />
            <ListItemSecondaryAction>
              <IconButton edge="end" onClick={() => handleDeleteItem(run)}>
                <CloseIcon />
              </IconButton>
            </ListItemSecondaryAction>
          </ListItem>
        ))}
        {_.isEmpty(runHistory) && <ListItem>No saved runs.</ListItem>}
      </List>
    </div>
  );
});

export default RunHistory;
