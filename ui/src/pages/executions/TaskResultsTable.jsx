import React, { useState, useRef, useEffect } from "react";
import {
  Paper,
  NavLink,
  DataTable,
  LinearProgress,
  TertiaryButton,
  Text,
} from "../../components";
import { Alert, AlertTitle } from "@material-ui/lab";
import { makeStyles } from "@material-ui/styles";
import BulkActionModule from "./BulkActionModule";
import executionsStyles from "./executionsStyles";
import sharedStyles from "../styles";
import TaskLink from "../../components/TaskLink";
import { SEARCH_TASK_TYPES_SET } from "../../utils/constants";

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const executionFields = [
  { name: "updateTime", label: "Update Time", type: "date" },
  { name: "scheduledTime", label: "Scheduled Time", type: "date" },
  { name: "startTime", label: "Start Time", type: "date" },
  { name: "endTime", label: "End Time", type: "date" },
  {
    name: "taskId",
    label: "Task ID",
    grow: 1.5,
    renderer: (taskId, row) => (
      <TaskLink taskId={taskId} workflowId={row.workflowId} />
    ),
  },
  {
    name: "taskDefName",
    label: "Task Name",
    grow: 1.5,
    renderer: (taskDefName) =>
      SEARCH_TASK_TYPES_SET.has(taskDefName) ? "-" : taskDefName,
  },
  {
    name: "taskType",
    label: "Task Type",
    grow: 0.6,
    sortable: false,
    renderer: (taskType) =>
      SEARCH_TASK_TYPES_SET.has(taskType) ? taskType : "SIMPLE",
  },
  {
    name: "workflowId",
    label: "Workflow ID",
    grow: 2,
    renderer: (workflowId) => (
      <NavLink path={`/execution/${workflowId}`}>{workflowId}</NavLink>
    ),
  },
  { name: "workflowType", label: "Workflow Name", grow: 1.5 },
  {
    name: "executionTime",
    label: "Execution Time",
    grow: 0.6,
    sortable: false,
  },
  {
    name: "queueWaitTime",
    label: "Queue Wait Time",
    grow: 0.6,
    sortable: false,
  },
  {
    name: "workflowPriority",
    label: "Workflow Priority",
    grow: 0.6,
    sortable: false,
  },
  {
    name: "status",
    label: "Status",
    sortable: false,
  },
  { name: "input", label: "Input", grow: 3, sortable: false, wrap: true },
  { name: "output", label: "Output", grow: 3, sortable: false, wrap: true },
  {
    name: "reasonForIncompletion",
    label: "Reason for Incompletion",
    grow: 3,
    sortable: false,
    wrap: true,
  },
];

function ShowMore({
  rowsPerPage,
  rowCount,
  onChangePage,
  onChangeRowsPerPage,
  currentPage,
}) {
  return (
    <div style={{ textAlign: "center", padding: 15 }}>
      <TertiaryButton onClick={() => onChangePage(currentPage + 1)}>
        Show More Results
      </TertiaryButton>
    </div>
  );
}

export default function ResultsTable({
  resultObj,
  error,
  busy,
  page,
  rowsPerPage,
  sort,
  setPage,
  setSort,
  setRowsPerPage,
  showMore,
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
  const [selectedRows, setSelectedRows] = useState([]);
  const [toggleCleared, setToggleCleared] = useState(false);
  const tableRef = useRef(null);

  const defaultSortField = sort ? sort.split(":")[0] : null;
  const defaultSortDirection = sort ? sort.split(":")[1] : null;

  useEffect(() => {
    setSelectedRows([]);
    setToggleCleared((t) => !t);
  }, [resultObj]);

  return (
    <Paper className={classes.paper}>
      {busy && <LinearProgress />}
      {error && (
        <Alert severity="error">
          <AlertTitle>Query Failed</AlertTitle>
          {error.message}
        </Alert>
      )}
      {!resultObj && !error && (
        <Text className={classes.clickSearch}>
          Click "Search" to submit query.
        </Text>
      )}
      {resultObj && (
        <DataTable
          title={totalHits > 0 && ` Page ${page} of ${totalHits}`}
          data={resultObj.results}
          columns={executionFields}
          defaultShowColumns={[
            "updateTime",
            "taskId",
            "taskDefName",
            "workflowType",
            "executionType",
            "taskType",
            "status",
          ]}
          localStorageKey="taskResultsTable"
          keyField="taskId"
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
          selectableRows
          contextComponent={
            <BulkActionModule
              selectedRows={selectedRows}
              popperAnchorEl={tableRef.current}
            />
          }
          onSelectedRowsChange={({ selectedRows }) =>
            setSelectedRows(selectedRows)
          }
          clearSelectedRows={toggleCleared}
          customStyles={{
            header: {
              style: {
                overflow: "visible",
              },
            },
            contextMenu: {
              style: {
                display: "none",
              },
              activeStyle: {
                display: "flex",
              },
            },
          }}
          paginationComponent={showMore ? ShowMore : null}
        />
      )}
    </Paper>
  );
}
