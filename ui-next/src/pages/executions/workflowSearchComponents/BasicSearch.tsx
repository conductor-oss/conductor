import { Box, Paper } from "@mui/material";
import { DEFAULT_ROWS_PER_PAGE } from "components/ui/DataTable/DataTable";
import { atom, useAtom } from "jotai";
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
import { Key } from "ts-key-enum";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { IObject } from "types/common";
import { dateToEpoch, useLocalStorage } from "utils";
import { ERROR_URL } from "utils/constants/route";
import { useAutoCompleteInputValidation } from "utils/hooks/useAutoCompleteInputValidation";
import { useWorkflowNames, useWorkflowSearch } from "utils/query";
import { getErrors } from "utils/utils";
import { ApiSearchModalIntegration } from "../ApiSearchModalIntegration";
import ResultsTable from "../ResultsTable";
import { DatePickerButtonHandle } from "./DatePickerButton";
import { MoreFiltersPopover } from "./MoreFiltersPopover";
import { QuickFiltersCard } from "./QuickFiltersCard";
import { WorkflowSearchBar } from "./WorkflowSearchBar";

const DEFAULT_SORT = "startTime:DESC";

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
  excludeSubWorkflows: boolean;
  setExcludeSubWorkflows: (val: boolean) => void;
  recentSearches: { start: string; end: string };
}

