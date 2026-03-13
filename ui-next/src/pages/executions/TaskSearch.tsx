import { Box } from "@mui/material";
import { Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/DataTable/DataTable";
import MuiTypography from "components/MuiTypography";
import AddIcon from "components/v1/icons/AddIcon";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useHotkeys } from "react-hotkeys-hook";
import { UseQueryResult } from "react-query";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import SectionHeaderActions from "shared/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { Key } from "ts-key-enum";
import { TaskExecutionResult } from "types/TaskExecution";
import { IObject } from "types/common";
import { dateToEpoch } from "utils";
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

const DEFAULT_SORT = "startTime:DESC";

const getTableTitle = (resultObj: TaskExecutionResult) => {
  const { results, totalHits } = resultObj;
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
      <MuiTypography fontWeight={400} fontSize={14}>
        {results.length} results
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
  const [queryText, setQueryText] = useQueryState("query", "");
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
      if (!_isEmpty(taskType) && !queryText.includes("taskType")) {
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
      if (!_isEmpty(status) && !queryText.includes("status")) {
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
          <SwitchComponent asQuery={asQuery} setAsQuery={setAsQuery} />
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
    </>
  );
}
