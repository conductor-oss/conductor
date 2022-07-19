import {
  List,
  ListItem,
  ListItemText,
  Toolbar,
  IconButton,
} from "@material-ui/core";
import { StatusBadge, Text, NavLink } from "../../components";
import { makeStyles } from "@material-ui/styles";
import { colors } from "../../theme/variables";
import _ from "lodash";
import { useInvalidateWorkflows, useWorkflowsByIds } from "../../data/workflow";
import { formatRelative } from "date-fns";
import RefreshIcon from "@material-ui/icons/Refresh";

const useStyles = makeStyles({
  sidebar: {
    width: 360,
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
  list: {
    overflowY: "auto",
    flex: 1,
  },
});

export default function ExecutionHistory({ run }) {
  const classes = useStyles();
  const workflowRecords = run ? run.workflowRecords : [];
  const workflowIds = workflowRecords.map((record) => `${record.workflowId}`);
  const results =
    useWorkflowsByIds(workflowIds, {
      staleTime: 60000,
    }) || [];
  const resultsMap = new Map(
    results
      .filter((r) => r.isSuccess)
      .map((result) => [result.data.workflowId, result.data])
  );
  const invalidateWorkflows = useInvalidateWorkflows();

  function handleRefresh() {
    invalidateWorkflows(workflowIds);
  }

  return (
    <div className={classes.sidebar}>
      <Toolbar className={classes.toolbar}>
        <Text level={0} className={classes.title}>
          Execution History
        </Text>
        <IconButton onClick={handleRefresh}>
          <RefreshIcon />
        </IconButton>
      </Toolbar>
      <List className={classes.list}>
        {Array.from(resultsMap.values()).map((workflow) => (
          <ListItem key={workflow.workflowId}>
            <ListItemText
              primary={
                <NavLink path={`/execution/${workflow.workflowId}`} newTab>
                  {workflow.workflowId}
                </NavLink>
              }
              secondary={
                <span>
                  <StatusBadge status={workflow.status} size="small" />{" "}
                  {formatRelative(new Date(workflow.startTime), new Date())}
                </span>
              }
              secondaryTypographyProps={{ component: "div" }}
            />
          </ListItem>
        ))}
        {_.isEmpty(workflowRecords) && (
          <ListItem>
            <ListItemText>No execution history.</ListItemText>
          </ListItem>
        )}
      </List>
    </div>
  );
}
