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

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const executionFields = [
  { name: "startTime", type: "date" },
  {
    name: "workflowId",
    grow: 2,
    renderer: (workflowId) => (
      <NavLink path={`/execution/${workflowId}`}>{workflowId}</NavLink>
    ),
  },
  { name: "workflowType", grow: 2 },
  { name: "version", grow: 0.5 },
  { name: "correlationId", grow: 2 },
  { name: "updateTime", type: "date" },
  { name: "endTime", type: "date" },
  { name: "status" },
  { name: "input", grow: 2, wrap: true },
  { name: "output", grow: 2 },
  { name: "reasonForIncompletion" },
  { name: "executionTime" },
  { name: "event" },
  { name: "failedReferenceTaskNames", grow: 2 },
  { name: "externalInputPayloadStoragePath" },
  { name: "externalOutputPayloadStoragePath" },
  { name: "priority" },
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
            "startTime",
            "workflowType",
            "workflowId",
            "endTime",
            "status",
          ]}
          localStorageKey="executionsTable"
          keyField="workflowId"
          paginationServer
          paginationTotalRows={totalHits}
          paginationDefaultPage={page}
          paginationPerPage={rowsPerPage}
          onChangeRowsPerPage={(rowsPerPage) => setRowsPerPage(rowsPerPage)}
          onChangePage={(page) => setPage(page)}
          sortServer
          defaultSortField="startTime"
          defaultSortAsc={false}
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
