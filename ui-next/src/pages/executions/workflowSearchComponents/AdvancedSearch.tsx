import { Monaco } from "@monaco-editor/react";
import { Box } from "@mui/material";
import { Button, Paper } from "components";
import ResetIcon from "components/icons/ResetIcon";
import SearchIcon from "components/icons/SearchIcon";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import MuiTypography from "components/ui/MuiTypography";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
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
import ResultsTable from "../ResultsTable";
import { ExampleSearchQuery } from "../SearchExampleQuery";
import { DatePickerButton, DatePickerButtonHandle } from "./DatePickerButton";
import { StatusComboButton } from "./StatusComboButton";

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
  recentSearches,
}: AdvancedSearchProps) {
  const disposeRef = useRef<null | (() => void)>(null);
  const startPickerRef = useRef<DatePickerButtonHandle>(null);
  const endPickerRef = useRef<DatePickerButtonHandle>(null);

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

  type QueryOverrides = Partial<{
    status: string[];
    startTimeFrom: string;
    startTimeTo: string;
    endTimeFrom: string;
    endTimeTo: string;
    freeText: string;
  }>;

  const buildQuery = useCallback(
    (overrides: QueryOverrides = {}) => {
      const _status = overrides.status ?? status;
      const _startTimeFrom = overrides.startTimeFrom ?? startTimeFrom;
      const _startTimeTo = overrides.startTimeTo ?? startTimeTo;
      const _endTimeFrom = overrides.endTimeFrom ?? endTimeFrom;
      const _endTimeTo = overrides.endTimeTo ?? endTimeTo;
      const _freeText = overrides.freeText ?? freeText;

      const clauses = [];

      if (!_isEmpty(_status) && !queryText.includes("status")) {
        clauses.push(`status IN (${_status.join(",")})`);
      }

      if (!queryText.includes("startTime")) {
        if (!_isEmpty(_startTimeFrom)) {
          clauses.push(`startTime>${dateToEpoch(_startTimeFrom)}`);
        }
      }

      if (!_isEmpty(_startTimeTo)) {
        clauses.push(`startTime<${dateToEpoch(_startTimeTo)}`);
      }
      if (!_isEmpty(_endTimeFrom)) {
        clauses.push(`endTime>${dateToEpoch(_endTimeFrom)}`);
      }
      if (!_isEmpty(_endTimeTo)) {
        clauses.push(`endTime<${dateToEpoch(_endTimeTo)}`);
      }

      if (!_isEmpty(queryText)) {
        clauses.push(queryText);
      }

      return {
        query: clauses.join(" AND "),
        freeText: _isEmpty(_freeText) ? "*" : _freeText,
      };
    },
    [
      freeText,
      startTimeFrom,
      startTimeTo,
      endTimeFrom,
      endTimeTo,
      status,
      queryText,
    ],
  );

  const [queryFT, setQueryFT] = useState(buildQuery);
  // Derive the live query directly from props so useWorkflowSearch refetches
  // automatically whenever any filter changes.
  const currentQuery = useMemo(() => buildQuery(), [buildQuery]);
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
      query: currentQuery.query,
      freeText: currentQuery.freeText,
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
    return currentQuery.query !== "" || currentQuery.freeText !== "*";
  }, [currentQuery]);

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

  const triggerSearchWith = (overrides: QueryOverrides = {}) => {
    setPage(1);
    const newQueryFT = buildQuery(overrides);
    setQueryFT(newQueryFT);
    if (_isEqual(queryFT, newQueryFT)) {
      refetch();
    }
    setRecentTaskSearch();
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

        <Box sx={SwitchComponent ? { pt: 0, px: 4, pb: 3 } : { p: 4 }}>
          {/* SQL editor */}
          <Box sx={{ mt: 3, mb: 2 }}>
            <ConductorCodeBlockInput
              label="SQL Query"
              language="sql"
              minHeight={60}
              value={queryText}
              onChange={setQueryText}
              autoFocus
              options={{ lineNumbers: "off" }}
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
                      const propertySuggestions = propertyKeys.map(
                        (property) => ({
                          label: property,
                          kind: monaco.languages.CompletionItemKind.Value,
                          insertText: property,
                        }),
                      );
                      return { suggestions: [...propertySuggestions] };
                    },
                  });
                // IMPORTANT: keep `dispose()` bound to its disposable context.
                // Destructuring `dispose` can lose `this` and throw "Unbound disposable context".
                disposeRef.current = () => disposable.dispose();
              }}
            />
          </Box>

          {/* Filters: Status · Start Time · End Time · Free Text */}
          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              columnGap: 1,
              rowGap: 2.5,
              alignItems: "center",
              pt: 2.5,
            }}
          >
            <StatusComboButton
              status={status}
              disabled={queryText.includes("status")}
              onStatusChange={(val) => {
                setStatus(val);
                triggerSearchWith({ status: val });
              }}
            />

            <DatePickerButton
              ref={startPickerRef}
              onOpen={() => endPickerRef.current?.close()}
              startTime={startTimeFrom}
              onStartFromChange={(val) => {
                onStartFromChange(val);
                setPage(1);
              }}
              startTimeEnd={startTimeTo}
              onStartToChange={(val) => {
                onStartToChange(val);
                setPage(1);
              }}
              fromDisplayTime={fromDisplayTime}
              setFromDisplayTime={setFromDisplayTime}
              recentSearches={recentSearches}
              startTimeLabel="Start Time"
              disabled={queryText.includes("startTime")}
            />

            <DatePickerButton
              ref={endPickerRef}
              onOpen={() => startPickerRef.current?.close()}
              startTime={endTimeFrom}
              onStartFromChange={(val) => {
                onEndFromChange(val);
                setPage(1);
              }}
              startTimeEnd={endTimeTo}
              onStartToChange={(val) => {
                onEndToChange(val);
                setPage(1);
              }}
              fromDisplayTime={toDisplayTime}
              setFromDisplayTime={setToDisplayTime}
              recentSearches={recentSearches}
              startTimeLabel="End Time"
              disabled={queryText.includes("endTime")}
            />

            <ConductorInput
              label="Free text"
              value={freeText}
              onTextInputChange={(val) => {
                setFreeText(val);
                triggerSearchWith({ freeText: val });
              }}
              showClearButton
              size="small"
              sx={{
                flexGrow: 1,
                minWidth: 220,
                ".MuiOutlinedInput-root": { height: 36 },
              }}
            />
          </Box>

          {/* Footer: Reset All + Search */}
          <Box
            sx={{
              display: "flex",
              justifyContent: "flex-end",
              alignItems: "center",
              gap: 1,
              mt: 3,
            }}
          >
            <Button
              id="reset-workflow-btn"
              variant="text"
              size="small"
              onClick={handleReset}
              startIcon={<ResetIcon />}
              sx={{ textTransform: "none" }}
            >
              Reset All
            </Button>

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
          </Box>
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
