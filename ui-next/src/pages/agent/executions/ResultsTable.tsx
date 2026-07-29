import { agentFetch } from "utils/agentFetch";
import { ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import { DataTable, NavLink, Paper, Text } from "components";
import {
  Box,
  FormControlLabel,
  LinearProgress,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableRow,
} from "@mui/material";
import BulkActionModule from "./BulkActionModule";
import executionsStyles from "./executionsStyles";
import StatusBadge from "components/StatusBadge";
import { calculateTimeFromMillis, totalPages } from "utils/utils";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import NoDataComponent from "components/ui/NoDataComponent";
import { colors } from "theme/tokens/variables";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";

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
    tooltip: "The time the execution was started.",
  },
  {
    id: "workflowId",
    name: "workflowId",
    label: "Execution Id",
    grow: 2,
    renderer: (workflowId) => (
      <NavLink path={`${AGENT_EXECUTIONS_URL.BASE}/${workflowId}`}>
        {workflowId}
      </NavLink>
    ),
    sortable: true,
    tooltip: "The unique identifier for the execution.",
  },
  {
    id: "workflowType",
    name: "workflowType",
    label: "Agent Name",
    grow: 2,
    sortable: true,
    tooltip: "The name of the agent.",
    renderer: (value: string, row: any) => {
      if (row._isSubAgent) {
        return (
          <span style={{ color: "#888", paddingLeft: 16 }}>
            ↳ {value}{" "}
            <span style={{ fontSize: 11, color: "#aaa" }}>sub-agent</span>
          </span>
        );
      }
      return value;
    },
  },
  {
    id: "version",
    name: "version",
    label: "Version",
    grow: 0.5,
    sortable: false,
    tooltip: "The version of the agent.",
  },
  {
    id: "correlationId",
    name: "correlationId",
    label: "Correlation Id",
    grow: 2,
    sortable: false,
    tooltip: "The correlation id for the execution.",
  },
  {
    id: "idempotencyKey",
    name: "idempotencyKey",
    label: "Idempotency Key",
    grow: 2,
    sortable: false,
    tooltip: "The idempotency key for the execution.",
  },
  {
    id: "updateTime",
    name: "updateTime",
    label: "Updated Time",
    type: ColumnCustomType.DATE,
    sortable: true,
    tooltip: "The time the execution was last updated.",
  },
  {
    id: "endTime",
    name: "endTime",
    label: "End Time",
    type: ColumnCustomType.DATE,
    minWidth: "120px",
    sortable: true,
    tooltip: "The time the execution was completed.",
  },
  {
    id: "status",
    name: "status",
    label: "Status",
    sortable: true,
    minWidth: "150px",
    renderer: (status) => <StatusBadge status={status} />,
    tooltip: "The status of the execution.",
  },
  {
    id: "input",
    name: "input",
    label: "Input",
    grow: 2,
    wrap: true,
    sortable: false,
    tooltip: "The input for the execution.",
  },
  {
    id: "output",
    name: "output",
    label: "Output",
    grow: 2,
    sortable: false,
    tooltip: "The output for the execution.",
  },
  {
    id: "reasonForIncompletion",
    name: "reasonForIncompletion",
    label: "Reason For Incompletion",
    sortable: false,
    tooltip: "The reason the execution was not completed.",
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
    tooltip: "The time it took to execute the agent.",
  },
  {
    id: "event",
    name: "event",
    label: "Event",
    sortable: false,
    tooltip: "The event that triggered this execution.",
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
    tooltip: "The priority of the execution.",
  },

  {
    id: "createdBy",
    name: "createdBy",
    label: "Created By",
    sortable: false,
    tooltip: "The user who created the execution.",
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
  hideSubWorkflows: boolean;
  setHideSubWorkflows: (value: boolean) => void;
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
  hideSubWorkflows,
  setHideSubWorkflows,
}: ResultsTableProps) {
  const [selectedRows, setSelectedRows] = useState<string[]>([]);
  const [toggleCleared, setToggleCleared] = useState(false);
  const [agentNames, setAgentNames] = useState<Set<string>>(new Set());

  // Cache for sub-agent lookups
  const [subAgentCache, setSubAgentCache] = useState<Record<string, any[]>>({});

  const fetchSubAgents = useCallback(
    async (workflowId: string) => {
      if (subAgentCache[workflowId]) return;
      try {
        const res = await agentFetch(`/api/workflow/${workflowId}`);
        const data = await res.json();
        const subs = (data.tasks || [])
          .filter((t: any) => t.subWorkflowId)
          .map((t: any) => ({
            referenceTaskName: t.referenceTaskName,
            subWorkflowId: t.subWorkflowId,
            status: t.status,
          }));
        setSubAgentCache((prev) => ({ ...prev, [workflowId]: subs }));
      } catch {
        setSubAgentCache((prev) => ({ ...prev, [workflowId]: [] }));
      }
    },
    [subAgentCache],
  );

  // Fetch registered agent names to identify top-level agents
  useEffect(() => {
    agentFetch("/api/agent/list")
      .then((res) => res.json())
      .then((agents: any[]) => {
        setAgentNames(new Set(agents.map((a: any) => a.name)));
      })
      .catch(() => {
        setAgentNames(new Set());
      });
  }, []);

  // Sub-agent filtering is done server-side (topLevelOnly -> parentWorkflowId = "").
  // Here we only mark sub-agents for visual distinction when showing all rows.
  const filteredResults = useMemo(() => {
    if (!resultObj?.results) return [];
    if (hideSubWorkflows || agentNames.size === 0) return resultObj.results;
    return resultObj.results.map((row: any) => ({
      ...row,
      _isSubAgent: !agentNames.has(row.workflowType),
    }));
  }, [resultObj?.results, hideSubWorkflows, agentNames]);

  const getErrorMessage = (error: any) => {
    return error?.message || error?.statusText;
  };

  useEffect(() => {
    setSelectedRows([]);
    setToggleCleared((t) => !t);
  }, [resultObj]);

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
      {resultObj?.results && (
        <FormControlLabel
          control={
            <Switch
              checked={hideSubWorkflows}
              onChange={(e) => setHideSubWorkflows(e.target.checked)}
              size="small"
            />
          }
          label="Hide sub-agent executions"
          sx={{ ml: 1, mb: 1, mt: 3 }}
        />
      )}
      <DataTable
        expandableRows={!hideSubWorkflows}
        expandOnRowClicked={!hideSubWorkflows}
        expandableRowsComponent={({ data: row }: { data: any }) => {
          const wfId = row.workflowId;
          if (!subAgentCache[wfId]) {
            fetchSubAgents(wfId);
            return (
              <Box sx={{ pl: 6, py: 1, color: "text.secondary", fontSize: 13 }}>
                Loading sub-agents...
              </Box>
            );
          }
          const subs = subAgentCache[wfId];
          if (subs.length === 0) {
            return (
              <Box sx={{ pl: 6, py: 1, color: "text.secondary", fontSize: 13 }}>
                No sub-agents
              </Box>
            );
          }
          return (
            <Table
              size="small"
              sx={{
                ml: 4,
                width: "auto",
                "& td": { border: 0, py: 0.5, fontSize: 13 },
              }}
            >
              <TableBody>
                {subs.map((sub: any) => {
                  const name = sub.referenceTaskName
                    .replace(/^.*?step_\d+_/, "")
                    .replace(/_coerce$/, "");
                  return (
                    <TableRow key={sub.subWorkflowId}>
                      <TableCell sx={{ color: "text.secondary", width: 24 }}>
                        └
                      </TableCell>
                      <TableCell>
                        <NavLink
                          path={`${AGENT_EXECUTIONS_URL.BASE}/${sub.subWorkflowId}`}
                        >
                          {name}
                        </NavLink>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={sub.status} />
                      </TableCell>
                      <TableCell
                        sx={{
                          color: "text.secondary",
                          fontFamily: "monospace",
                          fontSize: 12,
                        }}
                      >
                        {sub.subWorkflowId}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          );
        }}
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
        data={filteredResults}
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
        conditionalRowStyles={[
          {
            when: (row: any) => row._isSubAgent,
            style: { backgroundColor: "#f9f9f9", opacity: 0.8 },
          },
        ]}
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
              description="No executions found."
            />
          )
        }
      />
    </Paper>
  );
}
