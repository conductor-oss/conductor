import { FormControlLabel, Switch } from "@mui/material";
import { Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import _isEmpty from "lodash/isEmpty";
import {
  ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useHotkeys } from "react-hotkeys-hook";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { Key } from "ts-key-enum";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { IObject } from "types/common";
import { dateToEpoch } from "utils";
import { ERROR_URL } from "utils/constants/route";
import { useWorkflowNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import { buildExecutionDateFilters } from "../executionDateFilters";
import ResultsTable from "../ResultsTable";
import {
  ExecutionSearchFilters,
  searchScopesFor,
  SearchScope,
  splitFreeText,
  splitList,
  useScopedSearch,
  exactClause,
} from "components/features/executionSearch";

const DEFAULT_SORT = "startTime:DESC";

const SEARCH_SCOPES = searchScopesFor("Workflow id");

export interface BasicSearchProps {
  doSearch: ({
    resultObj,
    queryFT,
    buildQuery,
    setQueryFT,
    refetch,
    setPage,
    setRecentTaskSearch,
  }: DoSearchProps) => void;
  SwitchComponent: ReactNode;
  getTableTitle: (resultObj: TaskExecutionResult) => ReactNode;
  freeText: string;
  setFreeText: (val: string) => void;
  status: string[];
  setStatus: (val: string[]) => void;
  startTimeFrom: string;
  setStartTimeFrom: (val: string) => void;
  onStartFromChange: (val: string) => void;
  startTimeTo: string;
  setStartTimeTo: (val: string) => void;
  onStartToChange: (val: string) => void;
  endTimeFrom: string;
  setEndTimeFrom: (val: string) => void;
  onEndFromChange: (val: string) => void;
  endTimeTo: string;
  setEndTimeTo: (val: string) => void;
  onEndToChange: (val: string) => void;
  fromDisplayTime: string;
  setFromDisplayTime: (val: string) => void;
  toDisplayTime: string;
  setToDisplayTime: (val: string) => void;
  /** Classifier filter passed to /workflow/search (e.g. "workflow" or "agent"). */
  classifier?: string;
  /** When set, results are scoped to this agent (adds a workflowType clause). */
  agentName?: string;
  /**
   * When set, renders a toggle with this label that excludes sub-executions
   * (those with a parentWorkflowId) — e.g. "Exclude sub-agents".
   */
  excludeSubLabel?: string;
}

export default function BasicSearch({
  doSearch,
  SwitchComponent,
  getTableTitle,
  freeText,
  setFreeText,
  status,
  setStatus,
  startTimeFrom,
  setStartTimeFrom,
  onStartFromChange,
  startTimeTo,
  setStartTimeTo,
  onStartToChange,
  endTimeFrom,
  setEndTimeFrom,
  onEndFromChange,
  endTimeTo,
  setEndTimeTo,
  onEndToChange,
  fromDisplayTime,
  setFromDisplayTime,
  toDisplayTime,
  setToDisplayTime,
  classifier = "workflow",
  agentName,
  excludeSubLabel,
}: BasicSearchProps) {
  const [page, setPage] = useQueryState("page", 1);
  const [workflowType, setWorkflowType] = useQueryState<string[]>(
    "workflowType",
    [],
  );
  const [workflowId, setWorkflowId] = useQueryState("workflowId", "");
  const [correlationIds, setCorrelationIds] = useQueryState<string[]>(
    "correlationIds",
    [],
  );
  const [idempotencyKey, setIdempotencyKey] = useQueryState<string[]>(
    "idempotencyKey",
    [],
  );
  const [excludeSubExecutions, setExcludeSubExecutions] = useQueryState(
    "excludeSubExecutions",
    false,
  );

  const [modifiedFrom, setModifiedFrom] = useQueryState("modifiedFrom", "");
  const [modifiedTo, setModifiedTo] = useQueryState("modifiedTo", "");

  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE,
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");

  const workflowNames: string[] = useWorkflowNames();
  const workflowIds = useMemo(() => splitList(workflowId), [workflowId]);

  const handleRowsPerPage = (rowsPerPage: number) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

  const clearAllFields = () => {
    setWorkflowType([]);
    setCorrelationIds([]);
    setIdempotencyKey([]);
    setWorkflowId("");
    setStatus([]);
    setStartTimeFrom("");
    setStartTimeTo("");
    setFreeText("");
    setModifiedFrom("");
    setModifiedTo("");
    setEndTimeFrom("");
    setEndTimeTo("");
    setExcludeSubExecutions(false);
    search.clearTerm();
    setToDisplayTime("Now");
    setFromDisplayTime("Last 72 Hours");
  };

  const currentTimeStamp = Date.now().toString();
  const last72HoursTimestamp = Date.now() - 72 * 60 * 60 * 1000;

  const handleReset = () => {
    clearAllFields();
    setStartTimeFrom(String(last72HoursTimestamp));
    setStartTimeTo("");
    const newQueryFT = {
      query: `startTime>${String(
        last72HoursTimestamp,
      )} AND startTime<${currentTimeStamp}`,
      freeText: "*",
    };
    setQueryFT(newQueryFT);
  };

  const [errorMessage, setErrorMessage] = useState<IObject | null>(null);

  const [unauthorized, setUnauthorized] = useState<{
    message?: string;
    error?: string;
  } | null>(null);

  const buildQuery = useCallback(() => {
    const clauses = [];
    if (!_isEmpty(workflowType)) {
      clauses.push(`workflowType IN (${workflowType.join(",")})`);
    }
    const workflowIdFilter = exactClause("workflowId", splitList(workflowId));
    if (workflowIdFilter) {
      clauses.push(workflowIdFilter);
    }
    if (!_isEmpty(status)) {
      clauses.push(`status IN (${status.join(",")})`);
    }
    if (!_isEmpty(startTimeFrom)) {
      clauses.push(`startTime>${dateToEpoch(startTimeFrom)}`);
    }
    if (!_isEmpty(startTimeTo)) {
      clauses.push(`startTime<${dateToEpoch(startTimeTo)}`);
    }
    if (!_isEmpty(endTimeFrom)) {
      clauses.push(`endTime>${dateToEpoch(endTimeFrom)}`);
    }
    if (!_isEmpty(endTimeTo)) {
      clauses.push(`endTime<${dateToEpoch(endTimeTo)}`);
    }

    if (!_isEmpty(modifiedFrom)) {
      clauses.push(`modifiedTime>${modifiedFrom}`);
    }
    if (!_isEmpty(modifiedTo)) {
      clauses.push(`modifiedTime<${modifiedTo}`);
    }

    if (!_isEmpty(correlationIds)) {
      clauses.push(`correlationId IN (${correlationIds.join(",")})`);
    }

    if (!_isEmpty(idempotencyKey)) {
      clauses.push(`idempotencyKey IN (${idempotencyKey.join(",")})`);
    }

    if (!_isEmpty(agentName)) {
      clauses.push(`workflowType='${agentName}'`);
    }

    if (excludeSubLabel && excludeSubExecutions) {
      clauses.push(`parentWorkflowId=""`);
    }

    return {
      query: clauses.join(" AND "),
      freeText: _isEmpty(freeText) ? "*" : freeText,
    };
  }, [
    freeText,
    startTimeFrom,
    startTimeTo,
    status,
    workflowId,
    workflowType,
    modifiedFrom,
    modifiedTo,
    correlationIds,
    idempotencyKey,
    endTimeFrom,
    endTimeTo,
    agentName,
    excludeSubLabel,
    excludeSubExecutions,
  ]);

  const [queryFT, setQueryFT] = useState(buildQuery);
  const {
    data: resultObj,
    error,
    isFetching,
    refetch,
  } = useWorkflowSearch(
    {
      page,
      rowsPerPage,
      sort,
      query: queryFT.query,
      freeText: queryFT.freeText,
      // Scope results to a single classifier: "workflow" for plain workflow
      // executions, "agent" for Conductor-Agents runs on the Agents pages.
      classifier,
    },
    {},
    {
      onError: (error: any) => {
        if (error) {
          getErrors(error as Response).then((result) => {
            if (result?.["workflowName"] === "must not be empty") {
              setErrorMessage({ message: "Workflow name should not be empty" });
            } else {
              setErrorMessage(result);
            }
          });
        } else {
          setErrorMessage(null);
        }
      },
    },
  );

  // hotkeys to search execution
  useHotkeys(
    `${Key.Meta}+${Key.Enter}`,
    () =>
      doSearch({
        resultObj,
        queryFT,
        buildQuery,
        setQueryFT,
        refetch,
        setPage,
        setRecentTaskSearch,
      }),
    {
      enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
    },
  );

  const handleSort = (changedColumn: string, direction: string) => {
    const newSort = `${changedColumn}:${direction.toUpperCase()}`;

    // Only refetch if sort actually changed
    if (sort !== newSort) {
      setPage(1);
      setSort(newSort);
      refetch();
    }
  };
  const handlePage = (page: number) => {
    setPage(page);
  };

  const filterOn = useMemo(() => {
    if (queryFT.query !== "" || queryFT.freeText !== "*") {
      return true;
    } else {
      return false;
    }
  }, [queryFT]);

  const setRecentTaskSearch = () => {
    if (startTimeFrom || startTimeTo || endTimeFrom || endTimeTo) {
      localStorage.setItem(
        "recentTaskSearch",
        JSON.stringify({
          start: startTimeFrom || startTimeTo,
          end: endTimeTo || endTimeFrom,
        }),
      );
    }
  };

  useEffect(() => {
    if (!startTimeFrom) {
      const currentTime = Date.now();
      const timestamp72HoursAgo = currentTime - 72 * 60 * 60 * 1000;
      setStartTimeFrom(String(timestamp72HoursAgo));
    }
    // eslint-disable-next-line
  }, []);

  // Every filter is applied as soon as it changes: panels only write their
  // value on Apply, and the search bar only on Enter or Search, so re-running
  // the search here is what makes those actions search. Skip the initial mount.
  const filtersKey = JSON.stringify([
    workflowType,
    workflowId,
    correlationIds,
    idempotencyKey,
    status,
    freeText,
    startTimeFrom,
    startTimeTo,
    endTimeFrom,
    endTimeTo,
    excludeSubExecutions,
  ]);
  const filtersInitialized = useRef(false);
  useEffect(() => {
    if (!filtersInitialized.current) {
      filtersInitialized.current = true;
      return;
    }
    setPage(1);
    setQueryFT(buildQuery());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtersKey]);

  const runSearch = () =>
    doSearch({
      resultObj,
      queryFT,
      buildQuery,
      setQueryFT,
      refetch,
      setPage,
      setRecentTaskSearch,
    });

  const search = useScopedSearch({
    scopes: SEARCH_SCOPES,
    values: {
      workflowId: workflowIds,
      correlationId: correlationIds,
      idempotencyKey,
      freeText: splitFreeText(freeText),
    },
    setValues: (scope: SearchScope, values: string[]) => {
      switch (scope) {
        case "workflowId":
          setWorkflowId(values.join(","));
          break;
        case "correlationId":
          setCorrelationIds(values);
          break;
        case "idempotencyKey":
          setIdempotencyKey(values);
          break;
        case "freeText":
          setFreeText(values.join(" "));
          break;
      }
    },
    onSearchAgain: runSearch,
  });

  const hasActiveFilters =
    search.chips.length > 0 ||
    workflowType.length > 0 ||
    status.length > 0 ||
    !_isEmpty(startTimeTo) ||
    !_isEmpty(endTimeFrom) ||
    !_isEmpty(endTimeTo) ||
    excludeSubExecutions;

  // @ts-ignore
  if (error?.status === 401) {
    const errorResult = error;
    const parseErrorResponse = async () => {
      try {
        // @ts-ignore
        const json = await errorResult.clone().json();
        setUnauthorized(json);
      } catch {
        setUnauthorized(null);
      }
    };
    parseErrorResponse();
  }

  if (unauthorized) {
    if (unauthorized.message) {
      return (
        <Navigate
          to={`${ERROR_URL}?message=${unauthorized.message}&error=${unauthorized.error}`}
        />
      );
    }

    return <Navigate to={ERROR_URL} />;
  }

  const handleError = (error: any) => {
    setErrorMessage(error);
  };
  const handleClearError = () => {
    setErrorMessage(null);
  };

  return (
    <>
      <Paper variant="outlined" sx={{ marginBottom: 6 }}>
        {showCodeDialog && (
          <ApiSearchModalIntegration
            onClose={() => setShowCodeDialog("")}
            buildQueryOutput={{
              start: (page - 1) * rowsPerPage,
              size: rowsPerPage,
              sort,
              freeText,
              query: buildQuery().query,
            }}
          />
        )}
        <ExecutionSearchFilters
          search={search}
          nameFilter={{
            label: "Workflow name",
            noun: "workflow",
            names: workflowNames,
            selected: workflowType,
            onApply: setWorkflowType,
          }}
          statusFilter={{ selected: status, onApply: setStatus }}
          hasActiveFilters={hasActiveFilters}
          onClearAll={handleReset}
          dateFilters={buildExecutionDateFilters({
            startHelpText:
              "Select a date range within which the Workflow Execution has started.",
            endHelpText:
              "Select a date range within which the Workflow Execution has ended.",
            startTimeFrom,
            startTimeTo,
            onStartFromChange,
            onStartToChange,
            fromDisplayTime,
            setFromDisplayTime,
            endTimeFrom,
            endTimeTo,
            onEndFromChange,
            onEndToChange,
            toDisplayTime,
            setToDisplayTime,
          })}
          toggles={
            excludeSubLabel && (
              <FormControlLabel
                sx={{ m: 0, whiteSpace: "nowrap" }}
                control={
                  <Switch
                    color="primary"
                    checked={excludeSubExecutions}
                    onChange={(e) => setExcludeSubExecutions(e.target.checked)}
                    size="small"
                  />
                }
                label={excludeSubLabel}
                slotProps={{
                  typography: { variant: "body2" },
                }}
              />
            )
          }
          modeSwitch={SwitchComponent}
          refresh={{
            onRefresh: runSearch,
            onShowCode: () => setShowCodeDialog("active"),
          }}
        />
      </Paper>
      <ResultsTable
        title={resultObj ? getTableTitle(resultObj) : undefined}
        resultObj={resultObj}
        error={errorMessage}
        busy={isFetching}
        page={page}
        rowsPerPage={rowsPerPage}
        setPage={handlePage}
        setSort={handleSort}
        showMore={true}
        refetchExecution={refetch}
        handleError={handleError}
        handleClearError={handleClearError}
        filterOn={filterOn}
        handleReset={handleReset}
        setRowsPerPage={handleRowsPerPage}
      />
    </>
  );
}
