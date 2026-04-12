import { LinearProgress } from "@mui/material";
import { ReactNode, useEffect, useState } from "react";

import { DataTable, NavLink, Paper, Text } from "components";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import { usePushHistory } from "utils/hooks/usePushHistory";
import NoDataComponent from "components/ui/NoDataComponent";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import StatusBadge from "components/StatusBadge";
import { colors } from "theme/tokens/variables";
import {
  WORKFLOW_DEFINITION_URL,
  WORKFLOW_EXECUTION_URL,
  WORKFLOW_EXPLORER_URL,
} from "utils/constants/route";
import { calculateTimeFromMillis, totalPages } from "utils/utils";
import BulkActionModule from "./BulkActionModule";
import executionsStyles from "./executionsStyles";
import { toMaybeQueryString } from "utils/toMaybeQueryString";

const LinearIndeterminate = () => {
  return (
    <div style={{ width: "100%" }}>
      <LinearProgress />
    </div>
  );
};

const executionFields: LegacyColumn[] = [
  {
    id: "startTime",
    name: "startTime",
    type: ColumnCustomType.DATE,
    label: "Start Time",
    sortable: true,
    minWidth: "120px",
  },
  {
    id: "endTime",
    name: "endTime",
    label: "End Time",
    type: ColumnCustomType.DATE,
    minWidth: "120px",
    sortable: true,
  },
  {
    id: "taskId",
    name: "taskId",
    label: "Task execution Id",
    grow: 2,
    renderer: (taskId, row) => {
      const urlParameters = {
        taskId: row.taskId,
      };
      return (
        <NavLink
          path={`${WORKFLOW_EXECUTION_URL.BASE}/${
            row.workflowId
          }${toMaybeQueryString(urlParameters)}`}
        >
          {taskId}
        </NavLink>
      );
    },
    sortable: true,
  },
  {
    id: "taskDefName",
    name: "taskDefName",
    label: "Task name",
    sortable: false,
  },
  {
    id: "scheduledTime",
    name: "scheduledTime",
    label: "Scheduled time",
    type: ColumnCustomType.DATE,
    minWidth: "120px",
    sortable: true,
  },
  {
    id: "taskType",
    name: "taskType",
    label: "Task Type",
    sortable: true,
  },
  {
    id: "taskReferenceName",
    name: "taskReferenceName",
    label: "Task Reference Name",
    sortable: true,
  },
  {
    id: "workflowType",
    name: "workflowType",
    label: "Workflow Name",
    sortable: true,
    grow: 2,
    renderer: (workflowName) => (
      <NavLink
        path={`${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(
          workflowName,
        )}`}
      >
        {workflowName}
      </NavLink>
    ),
  },
  {
    id: "updateTime",
    name: "updateTime",
    label: "Updated Time",
    type: ColumnCustomType.DATE,
    sortable: true,
  },

  {
    id: "status",
    name: "status",
    label: "Status",
    sortable: true,
    minWidth: "150px",
    renderer: (status) => <StatusBadge status={status} />,
  },
  {
    id: "input",
    name: "input",
    label: "Input",
    grow: 2,
    wrap: true,
    sortable: false,
  },
  { id: "output", name: "output", label: "Output", grow: 2, sortable: false },
  {
    id: "executionTime",
    name: "executionTime",
    label: "Execution Time",
    renderer: (time) => {
      if (time < 1000) {
        return `${time} ms`;
      } else {
        return calculateTimeFromMillis(Math.floor(time / 1000));
      }
    },
    sortable: false,
  },
  {
    id: "workflowId",
    name: "workflowId",
    label: "Workflow Id",
    renderer: (executionId) => (
      <NavLink path={`${WORKFLOW_EXECUTION_URL.BASE}/${executionId}`}>
        {executionId}
      </NavLink>
    ),
    sortable: true,
  },
  {
    id: "workflowPriority",
    name: "workflowPriority",
    label: "Workflow priority",
    sortable: false,
  },
  {
    id: "correlationId",
    name: "correlationId",
    label: "Correlation id",
    sortable: false,
  },
  {
    id: "reasonForIncompletion",
    name: "reasonForIncompletion",
    label: "Reason for Incompletion",
    sortable: false,
  },
  {
    id: "queueWaitTime",
    name: "queueWaitTime",
    label: "Queue Wait Time",
    sortable: false,
  },
  {
    id: "externalInputPayloadStoragePath",
    name: "externalInputPayloadStoragePath",
    label: "External Input Payload Storage Path",
    sortable: false,
  },
  {
    id: "externalOutputPayloadStoragePath",
    name: "externalOutputPayloadStoragePath",
    label: "External Output Payload Storage Path",
    sortable: false,
  },
];