// Module-level atoms for MoreFiltersPopover draft state
const workflowIdDraftAtom = atom("");
const freeTextDraftAtom = atom("");
const idempotencyKeyDraftAtom = atom<string[]>([]);
const excludeSubWorkflowsDraftAtom = atom(false);

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

  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null);

  // Jotai draft atoms — synced to applied values in the open-button onClick, not via useEffect
  const [workflowIdDraft, setWorkflowIdDraft] = useAtom(workflowIdDraftAtom);
  const [freeTextDraft, setFreeTextDraft] = useAtom(freeTextDraftAtom);
  const [idempotencyKeyDraft, setIdempotencyKeyDraft] = useAtom(
    idempotencyKeyDraftAtom,
  );
  const [excludeSubWorkflowsDraft, setExcludeSubWorkflowsDraft] = useAtom(
    excludeSubWorkflowsDraftAtom,
  );

  const startPickerRef = useRef<DatePickerButtonHandle>(null);
  const endPickerRef = useRef<DatePickerButtonHandle>(null);

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
    setWorkflowIdDraft("");
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

  type QueryOverrides = Partial<{
    workflowType: string[];
    status: string[];
    startTimeFrom: string;
    startTimeTo: string;
    workflowId: string;
    correlationIds: string[];
    idempotencyKey: string[];
    freeText: string;
    endTimeFrom: string;
    endTimeTo: string;
    modifiedFrom: string;
    modifiedTo: string;
    excludeSubWorkflows: boolean;
  }>;

  const buildQuery = useCallback(
    (overrides: QueryOverrides = {}) => {
      const _workflowType = overrides.workflowType ?? workflowType;
      const _workflowId = overrides.workflowId ?? workflowId;
      const _status = overrides.status ?? status;
      const _startTimeFrom = overrides.startTimeFrom ?? startTimeFrom;
      const _startTimeTo = overrides.startTimeTo ?? startTimeTo;
      const _endTimeFrom = overrides.endTimeFrom ?? endTimeFrom;
      const _endTimeTo = overrides.endTimeTo ?? endTimeTo;
      const _modifiedFrom = overrides.modifiedFrom ?? modifiedFrom;
      const _modifiedTo = overrides.modifiedTo ?? modifiedTo;
      const _correlationIds = overrides.correlationIds ?? correlationIds;
      const _idempotencyKey = overrides.idempotencyKey ?? idempotencyKey;
      const _excludeSubWorkflows =
        overrides.excludeSubWorkflows ?? excludeSubWorkflows;
      const _freeText = overrides.freeText ?? freeText;

      const clauses = [];
      if (!_isEmpty(_workflowType)) {
        clauses.push(`workflowType IN (${_workflowType.join(",")})`);
      }
      if (!_isEmpty(_workflowId)) {
        clauses.push(`workflowId='${_workflowId}'`);
      }
      if (!_isEmpty(_status)) {
        clauses.push(`status IN (${_status.join(",")})`);
      }
      if (!_isEmpty(_startTimeFrom)) {
        clauses.push(`startTime>${dateToEpoch(_startTimeFrom)}`);
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
      if (!_isEmpty(_modifiedFrom)) {
        clauses.push(`modifiedTime>${_modifiedFrom}`);
      }
      if (!_isEmpty(_modifiedTo)) {
        clauses.push(`modifiedTime<${_modifiedTo}`);
      }
      if (!_isEmpty(_correlationIds)) {
        clauses.push(`correlationId IN (${_correlationIds.join(",")})`);
      }
      if (!_isEmpty(_idempotencyKey)) {
        clauses.push(`idempotencyKey IN (${_idempotencyKey.join(",")})`);
      }
      if (_excludeSubWorkflows) {
        clauses.push(`parentWorkflowId=""`);
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
    ],
  );

  const [queryFT, setQueryFT] = useState(buildQuery);
  // Derive the live query directly from URL state so react-query refetches
  // automatically when any committed filter changes (date, status, etc.).
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
    return currentQuery.query !== "" || currentQuery.freeText !== "*";
  }, [currentQuery]);

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

  // Immediately applies a search without waiting for React state to propagate.
  // Pass overrides for values that have just changed but aren't reflected in state yet.
  const triggerSearchWith = (overrides: QueryOverrides = {}) => {
    setPage(1);
    const newQueryFT = buildQuery(overrides);
    setQueryFT(newQueryFT);
    if (_isEqual(queryFT, newQueryFT)) {
      refetch();
    }
    setRecentTaskSearch();
  };

  const advancedFilterCount = [
    !_isEmpty(workflowId),
    !_isEmpty(correlationIds),
    !_isEmpty(idempotencyKey),
    !_isEmpty(freeText),
    excludeSubWorkflows,
  ].filter(Boolean).length;

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

  const handleError = (error: any) => {
    setErrorMessage(error);
  };
  const handleClearError = () => {
    setErrorMessage(null);
  };

  return (
    <>
      <Paper variant="outlined" sx={{ marginBottom: 2 }}>
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

        <Box sx={SwitchComponent ? { pt: 0, px: 4, pb: 2 } : { p: 4 }}>
          <WorkflowSearchBar
            workflowNames={workflowNames}
            workflowType={workflowType}
            onWorkflowTypeChange={(val) => {
              setWorkflowType(val);
              triggerSearchWith({ workflowType: val });
            }}
            tooltipShown={!!tooltipFlags.executionSearch}
            onTooltipClose={handleToolTipOnClose}
          />

          <QuickFiltersCard
            startPickerRef={startPickerRef}
            endPickerRef={endPickerRef}
            status={status}
            onStatusChange={(val) => {
              setStatus(val);
              triggerSearchWith({ status: val });
            }}
            startTimeFrom={startTimeFrom}
            onStartFromChange={onStartFromChange}
            startTimeTo={startTimeTo}
            onStartToChange={onStartToChange}
            fromDisplayTime={fromDisplayTime}
            setFromDisplayTime={setFromDisplayTime}
            endTimeFrom={endTimeFrom}
            onEndFromChange={onEndFromChange}
            endTimeTo={endTimeTo}
            onEndToChange={onEndToChange}
            toDisplayTime={toDisplayTime}
            setToDisplayTime={setToDisplayTime}
            recentSearches={recentSearches}
            advancedFilterCount={advancedFilterCount}
            onOpenFilters={(anchor) => {
              setWorkflowIdDraft(workflowId);
              setFreeTextDraft(freeText);
              setIdempotencyKeyDraft(idempotencyKey);
              setExcludeSubWorkflowsDraft(excludeSubWorkflows);
              setFiltersAnchor(anchor);
            }}
            onReset={handleReset}
            onSearch={() =>
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
            onShowCode={() => setShowCodeDialog("active")}
          />

          <MoreFiltersPopover
            anchor={filtersAnchor}
            onClose={() => setFiltersAnchor(null)}
            workflowIdDraft={workflowIdDraft}
            setWorkflowIdDraft={setWorkflowIdDraft}
            freeTextDraft={freeTextDraft}
            setFreeTextDraft={setFreeTextDraft}
            idempotencyKeyDraft={idempotencyKeyDraft}
            setIdempotencyKeyDraft={setIdempotencyKeyDraft}
            excludeSubWorkflowsDraft={excludeSubWorkflowsDraft}
            setExcludeSubWorkflowsDraft={setExcludeSubWorkflowsDraft}
            correlationIds={correlationIds}
            setCorrelationIds={setCorrelationIds}
            correlationIdHasError={correlationIdHasError}
            onCorrelationInputChange={setCorrelationInputVal}
            onCorrelationFocus={() => setCorrelationFieldFocus(true)}
            onCorrelationBlur={() => setCorrelationFieldFocus(false)}
            idempotencyKeyHasError={idempotencyKeyHasError}
            onIdempotencyKeyInputChange={setIdempotencyKeyInputVal}
            onIdempotencyKeyFocus={() => setIdempotencyKeyFieldFocus(true)}
            onIdempotencyKeyBlur={() => setIdempotencyKeyFieldFocus(false)}
            onApply={({
              workflowId: wfId,
              freeText: ft,
              idempotencyKey: ik,
              excludeSubWorkflows: esw,
            }) => {
              setWorkflowId(wfId);
              setFreeText(ft);
              setIdempotencyKey(ik);
              setExcludeSubWorkflows(esw);
              triggerSearchWith({
                workflowId: wfId,
                freeText: ft,
                idempotencyKey: ik,
                excludeSubWorkflows: esw,
              });
              setFiltersAnchor(null);
            }}
          />
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
