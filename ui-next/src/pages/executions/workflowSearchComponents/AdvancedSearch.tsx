import { Monaco } from "@monaco-editor/react";
import { Box, Grid } from "@mui/material";
import { Button, Paper } from "components";
import { DEFAULT_ROWS_PER_PAGE } from "components/DataTable/DataTable";
import MuiTypography from "components/MuiTypography";
import StatusBadge from "components/StatusBadge";
import { renderStatusTagChip } from "components/StatusTagChip";
import { ConductorAutoComplete } from "components/v1";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import ConductorInput from "components/v1/ConductorInput";
import SplitButton from "components/v1/ConductorSplitButton";
import ResetIcon from "components/v1/icons/ResetIcon";
import SearchIcon from "components/v1/icons/SearchIcon";
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
import { colors } from "theme/tokens/variables";
import { Key } from "ts-key-enum";
import { IObject } from "types/common";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { dateToEpoch, useLocalStorage } from "utils";
import { WORKFLOW_SEARCH_QUERY_SUGGESTIONS } from "utils/constants/common";
import { ERROR_URL } from "utils/constants/route";
import { useWorkflowNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import { DateControlComponent } from "../DateControlComponent";
import ResultsTable from "../ResultsTable";
import { ExampleSearchQuery } from "../SearchExampleQuery";

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
  openDateSelect: boolean;
  setOpenDateSelect: (val: boolean) => void;
  openStartDatePicker: boolean;
  setStartOpenDatePicker: (val: boolean) => void;
  openEndDatePicker: boolean;
  setEndOpenDatePicker: (val: boolean) => void;
  recentSearches: { start: string; end: string };
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
  openDateSelect,
  setOpenDateSelect,
  openStartDatePicker,
  setStartOpenDatePicker,
  openEndDatePicker,
  setEndOpenDatePicker,
  recentSearches,
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

  // for tooltip flag in localstorage
  const [tooltipFlags, setTooltipFlags] = useLocalStorage("tooltipFlags", {});
  const handleToolTipOnClose = () => {
    if (tooltipFlags && !tooltipFlags.executionSearch) {
      setTooltipFlags({ ...tooltipFlags, executionSearch: true });
    }
  };

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

  return (
    <>
      <Paper variant="outlined" sx={{ marginBottom: 6 }}>
        {SwitchComponent}
        <Box>
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
          <Grid container sx={{ width: "100%" }} spacing={3} p={6} pt={2}>
            <Grid size={12}>
              <ConductorCodeBlockInput
                label="Search"
                language="sql"
                minHeight={30}
                value={queryText}
                onChange={setQueryText}
                autoFocus
                options={{
                  lineNumbers: "off",
                }}
                tooltip={{
                  placement: "top",
                  title: "Search",
                  content: (
                    <Box>
                      Search workflow execution by query parameters. Then hit
                      ENTER, and now you can click SEARCH.
                      <Box
                        sx={{
                          border: "1px solid lightgrey",
                          padding: 2,
                          color: colors.black,
                          borderRadius: "4px",
                          marginTop: 1,
                          fontWeight: 400,
                        }}
                      >
                        <MuiTypography fontWeight={400} color={colors.greyText}>
                          Sample:
                        </MuiTypography>
                        <ExampleSearchQuery />
                      </Box>
                    </Box>
                  ),
                  showInitial: !tooltipFlags.executionSearch,
                  initialTimeout: 2000,
                  onClose: handleToolTipOnClose,
                }}
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
            </Grid>

            <Grid
              size={{
                xs: 12,
                sm: 12,
                md: 5.5,
                lg: 5,
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
                disabled={
                  queryText.includes("startTime") ||
                  queryText.includes("endTime")
                }
                recentSearches={recentSearches}
                startTimeLabel="Execution Start Time"
                endTimeLabel="Execution End Time"
              />
            </Grid>

            <Grid
              size={{
                xs: 12,
                sm: 12,
                md: 2,
                lg: 2.5,
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
              size={{
                xs: 12,
                sm: 6,
                md: 2,
                lg: 2,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-status"
                label="Status"
                disabled={queryText.includes("status")}
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
              justifyContent="end"
              size={{
                xs: 12,
                sm: 6,
                md: 2.5,
                lg: 2.5,
              }}
            >
              <Grid alignSelf="center" size={5}>
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
              <Grid alignSelf="center">
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