export interface ResultsTableProps {
  resultObj: any;
  error?: any;
  busy?: boolean;
  page: number;
  rowsPerPage: number;
  setPage: (page: number) => void;
  setSort: (id: string, direction: string) => void;
  setRowsPerPage?: (rowsPerPage: number) => void;
  showMore?: boolean;
  title?: string | ReactNode;
  refetchExecution: () => void;
  handleError?: (error: any) => void;
  handleClearError?: () => void;
  filterOn: boolean;
  handleReset: () => void;
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
  title,
  refetchExecution,
  handleError,
  handleClearError,
  filterOn,
  handleReset,
}: ResultsTableProps) {
  const [selectedRows, setSelectedRows] = useState<string[]>([]);
  const [toggleCleared, setToggleCleared] = useState(false);
  const pushHistory = usePushHistory();

  const getErrorMessage = (error: any) => {
    return error?.message || error?.statusText;
  };

  useEffect(() => {
    setSelectedRows([]);
    setToggleCleared((t) => !t);
  }, [resultObj]);

  const handleClickBrowseTemplates = () => {
    pushHistory(WORKFLOW_EXPLORER_URL);
  };
  const handleClickClearSearch = () => {
    handleReset();
  };

  const totalCount = resultObj?.totalHits ?? resultObj?.results?.length;

  return (
    <Paper sx={executionsStyles.paper} variant="outlined">
      {error && (
        <SnackbarMessage
          message={getErrorMessage(error)}
          severity="error"
          onDismiss={handleClearError}
        />
      )}
      {!resultObj && !error && (
        <Text sx={executionsStyles.clickSearch}>
          Click "Search" to submit query.
        </Text>
      )}

      <DataTable
        progressComponent={<LinearIndeterminate />}
        progressPending={busy}
        pagination
        paginationServer
        paginationTotalRows={totalCount}
        title={
          title ||
          ` Page ${page} of ${totalPages(
            page,
            rowsPerPage.toString(),
            resultObj?.results?.length,
          )}`
        }
        data={resultObj?.results ? resultObj?.results : []}
        columns={executionFields}
        defaultShowColumns={[
          "startTime",
          "endTime",
          "taskId",
          "taskType",
          "scheduledTime",
          "workflowType",
          "status",
        ]}
        localStorageKey="taskSearchExecutions"
        keyField="taskId" // paginationServer
        useGlobalRowsPerPage={false}
        paginationDefaultPage={page}
        paginationPerPage={rowsPerPage}
        onChangeRowsPerPage={setRowsPerPage}
        onChangePage={(page) => setPage(page)}
        hideSearch
        sortServer
        defaultSortAsc={false}
        onSort={(column, sortDirection) => {
          if (column.id) {
            setSort(column.id as string, sortDirection);
          }
        }}
        selectableRows
        contextComponent={
          <BulkActionModule
            selectedRows={selectedRows}
            refetchExecution={refetchExecution}
            handleError={handleError!}
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
        noDataComponent={
          filterOn ? (
            <NoDataComponent
              id="no-data-component-with-filters"
              title="Empty"
              titleBg={colors.warningTag}
              description="I'm sorry that search didn't find any matches. Please try different filters."
              buttonText="Clear search"
              buttonHandler={handleClickClearSearch}
            />
          ) : (
            <NoDataComponent
              id="no-data-component-without-filters"
              title="Empty"
              titleBg={colors.warningTag}
              description="Here you’ll see any executed tasks, regardless
              of its status. Let’s define a new task!"
              buttonText="BROWSE TEMPLATES"
              buttonHandler={handleClickBrowseTemplates}
            />
          )
        }
      />
    </Paper>
  );
}
