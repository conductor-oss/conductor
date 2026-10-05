import { Box } from "@mui/material";
import { Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import MuiTypography from "components/ui/MuiTypography";
import AddIcon from "components/icons/AddIcon";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Helmet } from "react-helmet";
import { useHotkeys } from "react-hotkeys-hook";
import { UseQueryResult } from "react-query";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { Key } from "ts-key-enum";
import { TaskExecutionResult } from "types/TaskExecution";
import { IObject } from "types/common";
import { FEATURES, dateToEpoch, featureFlags } from "utils";
import { pluralizeResults } from "utils/helpers";
import { ERROR_URL, NEW_TASK_DEF_URL } from "utils/constants/route";
import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";
import { useDebouncedQueryState } from "utils/hooks/useDebouncedQueryState";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useTaskExecutionsSearch } from "utils/query";
import { getErrors } from "utils/utils";
import {
  ExecutionSearchFilters,
  exactClause,
  exactScope,
  FreeTextInput,
  freeTextScope,
  SearchModeSwitch,
  SearchScope,
  SearchScopeConfig,
  splitFreeText,
  splitList,
  SqlQueryBar,
  useScopedSearch,
} from "components/features/executionSearch";
import StatusBadge from "components/StatusBadge";
import { Monaco } from "@monaco-editor/react";
import { TaskType } from "types/common";
import { TaskStatus } from "types/TaskStatus";
import {
  TASK_SEARCH_QUERY_SUGGESTIONS,
  WORKFLOW_SEARCH_QUERY_SUGGESTIONS,
} from "utils/constants/common";
import { buildExecutionDateFilters } from "./executionDateFilters";
import { TaskApiSearchModal } from "./Task/TaskApiSearchModal";
import ResultsTable from "./TaskResultsTable";

const DEFAULT_SORT = "startTime:DESC";

// conductor-ui queries workflowName. OSS context.js switches this to
// workflowType, which is the field the OSS task index stores.
const taskWorkflowQueryField =
  featureFlags.getValue(FEATURES.TASK_SEARCH_WORKFLOW_FIELD, "workflowName") ===
  "workflowType"
    ? "workflowType"
    : "workflowName";

const taskTypes = Object.values(TaskType).filter(
  (type) => ![TaskType.START, TaskType.SWITCH_JOIN].includes(type),
);
const taskStatuses = Object.values(TaskStatus)
  .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
  .filter((status) => status !== TaskStatus.PENDING);

const showTaskReferenceName = featureFlags.isEnabled(
  FEATURES.SHOW_TASK_REFERENCE_NAME,
);

// Fields the search bar offers, kept in the URL as comma-separated values.
const TASK_SEARCH_SCOPES: SearchScopeConfig[] = [
  exactScope("taskDefName", "Task definition name", false),
  exactScope("taskId", "Task execution id"),
  ...(showTaskReferenceName
    ? [exactScope("taskRefName", "Task reference name", false)]
    : []),
  exactScope("workflowName", "Workflow name", false),
  freeTextScope(
    "Words that appear in the task's data",
    "Matches words in the indexed task data · all words must match",
  ),
];

const renderTaskStatus = (status: string) => (
  <StatusBadge status={status as TaskStatus} />
);

const getTableTitle = (resultObj: TaskExecutionResult) => {
  const { results, totalHits } = resultObj;
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
      <MuiTypography fontWeight={400} fontSize={14}>
        {pluralizeResults(results.length)}
      </MuiTypography>
      <MuiTypography color={colors.greyText} fontSize={12}>
        of {totalHits}
      </MuiTypography>
    </Box>
  );
};

