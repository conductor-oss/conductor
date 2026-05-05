import { Box, Grid } from "@mui/material";
import { Button, Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import StatusBadge from "components/StatusBadge";
import { renderStatusTagChip } from "components/StatusTagChip";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import ConductorDateRangePicker from "components/ui/date-time/ConductorDateRangePicker";
import ResetIcon from "components/icons/ResetIcon";
import SearchIcon from "components/icons/SearchIcon";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import { useState } from "react";
import { Helmet } from "react-helmet";
import { useHotkeys } from "react-hotkeys-hook";
import { UseQueryResult } from "react-query/types/react/types";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import { Key } from "ts-key-enum";
import { ERROR_URL } from "utils/constants/route";
import { useScheduleNames } from "utils/hooks/useGetSchedulerDefinitions";
import { useSchedulerSearch, useWorkflowNames } from "utils/query";
import { SchedulerApiSearchModal } from "./SchedulerApiSearchModal";
import SchedulerResultsTable from "./SchedulerResultsTable";

const DEFAULT_SORT = "startTime:DESC";
const MS_IN_DAY = 86400000;

export default function SchedulerExecutions() {
  const [status, setStatus] = useQueryState<string[]>("status", []);
  const [workflowType, setWorkflowType] = useQueryState<string[]>(
    "workflowType",
    [],
  );
  const [scheduleName, setScheduleName] = useQueryState<string[]>(
    "scheduleName",
    [],
  );
  const [executionId, setExecutionId] = useQueryState("executionId", "");
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [lookback, setLookback] = useQueryState("lookback", "");

  const [errorMessage, setErrorMessage] = useState(null);

  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE,
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [queryFT, setQueryFT] = useState(buildQuery);

  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");

  const {
    data: resultObj,
    error,
    isFetching,
    refetch,
  } = useSchedulerSearch({
    page,
    rowsPerPage,
    sort,
    query: queryFT.query,
    freeText: queryFT.freeText,
  }) as UseQueryResult<any, any>;
  const [unauthorized, setUnauthorized] = useState<any>(null);

  // For dropdown
  const workflowNames = useWorkflowNames();
  const scheduleNames = useScheduleNames();
  const scheduleStatuses = ["POLLED", "EXECUTED", "FAILED"]; // POLLED, FAILED, EXECUTED

  function buildQuery() {
    const clauses = [];
    if (!_isEmpty(workflowType)) {
      clauses.push(`workflowType IN (${workflowType.join(",")})`);
    }
    if (!_isEmpty(scheduleName)) {
      clauses.push(`scheduleName IN (${scheduleName.join(",")})`);
    }
    if (!_isEmpty(executionId)) {
      clauses.push(`executionId='${executionId}'`);
    }
    if (!_isEmpty(status)) {
      clauses.push(`status IN (${status.join(",")})`);
    }
    if (!_isEmpty(lookback)) {
      clauses.push(
        `startTime>${new Date().getTime() - Number(lookback) * MS_IN_DAY}`,
      );
      clauses.push(`startTime<${new Date().getTime()}`);
    }
    if (!_isEmpty(startFrom)) {
      clauses.push(`startTime>${new Date(startFrom).getTime()}`);
    }
    if (!_isEmpty(startTo)) {
      clauses.push(`startTime<${new Date(startTo).getTime()}`);
    }
    if (!_isEmpty(startFrom) && _isEmpty(startTo)) {
      clauses.push(`startTime<${new Date().getTime()}`);
    }
    return {
      query: clauses.join(" AND "),
      freeText: "*",
    };
  }

  function doSearch() {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    // Only force refetch if query didn't change. Else let react-query detect difference and refetch automatically
    if (_isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
  }

  // hotkeys to search scheduler execution
  useHotkeys(`${Key.Meta}+${Key.Enter}`, doSearch, {
    enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
  });

  const handlePage = (page: number) => {
    setPage(page);
  };

  const handleSort = (changedColumn: string, direction: string) => {
    const sort = `${changedColumn}:${direction.toUpperCase()}`;
    setPage(1);
    setSort(sort);
    doSearch();
  };

  const handleRowsPerPage = (rowsPerPage: number) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

  const handleLookback = (val: string) => {
    setStartFrom("");
    setStartTo("");
    setLookback(val);
  };

  const onStartFromChange = (val: string) => {
    setLookback("");
    setStartFrom(val);
  };

  const onStartToChange = (val: string) => {
    setLookback("");
    setStartTo(val);
  };

  if (error?.status === 401) {
    const readJsonResponse = async () => {
      try {
        const json = await error.json();
        setUnauthorized(json);
      } catch {
        setUnauthorized(null);
      }
    };
    readJsonResponse();
  }

  if (unauthorized) {
    if (unauthorized?.message) {
      return (
        <Navigate
          to={`${ERROR_URL}?message=${unauthorized?.message}&error=${unauthorized?.error}`}
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
    setWorkflowType([]);
    setScheduleName([]);
    setExecutionId("");
    setStatus([]);
    setLookback("");
    setStartFrom("");
    setStartTo("");
  };

  const handleReset = () => {
    clearAllFields();
    const newQueryFT = { query: "", freeText: "*" };
    setQueryFT(newQueryFT);
    refetch();
  };

  return (
    <>
      <Helmet>
        <title>Scheduled Workflow Executions</title>
      </Helmet>
      {showCodeDialog && (
        <SchedulerApiSearchModal
          onClose={() => setShowCodeDialog("")}
          buildQueryOutput={{
            start: (page - 1) * rowsPerPage,
            size: rowsPerPage,
            sort,
            freeText: queryFT.freeText,
            query: buildQuery().query,
          }}
        />
      )}
      <SectionHeader
        _deprecate_marginTop={0}
        title="Scheduled Workflow Executions"
      />
      <SectionContainer>
        <Paper variant="outlined" sx={{ marginBottom: 6 }}>
          <Grid container sx={{ width: "100%" }} spacing={3} p={6}>
            <Grid
              size={{
                xs: 6,
                md: 4,
              }}
            >
              <ConductorAutoComplete
                fullWidth
                label="Schedule name"
                options={scheduleNames}
                multiple
                freeSolo
                onChange={(__, val: string[]) => setScheduleName(val)}
                value={scheduleName}
                conductorInputProps={{
                  autoFocus: true,
                }}
              />
            </Grid>

            <Grid
              size={{
                xs: 12,
                sm: 8,
                md: 4,
              }}
            >
              <ConductorAutoComplete
                fullWidth
                label="Workflow name"
                options={workflowNames}
                multiple
                freeSolo
                onChange={(__, val: string[]) => setWorkflowType(val)}
                value={workflowType}
              />
            </Grid>

            <Grid
              size={{
                xs: 12,
                sm: 8,
                md: 4,
              }}
            >
              <ConductorInput
                fullWidth
                label="Scheduler execution id"
                value={executionId}
                onTextInputChange={setExecutionId}
                showClearButton
              />
            </Grid>

            <Grid
              size={{
                xs: 12,
                sm: 8,
                md: 5,
              }}
            >
              <ConductorDateRangePicker
                disabled={!_isEmpty(lookback)}
                labelFrom="Start time - from"
                labelTo="Start time - to"
                from={startFrom ? new Date(startFrom) : null}
                to={startTo ? new Date(startTo) : null}
                onFromChange={onStartFromChange}
                onToChange={onStartToChange}
              />
            </Grid>

            <Grid
              size={{
                xs: 4,
                sm: 4,
                md: 2,
              }}
            >
              <ConductorInput
                fullWidth
                label="Lookback (days)"
                value={lookback}
                onTextInputChange={handleLookback}
                type="number"
                showClearButton
                disabled={!_isEmpty(startFrom) || !_isEmpty(startTo)}
              />
            </Grid>

            <Grid
              size={{
                xs: 4,
                md: 2,
              }}
            >
              <ConductorAutoComplete
                fullWidth
                label="Status"
                options={scheduleStatuses}
                multiple
                onChange={(__, val: string[]) => setStatus(val)}
                value={status}
                renderTags={renderStatusTagChip}
                renderOption={(props, option) => (
                  <Box component="li" {...props}>
                    <StatusBadge status={option} />
                  </Box>
                )}
              />
            </Grid>

            <Grid
              alignSelf="center"
              size={{
                xs: 12,
                sm: 2,
                md: 1,
                lg: 1,
              }}
            >
              <Button
                id="reset-workflow-btn"
                variant="text"
                onClick={handleReset}
                style={{ width: "100%" }}
                startIcon={<ResetIcon />}
              >
                Reset
              </Button>
            </Grid>

            <Grid
              alignSelf="center"
              size={{
                xs: 12,
                sm: 2,
                md: 2,
              }}
            >
              <SplitButton
                startIcon={<SearchIcon />}
                options={[
                  {
                    label: "Show as code",
                    onClick: () => setShowCodeDialog("active"),
                  },
                ]}
                primaryOnClick={doSearch}
              >
                Search
              </SplitButton>
            </Grid>
          </Grid>
        </Paper>
        <SchedulerResultsTable
          resultObj={resultObj}
          error={error}
          busy={isFetching}
          page={page}
          rowsPerPage={rowsPerPage}
          setPage={handlePage}
          setSort={handleSort}
          refetchExecution={refetch}
          errorMessage={errorMessage}
          handleError={handleError}
          handleClearError={handleClearError}
          isFilterOn={queryFT?.query !== "" ? true : false}
          handleReset={handleReset}
          setRowsPerPage={handleRowsPerPage}
        />
      </SectionContainer>
    </>
  );
}
