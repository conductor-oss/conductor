import { Box } from "@mui/material";
import { Paper } from "components";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import MuiTypography from "components/ui/MuiTypography";
import AddIcon from "components/icons/AddIcon";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import { useCallback, useEffect, useMemo, useState } from "react";
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
import { dateToEpoch } from "utils";
import { pluralizeResults } from "utils/helpers";
import { ERROR_URL, NEW_TASK_DEF_URL } from "utils/constants/route";
import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useTaskExecutionsSearch } from "utils/query";
import { getErrors, tryToJson } from "utils/utils";
import { AdvanceSearch } from "./Task/AdvanceSearch";
import { BasicSearch } from "./Task/BasicSearch";
import { SwitchComponent } from "./Task/SwitchComponent";
import { TaskApiSearchModal } from "./Task/TaskApiSearchModal";
import ResultsTable from "./TaskResultsTable";
import {
  ParsedBasicTaskFilters,
  basicOnlyTaskFilterQuery,
  basicTaskFieldsAfterQueryFormat,
  parseQueryToBasicTaskFilters,
} from "./taskFilterQuery";

const DEFAULT_SORT = "startTime:DESC";

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

  const [freeText, setFreeText] = useQueryState("freeText", "");
  const [taskDefName, setTaskDefName] = useQueryState("taskDefName", "");
  const [taskId, setTaskId] = useQueryState("taskId", "");
  const [taskRefName, setTaskRefName] = useQueryState("taskRefName", "");
  const [workflowName, setWorkflowName] = useQueryState("workflowName", "");
  const [status, setStatus] = useQueryState<string[]>("status", []);
  const [taskType, setTaskType] = useQueryState<string[]>("taskType", []);

  const [asQuery, setAsQuery] = useQueryState("asQuery", false);
  const [authoredQuery, setAuthoredQuery] = useQueryState("query", "");

  /** The clauses for the filters that only basic search renders a control for. */
  const seedFromBasicFilters = () =>
    basicOnlyTaskFilterQuery({
      taskDefName,
      taskType,
      taskId,
      taskRefName,
      workflowName,
      status,
    });

  // The seed cannot ride on useQueryState's default value the way the workflow
  // and agent searches do: that default is captured on the hook's first render
  // and never updated again, so any filter set after page load would be missed.
  // Those two get away with it because their advanced panel is a separate
  // component that mounts at the toggle; both modes live in this one. So the
  // seed is held in state, recomputed whenever SQL format is entered, and a
  // flag records whether the box is still showing it or the user has since
  // edited it — which is what tells the toggle back whether the basic fields
  // need updating from the text.
  const [seededQuery, setSeededQuery] = useState(seedFromBasicFilters);
  const [showingSeed, setShowingSeed] = useState(
    () => asQuery && _isEmpty(authoredQuery),
  );
  const queryText = showingSeed ? seededQuery : authoredQuery;

  const setQueryText = (value: string) => {
    setShowingSeed(false);
    setAuthoredQuery(value);
  };

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
  const [discardQueryOpen, setDiscardQueryOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<IObject | null>(null);

  const [unauthorized, setUnauthorized] = useState<{
    message?: string;
    error?: string;
  } | null>(null);

  const [openDateSelect, setOpenDateSelect] = useState(false);
  const [openStartDatePicker, setStartOpenDatePicker] = useState(false);
  const [openEndDatePicker, setEndOpenDatePicker] = useState(false);
  const [fromDisplayTime, setFromDisplayTime] = useState(
    startTimeFrom
      ? getSearchDateTime(startTimeFrom, startTimeEnd)
      : "Last 72 Hours",
  );
  const [toDisplayTime, setToDisplayTime] = useState(
    endTimeTo ? getSearchDateTime(endTimeFrom, endTimeTo) : "Select time range",
  );

  const recentSearches =
    (tryToJson(localStorage.getItem("recentTaskSearch")) as {
      start: string;
      end: string;
    }) || {};

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
      if (!_isEmpty(taskDefName)) {
        clauses.push(`taskDefName='${taskDefName}'`);
      }
      if (!_isEmpty(taskType) && !authoredQuery.includes("taskType")) {
        clauses.push(`taskType IN (${taskType.join(",")})`);
      }
      if (!_isEmpty(taskId)) {
        clauses.push(`taskId='${taskId}'`);
      }
      if (!_isEmpty(taskRefName)) {
        clauses.push(`referenceTaskName='${taskRefName}'`);
      }
      if (!_isEmpty(workflowName)) {
        clauses.push(`workflowName='${workflowName}'`);
      }
      if (!_isEmpty(status) && !authoredQuery.includes("status")) {
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
    authoredQuery,
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

  const handlePage = (page: number) => {
    setPage(page);
  };

  const handleSort = (changedColumn: string, direction: string) => {
    const sortColumn =
      changedColumn === "workflowType" ? "workflowName" : changedColumn;
    const sort = `${sortColumn}:${direction.toUpperCase()}`;
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
    // Clears every filter, not just the ones the current mode renders, so
    // nothing reappears on flipping the toggle. setQueryText also drops the
    // seed, so the box stays empty rather than refilling from fields that
    // have just been cleared.
    setQueryText("");
    setTaskDefName("");
    setTaskType([]);
    setTaskId("");
    setTaskRefName("");
    setWorkflowName("");
    setStatus([]);
    setStartTimeFrom(last72HoursTimestamp.toString());
    setStartTimeEnd("");
    setEndTimeFrom("");
    setEndTime("");
    setFreeText("");
    setToDisplayTime("");
    setFromDisplayTime("Last 72 Hours");
    setSort(DEFAULT_SORT);
  };

  const leaveQueryFormat = () => {
    // Drop the param too, so a discarded query cannot reappear the next time
    // SQL format is switched on.
    setShowingSeed(false);
    setAuthoredQuery("");
    setAsQuery(false);
  };

  const applyParsedFilters = (parsed: ParsedBasicTaskFilters) => {
    const next = basicTaskFieldsAfterQueryFormat(parsed, {
      startTimeFrom,
      startTimeTo: startTimeEnd,
      endTimeFrom,
      endTimeTo,
    });
    setTaskDefName(next.taskDefName);
    setTaskType(next.taskType);
    setTaskId(next.taskId);
    setTaskRefName(next.taskRefName);
    setWorkflowName(next.workflowName);
    setStatus(next.status);
    setStartTimeFrom(next.startTimeFrom);
    setStartTimeEnd(next.startTimeTo);
    setEndTimeFrom(next.endTimeFrom);
    setEndTime(next.endTimeTo);
    // Mirror how these labels are derived on mount.
    setFromDisplayTime(
      next.startTimeFrom
        ? getSearchDateTime(next.startTimeFrom, next.startTimeTo)
        : "Last 72 Hours",
    );
    setToDisplayTime(
      next.endTimeTo
        ? getSearchDateTime(next.endTimeFrom, next.endTimeTo)
        : "Select time range",
    );
  };

  const handleToggleQueryFormat = () => {
    if (!asQuery) {
      setSeededQuery(seedFromBasicFilters());
      setShowingSeed(true);
      setAuthoredQuery("");
      setAsQuery(true);
      return;
    }
    // An untouched box still says exactly what the basic fields say, so there
    // is nothing to read back into them.
    if (showingSeed) {
      leaveQueryFormat();
      return;
    }
    const parsed = parseQueryToBasicTaskFilters(authoredQuery);
    if (parsed) {
      applyParsedFilters(parsed);
      leaveQueryFormat();
      return;
    }
    // Nothing basic search can express; ask before dropping it.
    setDiscardQueryOpen(true);
  };

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
          <SwitchComponent
            asQuery={asQuery}
            onToggle={handleToggleQueryFormat}
          />
          {asQuery ? (
            <AdvanceSearch
              setShowCodeDialog={setShowCodeDialog}
              doSearch={doSearch}
              handleReset={handleReset}
              onStartFromChange={onStartFromChange}
              onStartToChange={onStartToChange}
              startTime={startTimeFrom}
              endTime={endTimeTo}
              queryText={queryText}
              setQueryText={setQueryText}
              freeText={freeText}
              setFreeText={setFreeText}
              fromDisplayTime={fromDisplayTime}
              setFromDisplayTime={setFromDisplayTime}
              openEndDatePicker={openEndDatePicker}
              setEndOpenDatePicker={setEndOpenDatePicker}
              toDisplayTime={toDisplayTime}
              setToDisplayTime={setToDisplayTime}
              openDateSelect={openDateSelect}
              setOpenDateSelect={setOpenDateSelect}
              openStartDatePicker={openStartDatePicker}
              setStartOpenDatePicker={setStartOpenDatePicker}
              onEndFromChange={onEndFromChange}
              onEndToChange={onEndToChange}
              startTimeEnd={startTimeEnd}
              endTimeStart={endTimeFrom}
              recentSearches={recentSearches}
            />
          ) : (
            <BasicSearch
              taskDefName={taskDefName}
              taskType={taskType}
              taskExecutionId={taskId}
              taskRefName={taskRefName}
              workflowName={workflowName}
              status={status}
              startTime={startTimeFrom}
              startTimeEnd={startTimeEnd}
              endTime={endTimeTo}
              endTimeStart={endTimeFrom}
              freeText={freeText}
              setTaskDefName={setTaskDefName}
              setTaskType={setTaskType}
              setTaskExecutionId={setTaskId}
              setTaskRefName={setTaskRefName}
              setWorkflowName={setWorkflowName}
              setShowCodeDialog={setShowCodeDialog}
              doSearch={doSearch}
              handleReset={handleReset}
              onStartFromChange={onStartFromChange}
              onStartToChange={onStartToChange}
              setFreeText={setFreeText}
              setStatus={setStatus}
              fromDisplayTime={fromDisplayTime}
              setFromDisplayTime={setFromDisplayTime}
              openEndDatePicker={openEndDatePicker}
              setEndOpenDatePicker={setEndOpenDatePicker}
              toDisplayTime={toDisplayTime}
              setToDisplayTime={setToDisplayTime}
              openDateSelect={openDateSelect}
              setOpenDateSelect={setOpenDateSelect}
              openStartDatePicker={openStartDatePicker}
              setStartOpenDatePicker={setStartOpenDatePicker}
              onEndFromChange={onEndFromChange}
              onEndToChange={onEndToChange}
              queryText={queryText}
              recentSearches={recentSearches}
            />
          )}
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
      {discardQueryOpen && (
        <ConfirmChoiceDialog
          id="discard-sql-query-dialog"
          header="Discard SQL query?"
          message="Basic search cannot represent this query, so switching will discard it and search with the fields above instead."
          cancelBtnLabel="Keep editing"
          confirmBtnLabel="Discard and switch"
          handleConfirmationValue={(confirmed: boolean) => {
            setDiscardQueryOpen(false);
            if (confirmed) {
              leaveQueryFormat();
            }
          }}
        />
      )}
    </>
  );
}
