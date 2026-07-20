import {
  Box,
  FormControl,
  Grid,
  InputLabel,
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
import { ReactNode, useCallback, useMemo, useState } from "react";
import { useHotkeys } from "react-hotkeys-hook";
import { Navigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { Key } from "ts-key-enum";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { useLocalStorage } from "utils";
import { ERROR_URL } from "utils/constants/route";
import { useAgentNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import { DateControlComponent } from "../DateControlComponent";
import ResultsTable from "../ResultsTable";
import {
  toDateControlProps,
  useAgentSearchFilters,
} from "../useAgentSearchFilters";
import {
  buildBasicSearchQuery,
  isWorkflowIdQuery,
  mergeUniqueSortedNames,
} from "./buildBasicSearchQuery";

/** Shape shown in the results snackbar (from getErrors or fetch Response). */
type ExecutionSearchError = {
  message?: string;
  statusText?: string;
} & Record<string, unknown>;

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
}

export default function BasicSearch({
  doSearch,
  SwitchComponent,
  getTableTitle,
}: BasicSearchProps) {
  const filters = useAgentSearchFilters();
  const {
    freeText,
    setFreeText,
    status,
    setStatus,
    startTimeFrom,
    setStartTimeFrom,
    startTimeTo,
    setStartTimeTo,
    endTimeFrom,
    setEndTimeFrom,
    endTimeTo,
    setEndTimeTo,
    setFromDisplayTime,
    setToDisplayTime,
  } = filters;
  const [page, setPage] = useQueryState("page", 1);
  const [workflowType, setWorkflowType] = useQueryState<string[]>(
    "workflowType",
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

  const [agentNameInput, setAgentNameInput] = useState("");
  // Local draft so keystrokes do not rewrite the URL on every change.
  // When committed freeText changes (reset, back/forward, Advanced↔Basic),
  // sync during render instead of an effect — see React "adjusting state
  // when a prop changes".
  const [searchInput, setSearchInput] = useState(freeText);
  const [prevFreeText, setPrevFreeText] = useState(freeText);
  if (freeText !== prevFreeText) {
    setPrevFreeText(freeText);
    setSearchInput(freeText);
  }

  const isMobile = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );

  // Registered agent definitions (often empty if nothing is deployed via AgentSpan).
  const registeredAgentNames = useAgentNames();

  // Stable lookback so the hint query key does not change every render.
  const [nameHintLookbackMs] = useState(
    () => Date.now() - 30 * 24 * 60 * 60 * 1000,
  );

  // Supplement the dropdown with names seen on recent agent executions so the
  // select is useful even when /agent/list returns [].
  const { data: agentNameHintResult } = useWorkflowSearch<{
    results?: { workflowType?: string }[];
  }>(
    {
      page: 1,
      rowsPerPage: 100,
      sort: DEFAULT_SORT,
      query: `startTime>${nameHintLookbackMs}`,
      freeText: "*",
      classifier: "agent",
      topLevelOnly: true,
    },
    { staleTime: 60_000 },
  );

  const knownAgentNames = useMemo(() => {
    const fromExecutions =
      agentNameHintResult?.results
        ?.map((row) => row.workflowType)
        .filter((name): name is string => Boolean(name)) ?? [];
    return mergeUniqueSortedNames(registeredAgentNames, fromExecutions);
  }, [registeredAgentNames, agentNameHintResult]);

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
    setAgentNameInput("");
    setStatus([]);
    setStartTimeFrom("");
    setStartTimeTo("");
    setSearchInput("");
    setFreeText("");
    setModifiedFrom("");
    setModifiedTo("");
    setEndTimeFrom("");
    setEndTimeTo("");
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

  const [errorMessage, setErrorMessage] = useState<ExecutionSearchError | null>(
    null,
  );

  const [unauthorized, setUnauthorized] = useState<{
    message?: string;
    error?: string;
  } | null>(null);

  // Include typed-but-not-committed agent name so Search works without Enter
  const resolvedWorkflowTypes = useCallback(() => {
    const types = [...workflowType];
    const pending = agentNameInput.trim();
    if (pending && !types.includes(pending)) {
      types.push(pending);
    }
    return types;
  }, [workflowType, agentNameInput]);

  const commitPendingAgentName = useCallback(() => {
    const pending = agentNameInput.trim();
    if (pending && !workflowType.includes(pending)) {
      setWorkflowType([...workflowType, pending]);
    }
    setAgentNameInput("");
  }, [agentNameInput, workflowType, setWorkflowType]);

  const buildQuery = useCallback(
    () =>
      buildBasicSearchQuery({
        searchInput,
        selectedTypes: resolvedWorkflowTypes(),
        knownAgentNames,
        status,
        startTimeFrom,
        startTimeTo,
        endTimeFrom,
        endTimeTo,
        modifiedFrom,
        modifiedTo,
      }),
    [
      searchInput,
      startTimeFrom,
      startTimeTo,
      status,
      resolvedWorkflowTypes,
      modifiedFrom,
      modifiedTo,
      endTimeFrom,
      endTimeTo,
      knownAgentNames,
    ],
  );

  const [queryFT, setQueryFT] = useState(buildQuery);
  const [hideSubWorkflows, setHideSubWorkflows] = useState(true);

  // Direct execution-id lookup should not also require classifier=agent /
  // topLevelOnly — older or mis-tagged rows would otherwise return 0 hits.
  const isExecutionIdQuery = isWorkflowIdQuery(queryFT.query);

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
      // Scope results to agent executions unless looking up a specific id.
      ...(isExecutionIdQuery
        ? {}
        : {
            classifier: "agent",
            topLevelOnly: hideSubWorkflows,
          }),
    },
    {},
    {
      onError: (error: unknown) => {
        if (error) {
          getErrors(error as Response).then((result) => {
            if (result?.["workflowName"] === "must not be empty") {
              setErrorMessage({ message: "Agent name should not be empty" });
            } else {
              setErrorMessage(result as ExecutionSearchError);
            }
          });
        } else {
          setErrorMessage(null);
        }
      },
    },
  );

  // Dropdown options: registered defs + recent executions + names from current results
  const agentNameOptions = useMemo(() => {
    const fromResults =
      resultObj?.results
        ?.map((row: { workflowType?: string }) => row.workflowType)
        .filter((name: string | undefined): name is string => Boolean(name)) ??
      [];
    return mergeUniqueSortedNames(knownAgentNames, fromResults);
  }, [knownAgentNames, resultObj]);

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

  const runSearch = () => {
    setFreeText(searchInput);
    commitPendingAgentName();
    doSearch({
      resultObj,
      queryFT,
      buildQuery,
      setQueryFT,
      refetch,
      setPage,
      setRecentTaskSearch,
    });
  };

  // hotkeys to search execution
  useHotkeys(`${Key.Meta}+${Key.Enter}`, () => runSearch(), {
    enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
  });

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

  if (error?.status === 401) {
    const errorResult = error;
    const parseErrorResponse = async () => {
      try {
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

  const handleError = (error: ExecutionSearchError) => {
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
                freeText: buildQuery().freeText,
                query: buildQuery().query,
              }}
            />
          )}
          <Grid
            container
            sx={{ width: "100%" }}
            spacing={3}
            pt={2}
            alignItems="flex-end"
          >
            <Grid
              size={{
                xs: 12,
                md: 7,
                lg: 8,
              }}
            >
              <ConductorInput
                id="workflow-search-combined"
                fullWidth
                label="Search"
                placeholder="Full execution ID, correlation ID, idempotency key, or agent name"
                value={searchInput}
                onTextInputChange={setSearchInput}
                showClearButton
                autoFocus
                onKeyDown={(event) => {
                  if (event.key === Key.Enter) {
                    event.preventDefault();
                    runSearch();
                  }
                }}
                tooltip={{
                  title: "General search",
                  content:
                    "Matches a full execution ID (UUID), correlation ID, idempotency key, and free text. Partial agent names match known agents (e.g. aa finds aaaa). Use a wildcard (e.g. my-*) when the agent is not in the known list yet.",
                }}
              />
            </Grid>
            <Grid
              size={{
                xs: 12,
                md: 5,
                lg: 4,
              }}
            >
              <ConductorAutoComplete
                id="workflow-search-name-dropdown"
                fullWidth
                label="Agent name"
                placeholder="Select agents…"
                options={agentNameOptions}
                multiple
                freeSolo
                openOnFocus
                onInputChange={(__, val: string, reason: string) => {
                  // Track typed text for Search-without-Enter, but do not
                  // control inputValue — that breaks the options dropdown.
                  if (reason === "input" || reason === "clear") {
                    setAgentNameInput(val);
                  }
                  if (reason === "reset") {
                    setAgentNameInput("");
                  }
                }}
                onChange={(__, val: string[]) => {
                  setAgentNameInput("");
                  setWorkflowType(val);
                }}
                value={workflowType}
                autoFocus
                conductorInputProps={{
                  tooltip: {
                    title: "Filter by agent",
                    content:
                      "Select from registered agents and recently run agent names. You can also type a name or wildcard (e.g. my-agen* or *bot*) and press Enter to add it as a filter — this is not a free-text search.",
                    placement: "top",
                    showInitial: !tooltipFlags.executionSearch ? true : false,
                    initialTimeout: 2000,
                    onClose: handleToolTipOnClose,
                  },
                  autoFocus: true,
                  placeholder: "Select agents…",
                }}
              />
            </Grid>
            <Grid
              size={{
                xs: 12,
                sm: 6,
                md: 3,
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
                sm: 6,
                md: 5,
                lg: 6,
              }}
            >
              <DateControlComponent
                {...toDateControlProps(filters)}
                startDialogTitle="Execution Start Time"
                startDialogHelpText="Select a date range within which the Execution has started."
                endDialogTitle="Execution End Time"
                endDialogHelpText="Select a date range within which the Execution has ended."
                startTimeLabel="Execution Start Time"
                endTimeLabel="Execution End Time"
              />
            </Grid>
            <Grid
              display="flex"
              justifyContent="end"
              alignItems="end"
              gap={1}
              size={{
                xs: 12,
                md: 4,
                lg: 4,
              }}
            >
              <FormControl>
                {!isMobile && <InputLabel>&nbsp;</InputLabel>}
                <Button
                  id="reset-workflow-btn"
                  variant="text"
                  onClick={handleReset}
                  startIcon={<ResetIcon />}
                >
                  Reset
                </Button>
              </FormControl>
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
                  primaryOnClick={runSearch}
                >
                  Search
                </SplitButton>
              </FormControl>
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
        hideSubWorkflows={hideSubWorkflows}
        setHideSubWorkflows={(value: boolean) => {
          setHideSubWorkflows(value);
          setPage(1);
        }}
      />
    </>
  );
}
