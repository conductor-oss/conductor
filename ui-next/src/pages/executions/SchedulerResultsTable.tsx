import { useEffect, useState } from "react";
import { DataTable, LinearProgress, NavLink, Paper, Text } from "components";
import { AlertTitle } from "@mui/material";
import BulkActionModule from "./BulkActionModule";
import executionsStyles from "./executionsStyles";
import ClipboardCopy from "components/ui/ClipboardCopy";
import StatusBadge from "components/StatusBadge";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { totalPages } from "utils/index";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import MuiAlert from "components/ui/MuiAlert";
import NoDataComponent from "components/ui/NoDataComponent";
import { colors } from "theme/tokens/variables";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { SCHEDULER_DEFINITION_URL } from "utils/constants/route";
import { useAuth } from "components/features/auth";

const executionFields: LegacyColumn[] = [
  {
    id: "scheduledTime",
    name: "scheduledTime",
    type: ColumnCustomType.DATE,
    label: "Scheduled time",
    grow: 0.7,
    sortable: true,
    tooltip: "The time the workflow was scheduled to run.",
  },
  {
    id: "executionTime",
    name: "executionTime",
    type: ColumnCustomType.DATE,
    label: "Execution time",
    grow: 0.7,
    sortable: true,
    tooltip: "The time the workflow was executed.",
  },
  {
    id: "executionId",
    name: "executionId",
    label: "Execution id",
    grow: 1.5,
    sortable: true,
    renderer: (executionId) => (
      <ClipboardCopy value={executionId}>{executionId}</ClipboardCopy>
    ),
    tooltip: "The unique identifier for the scheduler execution.",
  },
  {
    id: "scheduleName",
    name: "scheduleName",
    label: "Schedule name",
    grow: 0.7,
    sortable: true,
    tooltip: "The name of the schedule.",
  },
  {
    id: "workflowName",
    name: "workflowName",
    label: "Workflow name",
    grow: 0.7,
    sortable: true,
    tooltip: "The name of the workflow.",
  },
  {
    id: "workflowId",
    name: "workflowId",
    label: "Workflow id",
    grow: 1.5,
    sortable: true,
    renderer: (workflowId) => {
      if (!workflowId) {
        return "";
      }
      return (
        <ClipboardCopy value={workflowId}>
          <NavLink path={`/execution/${workflowId}`}>{workflowId}</NavLink>
        </ClipboardCopy>
      );
    },
    tooltip: "The unique identifier for the workflow execution.",
  },
  {
    id: "state",
    name: "state",
    label: "Status",
    grow: 0.5,
    sortable: false,
    renderer: (state) => <StatusBadge status={state} />,
    tooltip: "The status of the execution.",
  },
  {
    id: "reason",
    name: "reason",
    label: "Reason for failure",
    sortable: true,
    tooltip: "The reason the execution failed.",
  },
  {
    id: "stackTrace",
    name: "stackTrace",
    label: "Error details",
    sortable: true,
    tooltip: "The error details.",
  },
];

export interface SchedulerResultsTableProps {
  resultObj: any;
  error: any;
  busy?: boolean;
  page: number;
  rowsPerPage: number;
  setPage: (page: number) => void;
  setSort: (id: string, direction: string) => void;
  setRowsPerPage?: (rowsPerPage: number) => void;
  refetchExecution: () => void;
  errorMessage: any;
  handleError: (error: any) => void;
  handleClearError: () => void;
  isFilterOn: boolean;
  handleReset: () => void;
}

export default function SchedulerResultsTable({
  resultObj,
  error,
  busy,
  page,
  rowsPerPage,
  setPage,
  setSort,
  setRowsPerPage,
  refetchExecution,
  errorMessage,
  handleError,
  handleClearError,
  isFilterOn,
  handleReset,
}: SchedulerResultsTableProps) {
  const { isTrialExpired } = useAuth();
  const [selectedRows, setSelectedRows] = useState<any[]>([]);
  const [toggleCleared, setToggleCleared] = useState(false);
  const pushHistory = usePushHistory();

  const getErrorMessage = (error: any) => {
    return error?.message || error?.statusText;
  };

  useEffect(() => {
    setSelectedRows([]);
    setToggleCleared((t) => !t);
  }, [resultObj]);

  const handleClickDefineSchedule = () => {
    pushHistory(SCHEDULER_DEFINITION_URL.NEW);
  };

  return (
    // @ts-ignore
    <Paper sx={executionsStyles.paper} variant="outlined">
      {busy && <LinearProgress />}
      {error && (
        <MuiAlert severity="error">
          <AlertTitle>Request Failed</AlertTitle>
          {getErrorMessage(error)}
        </MuiAlert>
      )}
      {errorMessage && (
        <SnackbarMessage
          message={getErrorMessage(errorMessage)}
          severity="error"
          onDismiss={handleClearError}
        />
      )}
      {!resultObj && !error && (
        <Text sx={executionsStyles.clickSearch}>
          Click "Search" to submit query.
        </Text>
      )}
      {resultObj && (
        <DataTable
          title={`Page ${page} of ${totalPages(
            page,
            rowsPerPage.toString(),
            resultObj?.results?.length,
          )}`}
          data={resultObj.results}
          columns={executionFields}
          defaultShowColumns={[
            "executionId",
            "scheduleName",
            "workflowName",
            "scheduledTime",
            "executionTime",
            "workflowId",
            "state",
            "reason",
          ]}
          useGlobalRowsPerPage={false}
          localStorageKey="schedulerExecutionsTable"
          keyField="executionId"
          paginationServer
          paginationDefaultPage={page}
          paginationPerPage={rowsPerPage}
          onChangeRowsPerPage={setRowsPerPage ? setRowsPerPage : undefined}
          onChangePage={(page) => setPage(page)}
          sortServer
          defaultSortFieldId="scheduledTime"
          defaultSortAsc={false}
          onSort={(column, sortDirection) => {
            setSort(column.id as string, sortDirection);
          }}
          selectableRows
          paginationTotalRows={resultObj?.totalHits}
          contextComponent={
            <BulkActionModule
              selectedRows={selectedRows}
              refetchExecution={refetchExecution}
              handleError={handleError}
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
            isFilterOn ? (
              <NoDataComponent
                title="Empty"
                titleBg={colors.warningTag}
                description="I'm sorry that search didn't find any matches. Please try different filters."
                buttonText="Clear search"
                buttonHandler={handleReset}
              />
            ) : (
              <NoDataComponent
                title="Empty"
                titleBg={colors.warningTag}
                description="Here you’ll see any executed scheduled workflows, regardless of its status.Let’s define a new schedule and automate!"
                buttonText="Define a Schedule"
                buttonHandler={handleClickDefineSchedule}
                disableButton={isTrialExpired}
              />
            )
          }
        />
      )}
    </Paper>
  );
}
