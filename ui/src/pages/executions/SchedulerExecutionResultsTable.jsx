import React from "react";
import {
  Paper,
  NavLink,
  DataTable,
  LinearProgress,
  Text,
} from "../../components";
import { Alert, AlertTitle } from "@material-ui/lab";
import { makeStyles } from "@material-ui/styles";
import executionsStyles from "./executionsStyles";
import sharedStyles from "../styles";

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const executionFields = [
  { name: "scheduledTime", label: "Scheduled Time", type: "date" },
  { name: "executionTime", label: "Execution Time", type: "date" },
  { name: "scheduleName", label: "Schedule Name", grow: 1.5 },
  { name: "workflowName", label: "Workflow Name", grow: 1.5 },
  {
    name: "workflowId",
    label: "Workflow ID",
    grow: 2,
    renderer: (workflowId) =>
      workflowId ? (
        <NavLink path={`/execution/${workflowId}`}>{workflowId}</NavLink>
      ) : (
        "-"
      ),
  },
  { name: "state", label: "State" },
  { name: "reason", label: "Reason", grow: 2, wrap: true },
  { name: "executionId", label: "Execution ID", grow: 2 },
];

export default function SchedulerExecutionResultsTable({
  resultObj,
  error,
  busy,
  page,
  rowsPerPage,
  sort,
  setPage,
  setSort,
  setRowsPerPage,
}) {
  const classes = useStyles();
  let totalHits = 0;
  if (resultObj) {
    if (resultObj.totalHits) {
      totalHits = resultObj.totalHits;
    } else {
      if (resultObj.results) {
        totalHits = resultObj.results.length;
      }
    }
  }

  const defaultSortField = sort ? sort.split(":")[0] : null;
  const defaultSortDirection = sort ? sort.split(":")[1] : null;

  return (
    <Paper className={classes.paper}>
      {busy && <LinearProgress />}
      {error && (
        <Alert severity="error">
          <AlertTitle>Query Failed</AlertTitle>
          {typeof error === "string" ? error : error.message}
        </Alert>
      )}
      {!resultObj && !error && (
        <Text className={classes.clickSearch}>
          Click "Search" to submit query.
        </Text>
      )}
      {resultObj && (
        <DataTable
          title={
            totalHits > 0 &&
            ` Page ${page} of ${Math.ceil(totalHits / rowsPerPage)}`
          }
          data={resultObj.results}
          columns={executionFields}
          defaultShowColumns={[
            "scheduledTime",
            "executionTime",
            "scheduleName",
            "workflowName",
            "workflowId",
            "state",
          ]}
          localStorageKey="schedulerExecutionResultsTable"
          keyField="executionId"
          paginationServer
          paginationTotalRows={totalHits}
          paginationDefaultPage={page}
          paginationPerPage={rowsPerPage}
          onChangeRowsPerPage={(rowsPerPage) => setRowsPerPage(rowsPerPage)}
          onChangePage={(page) => setPage(page)}
          sortServer
          defaultSortField={defaultSortField}
          defaultSortAsc={defaultSortDirection === "ASC"}
          onSort={(column, sortDirection) => {
            setSort(column.id, sortDirection);
          }}
        />
      )}
    </Paper>
  );
}
