import {
  Box,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  Switch,
  useMediaQuery,
} from "@mui/material";
import { Theme } from "@mui/material/styles";
import { Button, Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import StatusBadge from "components/StatusBadge";
import { renderStatusTagChip } from "components/StatusTagChip";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import ResetIcon from "components/icons/ResetIcon";
import SearchIcon from "components/icons/SearchIcon";
import _isEmpty from "lodash/isEmpty";
import { ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import { useHotkeys } from "react-hotkeys-hook";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { Key } from "ts-key-enum";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { IObject } from "types/common";
import { dateToEpoch, useLocalStorage } from "utils";
import { ERROR_URL } from "utils/constants/route";
import { useAutoCompleteInputValidation } from "utils/hooks/useAutoCompleteInputValidation";
import { useWorkflowNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import { DateControlComponent } from "../DateControlComponent";
import ResultsTable from "../ResultsTable";

const DEFAULT_SORT = "startTime:DESC";
const workflowStatuses = Object.values(WorkflowExecutionStatus);

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
  openDateSelect: boolean;
  setOpenDateSelect: (val: boolean) => void;
  openStartDatePicker: boolean;
  setStartOpenDatePicker: (val: boolean) => void;
  openEndDatePicker: boolean;
  setEndOpenDatePicker: (val: boolean) => void;
  excludeSubWorkflows: boolean;
  setExcludeSubWorkflows: (val: boolean) => void;
  recentSearches: { start: string; end: string };
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
  openDateSelect,
  setOpenDateSelect,
  openStartDatePicker,
  setStartOpenDatePicker,
  openEndDatePicker,
  setEndOpenDatePicker,
  excludeSubWorkflows,
  setExcludeSubWorkflows,
  recentSearches,
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

  const [modifiedFrom, setModifiedFrom] = useQueryState("modifiedFrom", "");
  const [modifiedTo, setModifiedTo] = useQueryState("modifiedTo", "");

  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE,
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");

  const {
    setValue: setCorrelationInputVal,
    setFocused: setCorrelationFieldFocus,
    hasError: correlationIdHasError,
  } = useAutoCompleteInputValidation();

  const {
    setValue: setIdempotencyKeyInputVal,
    setFocused: setIdempotencyKeyFieldFocus,
    hasError: idempotencyKeyHasError,
  } = useAutoCompleteInputValidation();

  const isMobile = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );

  // For dropdown
  const workflowNames: string[] = useWorkflowNames();

  // for tooltip flag in localstorage
  const [tooltipFlags, setTooltipFlags] = useLocalStorage("tooltipFlags", {});
  const handleToolTipOnClose = () => {
    if (tooltipFlags && !tooltipFlags.executionSearch) {
      setTooltipFlags({ ...tooltipFlags, executionSearch: true });
    }
  };

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
    setExcludeSubWorkflows(false);
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
    if (!_isEmpty(workflowId)) {
      clauses.push(`workflowId='${workflowId}'`);
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

    if (excludeSubWorkflows) {
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
    excludeSubWorkflows,
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
    const sortColumn =
      changedColumn === "workflowType" ? "workflowName" : changedColumn;
    const newSort = `${sortColumn}:${direction.toUpperCase()}`;

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
        {SwitchComponent}
        <Box sx={{ padding: SwitchComponent ? "0 24px 24px 24px" : 6 }}>
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
          <Grid
            container
            sx={{ width: "100%" }}
            spacing={3}
            pt={2}
            justifyContent="flex-end"
          >
            <Grid
              size={{
                xs: 6,
                md: 6,
                lg: 4,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-name-dropdown"
                fullWidth
                label="Workflow name"
                options={workflowNames.sort((a, b) =>
                  a.toLowerCase().localeCompare(b.toLowerCase()),
                )}
                multiple
                freeSolo
                onChange={(__, val: string[]) => setWorkflowType(val)}
                value={workflowType}
                autoFocus
                conductorInputProps={{
                  tooltip: {
                    title: "Partial Name Search",
                    content:
                      "Search workflows by partial names with a wildcard * in your keyword. Then hit ENTER, and now you can click SEARCH. i.e. Workfl* or *orkfl*w",
                    placement: "top",
                    showInitial: !tooltipFlags.executionSearch ? true : false,
                    initialTimeout: 2000,
                    onClose: handleToolTipOnClose,
                  },
                  autoFocus: true,
                }}
              />
            </Grid>
            <Grid
              size={{
                xs: 6,
                md: 6,
                lg: 2,
              }}
            >
              <ConductorInput
                id="workflow-search-id"
                fullWidth
                label="Workflow id"
                value={workflowId}
                onTextInputChange={setWorkflowId}
                showClearButton
              />
            </Grid>
            <Grid
              position="relative"
              size={{
                xs: 6,
                md: 6,
                lg: 2,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-correlation-id"
                fullWidth
                label="Correlation id"
                options={[]}
                multiple
                freeSolo
                onTextInputChange={(typingValue: string) => {
                  setCorrelationInputVal(typingValue);
                }}
                onChange={(evt: any, val: string[]) => {
                  if (evt.key === "Backspace" || evt.key === "Enter") {
                    setCorrelationInputVal("");
                  }
                  setCorrelationIds(val);
                }}
                onFocus={() => setCorrelationFieldFocus(true)}
                onBlur={() => setCorrelationFieldFocus(false)}
                value={correlationIds}
                error={correlationIdHasError}
                conductorInputProps={{
                  tooltip: {
                    title: "Get Workflows by Correlation ID",
                    content:
                      "Search workflows by Correlation ID. This field has support for multiple values, so please remember to press 'Enter' for each value to apply the search.",
                  },
                  error: correlationIdHasError,
                }}
              />
            </Grid>
            <Grid
              position="relative"
              size={{
                xs: 6,
                md: 6,
                lg: 2,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-idempotency-key"
                fullWidth
                label="Idempotency key"
                options={[]}
                multiple
                freeSolo
                onTextInputChange={(typingValue: string) => {
                  setIdempotencyKeyInputVal(typingValue);
                }}
                onChange={(evt: any, val: string[]) => {
                  if (evt.key === "Backspace" || evt.key === "Enter") {
                    setIdempotencyKeyInputVal("");
                  }

                  setIdempotencyKey(val);
                }}
                onFocus={() => setIdempotencyKeyFieldFocus(true)}
                onBlur={() => setIdempotencyKeyFieldFocus(false)}
                value={idempotencyKey}
                error={idempotencyKeyHasError}
                conductorInputProps={{
                  tooltip: {
                    title: "Get Workflows by Idempotency key",
                    content:
                      "Search workflows by Idempotency key. This field has support for multiple values, so please remember to press 'Enter' for each value to apply the search.",
                  },
                  error: idempotencyKeyHasError,
                }}
              />
            </Grid>
            <Grid
              size={{
                xs: 12,
                md: 6,
                lg: 2,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-status"
                label="Status"
                fullWidth
                options={workflowStatuses}
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
              display="flex"
              alignItems="end"
              size={{
                xs: 12,
                sm: 12,
                md: 6,
                lg: 6,
              }}
            >
              <DateControlComponent
                startTime={startTimeFrom}
                onStartFromChange={onStartFromChange}
                startTimeEnd={startTimeTo}
                onStartToChange={onStartToChange}
                endTimeStart={endTimeFrom}
                onEndFromChange={onEndFromChange}
                endTime={endTimeTo}
                onEndToChange={onEndToChange}
                fromDisplayTime={fromDisplayTime}
                setFromDisplayTime={setFromDisplayTime}
                toDisplayTime={toDisplayTime}
                setToDisplayTime={setToDisplayTime}
                openDateSelect={openDateSelect}
                setOpenDateSelect={setOpenDateSelect}
                openStartDatePicker={openStartDatePicker}
                setStartOpenDatePicker={setStartOpenDatePicker}
                openEndDatePicker={openEndDatePicker}
                setEndOpenDatePicker={setEndOpenDatePicker}
                recentSearches={recentSearches}
                startDialogTitle="Execution Start Time"
                startDialogHelpText="Select a date range within which the Workflow Execution has started."
                endDialogTitle="Execution End Time"
                endDialogHelpText="Select a date range within which the Workflow Execution has ended."
                startTimeLabel="Execution Start Time"
                endTimeLabel="Execution End Time"
              />
            </Grid>
            <Grid
              size={{
                xs: 12,
                sm: 6,
                md: 6,
                lg: 2,
              }}
            >
              <ConductorInput
                fullWidth
                label="Free text search"
                value={freeText}
                onTextInputChange={setFreeText}
                showClearButton
              />
            </Grid>
            <Grid
              display="flex"
              alignItems="end"
              size={{
                xs: 12,
                sm: 6,
                md: 3,
                lg: 2,
              }}
            >
              <FormControlLabel
                sx={{ whiteSpace: "nowrap", mb: 0.5 }}
                control={
                  <Switch
                    color="primary"
                    checked={excludeSubWorkflows}
                    onChange={(e) => setExcludeSubWorkflows(e.target.checked)}
                    size="small"
                  />
                }
                label="Exclude sub-workflows"
                slotProps={{
                  typography: { variant: "body2" },
                }}
              />
            </Grid>
            <Grid
              display="flex"
              justifyContent="end"
              size={{
                xs: 12,
                sm: 6,
                md: 3,
                lg: 2,
              }}
            >
              <Grid size={5}>
                <FormControl>
                  {!isMobile && <InputLabel>&nbsp;</InputLabel>}
                  <Button
                    id="reset-workflow-btn"
                    variant="text"
                    onClick={handleReset}
                    style={{ width: "100%" }}
                    startIcon={<ResetIcon />}
                  >
                    Reset
                  </Button>
                </FormControl>
              </Grid>
              <Grid>
                <FormControl>
                  {!isMobile && <InputLabel>&nbsp;</InputLabel>}

                  <SplitButton
                    id="search-workflow-btn"
                    startIcon={<SearchIcon />}
                    options={[
                      {
                        label: "Show as code",
                        onClick: () => setShowCodeDialog("active"),
                      },
                    ]}
                    primaryOnClick={() =>
                      doSearch({
                        resultObj,
                        queryFT,
                        buildQuery,
                        setQueryFT,
                        refetch,
                        setPage,
                        setRecentTaskSearch,
                      })
                    }
                  >
                    Search
                  </SplitButton>
                </FormControl>
              </Grid>
            </Grid>
          </Grid>
        </Box>
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
