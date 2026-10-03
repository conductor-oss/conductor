import { Monaco } from "@monaco-editor/react";
import { Box } from "@mui/material";
import { Paper } from "components";
import {
  ExecutionSearchFilters,
  FreeTextInput,
  SqlQueryBar,
} from "components/features/executionSearch";
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
import { IObject } from "types/common";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { dateToEpoch } from "utils";
import { WORKFLOW_SEARCH_QUERY_SUGGESTIONS } from "utils/constants/common";
import { ERROR_URL } from "utils/constants/route";
import { useWorkflowNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import { buildExecutionDateFilters } from "../executionDateFilters";
import ResultsTable from "../ResultsTable";

const DEFAULT_SORT = "startTime:DESC";
const workflowStatuses = Object.values(WorkflowExecutionStatus);

export interface AdvancedSearchProps {
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
}

export default function AdvancedSearch({
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
}: AdvancedSearchProps) {
  const disposeRef = useRef<null | (() => void)>(null);
  const [queryText, setQueryText] = useQueryState("query", "");
  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE,
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");

  const [errorMessage, setErrorMessage] = useState<IObject | null>(null);

  const [unauthorized, setUnauthorized] = useState<{
    message?: string;
    error?: string;
  } | null>(null);

  // For dropdown
  const workflowNames: string[] = useWorkflowNames();

  useEffect(() => {
    return () => {
      if (disposeRef.current) {
        disposeRef.current();
      }
    };
  }, []);

  const currentTimeStamp = Date.now().toString();
  const last72HoursTimestamp = Date.now() - 72 * 60 * 60 * 1000;

  const buildQuery = useCallback(() => {
    const clauses = [];

    if (!_isEmpty(status) && !queryText.includes("status")) {
      clauses.push(`status IN (${status.join(",")})`);
    }

    if (!queryText.includes("startTime")) {
      if (!_isEmpty(startTimeFrom)) {
        clauses.push(`startTime>${dateToEpoch(startTimeFrom)}`);
      }
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

    if (!_isEmpty(queryText)) {
      clauses.push(queryText);
    }

    return {
      query: clauses.join(" AND "),
      freeText: _isEmpty(freeText) ? "*" : freeText,
    };
  }, [
    freeText,
    startTimeFrom,
    startTimeTo,
    endTimeFrom,
    endTimeTo,
    status,
    queryText,
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
      staleTime: 0,
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

  // Must be called before any early returns to follow Rules of Hooks
  const filterOn = useMemo(() => {
    if (queryFT.query !== "" || queryFT.freeText !== "*") {
      return true;
    } else {
      return false;
    }
  }, [queryFT]);

  const handlePage = (page: number) => {
    setPage(page);
  };

  const handleSort = (changedColumn: string, direction: string) => {
    const newSort = `${changedColumn}:${direction.toUpperCase()}`;

    // Only refetch if sort actually changed
    if (sort !== newSort) {
      setPage(1);
      setSort(newSort);
      refetch();
    }
  };

  // The pills apply on Apply, like in basic mode, so changing them searches
  // with the current query. Typing in the editor still waits for Search.
  const pillFiltersKey = JSON.stringify([
    status,
    startTimeFrom,
    startTimeTo,
    endTimeFrom,
    endTimeTo,
  ]);
  const pillFiltersInitialized = useRef(false);
  useEffect(() => {
    if (!pillFiltersInitialized.current) {
      pillFiltersInitialized.current = true;
      return;
    }
    setPage(1);
    setQueryFT(buildQuery());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pillFiltersKey]);

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

  const clearAllFields = () => {
    setStatus([]);
    setStartTimeFrom("");
    setStartTimeTo("");
    setEndTimeFrom("");
    setEndTimeTo("");
    setToDisplayTime("");
    setFromDisplayTime("Last 72 Hours");
    setFreeText("");
    setQueryText("");
  };

  const handleReset = () => {
    clearAllFields();
    setStartTimeFrom(last72HoursTimestamp.toString());
    setStartTimeTo("");
    const newQueryFT = {
      query: `startTime>${last72HoursTimestamp.toString()} AND startTime<${currentTimeStamp}`,
      freeText: "*",
    };
    setQueryFT(newQueryFT);
  };

  const handleRowsPerPage = (rowsPerPage: number) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

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

  const hasActiveFilters =
    !_isEmpty(queryText) ||
    !_isEmpty(freeText) ||
    status.length > 0 ||
    !_isEmpty(startTimeTo) ||
    !_isEmpty(endTimeFrom) ||
    !_isEmpty(endTimeTo);

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
          query={
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
              <SqlQueryBar
                value={queryText}
                onChange={setQueryText}
                onSubmit={runSearch}
                hint="Join conditions with AND · ⌘/Ctrl+Enter to search"
                beforeMount={(monaco: Monaco) => {
                  if (disposeRef.current) {
                    disposeRef.current();
                    disposeRef.current = null;
                  }
                  const disposable =
                    monaco.languages.registerCompletionItemProvider("sql", {
                      provideCompletionItems: () => {
                        const propertyKeys = [
                          ...WORKFLOW_SEARCH_QUERY_SUGGESTIONS,
                          ...workflowStatuses,
                          ...workflowNames,
                          "workflowType",
                        ];
                        // Provide suggestions for properties that start with the current text
                        const propertySuggestions = propertyKeys.map(
                          (property) => ({
                            label: property,
                            kind: monaco.languages.CompletionItemKind.Value,
                            insertText: property,
                          }),
                        );
                        // Merge custom suggestions with property suggestions
                        const suggestions = [...propertySuggestions];
                        return { suggestions };
                      },
                    });
                  // IMPORTANT: keep `dispose()` bound to its disposable context.
                  // Destructuring `dispose` can lose `this` and throw "Unbound disposable context".
                  disposeRef.current = () => disposable.dispose();
                }}
              />
              <FreeTextInput
                value={freeText}
                onChange={setFreeText}
                onSubmit={runSearch}
              />
            </Box>
          }
          refresh={{
            onRefresh: runSearch,
            onShowCode: () => setShowCodeDialog("active"),
          }}
          statusFilter={{
            selected: status,
            onApply: setStatus,
            disabled: queryText.includes("status"),
          }}
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
          modeSwitch={SwitchComponent}
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