export function TaskSearch() {
  const currentTimeStamp = Date.now().toString();
  const last72HoursTimestamp = Date.now() - 72 * 60 * 60 * 1000;

  // Text filters sync to the URL on a debounce. The value itself updates
  // immediately, so Search still sees the full text; see the hook for why.
  const [freeText, setFreeText] = useDebouncedQueryState("freeText");
  const [taskDefName, setTaskDefName] = useDebouncedQueryState("taskDefName");
  const [taskId, setTaskId] = useDebouncedQueryState("taskId");
  const [taskRefName, setTaskRefName] = useDebouncedQueryState("taskRefName");
  const [workflowName, setWorkflowName] =
    useDebouncedQueryState("workflowName");
  const [queryText, setQueryText] = useDebouncedQueryState("query");
  const [status, setStatus] = useQueryState<string[]>("status", []);
  const [taskType, setTaskType] = useQueryState<string[]>("taskType", []);

  const [startTimeFrom, setStartTimeFrom] = useQueryState(
    "startFrom",
    commonlyUsedDateTime("last72Hours").rangeStart,
  );

  const [startTimeEnd, setStartTimeEnd] = useQueryState("startTimeTo", "");
  const [endTimeFrom, setEndTimeFrom] = useQueryState("endTimeFrom", "");
  const [endTimeTo, setEndTime] = useQueryState("endTimeTo", "");

  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE,
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");
  const [asQuery, setAsQuery] = useQueryState("asQuery", false);
  const [errorMessage, setErrorMessage] = useState<IObject | null>(null);

  const [unauthorized, setUnauthorized] = useState<{
    message?: string;
    error?: string;
  } | null>(null);

  const [fromDisplayTime, setFromDisplayTime] = useState(
    startTimeFrom
      ? getSearchDateTime(startTimeFrom, startTimeEnd)
      : "Last 72 Hours",
  );
  const [toDisplayTime, setToDisplayTime] = useState(
    endTimeTo ? getSearchDateTime(endTimeFrom, endTimeTo) : "Select time range",
  );

  useEffect(() => {
    if (!startTimeFrom) {
      setStartTimeFrom(last72HoursTimestamp.toString());
      setStartTimeEnd("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const buildQuery = useCallback(() => {
    const clauses = [];

    if (asQuery) {
      if (!_isEmpty(queryText)) {
        clauses.push(queryText);
      }
    } else {
      const listClauses = [
        exactClause("taskDefName", splitList(taskDefName)),
        exactClause("taskId", splitList(taskId)),
        showTaskReferenceName
          ? exactClause("referenceTaskName", splitList(taskRefName))
          : null,
        exactClause(taskWorkflowQueryField, splitList(workflowName)),
      ];
      listClauses.forEach((clause) => clause && clauses.push(clause));
      if (!_isEmpty(taskType)) {
        clauses.push(`taskType IN (${taskType.join(",")})`);
      }
      if (!_isEmpty(status)) {
        clauses.push(`status IN (${status.join(",")})`);
      }
    }
    if (!_isEmpty(startTimeFrom)) {
      clauses.push(`startTime>${dateToEpoch(startTimeFrom)}`);
    }
    if (!_isEmpty(startTimeEnd)) {
      clauses.push(`startTime<${dateToEpoch(startTimeEnd)}`);
    }
    if (!_isEmpty(endTimeFrom)) {
      clauses.push(`endTime>${dateToEpoch(endTimeFrom)}`);
    }
    if (!_isEmpty(endTimeTo)) {
      clauses.push(`endTime<${dateToEpoch(endTimeTo)}`);
    }

    return {
      query: clauses.join(" AND "),
      freeText: _isEmpty(freeText) ? "*" : freeText,
    };
  }, [
    asQuery,
    endTimeTo,
    endTimeFrom,
    freeText,
    queryText,
    startTimeFrom,
    startTimeEnd,
    status,
    taskDefName,
    taskId,
    taskRefName,
    taskType,
    workflowName,
  ]);

  const [queryFT, setQueryFT] = useState(buildQuery);
  const {
    data: resultObj,
    error,
    isFetching,
    refetch,
  }: UseQueryResult<TaskExecutionResult> = useTaskExecutionsSearch(
    {
      page,
      rowsPerPage,
      sort,
      query: queryFT.query,
      freeText: queryFT.freeText,
    },
    {
      onError: (error: any) => {
        if (error) {
          getErrors(error as Response).then((result) => {
            if (result?.["taskNames"] === "must not be empty") {
              setErrorMessage({ message: "task name should not be empty" });
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

  const doSearch = useCallback(() => {
    setPage(1);

    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    if (_isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
    if (startTimeFrom || startTimeEnd || endTimeFrom || endTimeTo) {
      localStorage.setItem(
        "recentTaskSearch",
        JSON.stringify({
          start: startTimeFrom || startTimeEnd,
          end: endTimeTo || endTimeFrom,
        }),
      );
    }
  }, [
    buildQuery,
    endTimeTo,
    queryFT,
    refetch,
    setPage,
    startTimeFrom,
    startTimeEnd,
    endTimeFrom,
  ]);

  // hotkeys to search execution
  useHotkeys(`${Key.Meta}+${Key.Enter}`, doSearch, {
    enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
  });

  const search = useScopedSearch({
    scopes: TASK_SEARCH_SCOPES,
    values: {
      taskDefName: splitList(taskDefName),
      taskId: splitList(taskId),
      taskRefName: splitList(taskRefName),
      workflowName: splitList(workflowName),
      freeText: splitFreeText(freeText),
    },
    setValues: (scope: SearchScope, values: string[]) => {
      const setters: Record<SearchScope, (value: string) => void> = {
        taskDefName: setTaskDefName,
        taskId: setTaskId,
        taskRefName: setTaskRefName,
        workflowName: setWorkflowName,
      };
      if (scope === "freeText") {
        setFreeText(values.join(" "));
      } else {
        setters[scope]?.(values.join(","));
      }
    },
    onSearchAgain: doSearch,
  });

  // Every filter is applied as soon as it changes: panels only write their
  // value on Apply, and the search bar only on Enter or Search, so re-running
  // the search here is what makes those actions search. Typing in the SQL
  // editor still waits for Search. Skip the initial mount.
  const filtersKey = JSON.stringify([
    asQuery,
    taskDefName,
    taskId,
    taskRefName,
    workflowName,
    taskType,
    status,
    asQuery ? "" : freeText,
    startTimeFrom,
    startTimeEnd,
    endTimeFrom,
    endTimeTo,
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

  // The SQL editor's completion provider is global to Monaco; drop it on unmount.
  const disposeCompletionsRef = useRef<null | (() => void)>(null);
  useEffect(
    () => () => {
      disposeCompletionsRef.current?.();
      disposeCompletionsRef.current = null;
    },
    [],
  );
  const registerCompletions = (monaco: Monaco) => {
    disposeCompletionsRef.current?.();
    const disposable = monaco.languages.registerCompletionItemProvider("sql", {
      provideCompletionItems: () => ({
        suggestions: [
          ...WORKFLOW_SEARCH_QUERY_SUGGESTIONS,
          ...TASK_SEARCH_QUERY_SUGGESTIONS,
          ...taskTypes,
          ...taskStatuses,
        ]
          .filter(
            (property) =>
              showTaskReferenceName || property !== "referenceTaskName",
          )
          .map((property) => ({
            label: property,
            kind: monaco.languages.CompletionItemKind.Value,
            insertText: property,
          })),
      }),
    });
    // Keep dispose() bound to its disposable.
    disposeCompletionsRef.current = () => disposable.dispose();
  };

  const handlePage = (page: number) => {
    setPage(page);
  };

  const handleSort = (changedColumn: string, direction: string) => {
    const sort = `${changedColumn}:${direction.toUpperCase()}`;
    setPage(1);
    setSort(sort);
  };

  const handleRowsPerPage = (rowsPerPage: number) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

  const onStartFromChange = (val: string) => {
    if (val) setStartTimeFrom(String(dateToEpoch(val)));
    else setStartTimeFrom("");
  };

  const onStartToChange = (val: string) => {
    if (val) setStartTimeEnd(String(dateToEpoch(val)));
    else setStartTimeEnd("");
  };

  const pushHistory = usePushHistory();

  // Must be called before any early returns to follow Rules of Hooks
  const filterOn = useMemo(() => {
    if (queryFT.query !== "" || queryFT.freeText !== "*") {
      return true;
    } else {
      return false;
    }
  }, [queryFT]);

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

  const onEndFromChange = (val: string) => {
    if (val) setEndTimeFrom(String(dateToEpoch(val)));
    else setEndTimeFrom("");
  };

  const onEndToChange = (val: string) => {
    if (val) setEndTime(String(dateToEpoch(val)));
    else setEndTime("");
  };

  const clearAllFields = () => {
    if (asQuery) {
      setQueryText("");
    } else {
      setTaskDefName("");
      setTaskType([]);
      setTaskId("");
      setTaskRefName("");
      setWorkflowName("");
      setStatus([]);
    }
    setStartTimeFrom(last72HoursTimestamp.toString());
    setStartTimeEnd("");
    setEndTimeFrom("");
    setEndTime("");
    setFreeText("");
    setToDisplayTime("");
    setFromDisplayTime("Last 72 Hours");
    setSort(DEFAULT_SORT);
  };

  const hasActiveFilters =
    (asQuery
      ? !_isEmpty(queryText)
      : search.chips.length > 0 || taskType.length > 0 || status.length > 0) ||
    !_isEmpty(freeText) ||
    !_isEmpty(startTimeEnd) ||
    !_isEmpty(endTimeFrom) ||
    !_isEmpty(endTimeTo);

  const handleReset = () => {
    clearAllFields();
    const newQueryFT = {
      query: `startTime>${last72HoursTimestamp.toString()} AND startTime<${currentTimeStamp}`,
      freeText: "*",
    };
    setQueryFT(newQueryFT);
  };

  return (
    <>
      <Helmet>
        <title>Task Executions</title>
      </Helmet>

      {showCodeDialog && (
        <TaskApiSearchModal
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
      <SectionHeader
        _deprecate_marginTop={0}
        title="Task Executions"
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Define task",
                onClick: () => pushHistory(NEW_TASK_DEF_URL),
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
        <Paper variant="outlined" sx={{ marginBottom: 6 }}>
          <ExecutionSearchFilters
            search={asQuery ? undefined : search}
            query={
              asQuery ? (
                <Box
                  sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}
                >
                  <SqlQueryBar
                    value={queryText}
                    onChange={setQueryText}
                    onSubmit={doSearch}
                    beforeMount={registerCompletions}
                    hint="Join conditions with AND · ⌘/Ctrl+Enter to search"
                    searchButtonId="search-task-btn"
                    placeholder="taskType = 'HTTP' AND status IN (FAILED)"
                  />
                  <FreeTextInput
                    value={freeText}
                    onChange={setFreeText}
                    onSubmit={doSearch}
                    placeholder="Free text: words that appear in the task's data"
                  />
                </Box>
              ) : undefined
            }
            ids={{
              search: "search-task-btn",
              refresh: "refresh-task-search-btn",
              clearAll: "reset-task-btn",
              name: "task-type-dropdown",
              status: "task-status-dropdown",
            }}
            nameFilter={
              asQuery
                ? undefined
                : {
                    label: "Task type",
                    noun: "task type",
                    names: taskTypes,
                    selected: taskType,
                    onApply: setTaskType,
                    allowPatterns: false,
                  }
            }
            statusFilter={
              asQuery
                ? undefined
                : {
                    selected: status,
                    onApply: setStatus,
                    options: taskStatuses,
                    renderOption: renderTaskStatus,
                  }
            }
            dateFilters={buildExecutionDateFilters({
              startHelpText:
                "Select a date range within which the Task Execution has started.",
              endHelpText:
                "Select a date range within which the Task Execution has ended.",
              startTimeFrom,
              startTimeTo: startTimeEnd,
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
            modeSwitch={
              <SearchModeSwitch checked={asQuery} onChange={setAsQuery} />
            }
            refresh={{
              onRefresh: doSearch,
              onShowCode: () => setShowCodeDialog("active"),
            }}
            hasActiveFilters={hasActiveFilters}
            onClearAll={handleReset}
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
          setRowsPerPage={handleRowsPerPage}
          showMore={true}
          refetchExecution={refetch}
          handleError={handleError}
          handleClearError={handleClearError}
          filterOn={filterOn}
          handleReset={handleReset}
        />
      </SectionContainer>
    </>
  );
}
