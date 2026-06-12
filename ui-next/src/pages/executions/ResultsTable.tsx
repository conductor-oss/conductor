import { ReactNode, useEffect, useState } from "react";
import { DataTable, NavLink, Paper, Text } from "components";
import { LinearProgress } from "@mui/material";
import BulkActionModule from "./BulkActionModule";
import executionsStyles from "./executionsStyles";
import StatusBadge from "components/StatusBadge";
import { calculateTimeFromMillis, totalPages } from "utils/utils";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import NoDataComponent from "components/ui/NoDataComponent";
import { colors } from "theme/tokens/variables";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { WORKFLOW_EXPLORER_URL } from "utils/constants/route";

const LinearIndeterminate = () => {
  return (
    <div style={{ width: "100%" }} id="linear-indeterminate-progress">
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
    minWidth: "120px",
    sortable: true,
    tooltip: "The time the workflow was started.",
  },
  {
    id: "workflowId",
    name: "workflowId",
    label: "Workflow Id",
    grow: 2,
    renderer: (workflowId) => (
      <NavLink path={`/execution/${workflowId}`}>{workflowId}</NavLink>
    ),
    sortable: true,
    tooltip: "The unique identifier for the workflow execution.",
  },
  {
    id: "workflowType",
    name: "workflowType",
    label: "Workflow Name",
    grow: 2,
    sortable: true,
    tooltip: "The name of the workflow.",
  },
  {
    id: "version",
    name: "version",
    label: "Version",
    grow: 0.5,
    sortable: false,
    tooltip: "The version of the workflow.",
  },
  {
    id: "correlationId",
    name: "correlationId",
    label: "Correlation Id",
    grow: 2,
    sortable: false,
    tooltip: "The correlation id for the workflow.",
  },
  {
    id: "idempotencyKey",
    name: "idempotencyKey",
    label: "Idempotency Key",
    grow: 2,
    sortable: false,
    tooltip: "The idempotency key for the workflow.",
  },
  {
    id: "updateTime",
    name: "updateTime",
    label: "Updated Time",
    type: ColumnCustomType.DATE,
    sortable: true,
    tooltip: "The time the workflow was last updated.",
  },
  {
    id: "endTime",
    name: "endTime",
    label: "End Time",
    type: ColumnCustomType.DATE,
    minWidth: "120px",
    sortable: true,
    tooltip: "The time the workflow was completed.",
  },
  {
    id: "status",
    name: "status",
    label: "Status",
    sortable: true,
    minWidth: "150px",
    renderer: (status) => <StatusBadge status={status} />,
    tooltip: "The status of the workflow.",
  },
  {
    id: "input",
    name: "input",
    label: "Input",
    grow: 2,
    wrap: true,
    sortable: false,
    tooltip: "The input for the workflow.",
  },
  {
    id: "output",
    name: "output",
    label: "Output",
    grow: 2,
    sortable: false,
    tooltip: "The output for the workflow.",
  },
  {
    id: "reasonForIncompletion",
    name: "reasonForIncompletion",
    label: "Reason For Incompletion",
    sortable: false,
    tooltip: "The reason the workflow was not completed.",
  },
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
    tooltip: "The time it took to execute the workflow.",
  },
  {
    id: "event",
    name: "event",
    label: "Event",
    sortable: false,
    tooltip: "The event that triggered this workflow.",
  },
  {
    id: "failedReferenceTaskNames",
    name: "failedReferenceTaskNames",
    label: "Failed Ref Task Names",
    grow: 2,
    sortable: false,
    tooltip: "The names of the reference tasks that failed.",
  },
  {
    id: "externalInputPayloadStoragePath",
    name: "externalInputPayloadStoragePath",
    label: "External Input Payload Storage Path",
    sortable: false,
    tooltip: "The storage path for the external input payload.",
  },
  {
    id: "externalOutputPayloadStoragePath",
    name: "externalOutputPayloadStoragePath",
    label: "External Output Payload Storage Path",
    sortable: false,
    tooltip: "The storage path for the external output payload.",
  },
  {
    id: "priority",
    name: "priority",
    label: "Priority",
    sortable: false,
    tooltip: "The priority of the workflow.",
  },

  {
    id: "createdBy",
    name: "createdBy",
    label: "Created By",
    sortable: false,
    tooltip: "The user who created the workflow.",
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
  title?: ReactNode;
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
    // @ts-ignore
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
          "workflowType",
          "workflowId",
          "endTime",
          "status",
        ]}
        localStorageKey="workflowSearchExecutions"
        keyField="workflowId"
        useGlobalRowsPerPage={false}
        paginationServer
        paginationDefaultPage={page}
        paginationPerPage={rowsPerPage}
        paginationTotalRows={totalCount}
        onChangeRowsPerPage={setRowsPerPage ? setRowsPerPage : undefined}
        onChangePage={(page) => setPage(page)}
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
              description="Here you’ll see any executed workflows, regardless
              of its status. Let’s define a new workflow!"
              buttonText="BROWSE TEMPLATES"
              buttonHandler={handleClickBrowseTemplates}
            />
          )
        }
      />
    </Paper>
  );
}
