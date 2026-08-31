import { Box, Divider, FormControlLabel, Switch } from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import PlayIcon from "components/icons/PlayIcon";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { ReactNode, useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { RUN_WORKFLOW_URL } from "utils/constants/route";
import { pluralizeResults } from "utils/helpers";
import { dateToEpoch } from "utils/date";
import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { tryToJson } from "utils/utils";
import { featureFlags, FEATURES } from "utils/flags";
import SplitWorkflowDefinitionButton from "./SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton";
import ImportBpmnButton from "./SplitWorkflowDefinitionButton/ImportBpmnButton";
import AdvancedSearch from "./workflowSearchComponents/AdvancedSearch";
import BasicSearch from "./workflowSearchComponents/BasicSearch";
import {
  ParsedBasicFilters,
  basicFieldsAfterQueryFormat,
  parseQueryToBasicFilters,
} from "./workflowSearchComponents/basicFilterQuery";

const SwitchComponent = ({
  asQuery,
  onToggle,
}: {
  asQuery: boolean;
  onToggle: () => void;
}) => (
  <Box
    sx={{
      display: "flex",
      justifyContent: "flex-end",
      padding: "10px 24px 0 24px",
    }}
  >
    <FormControlLabel
      sx={{
        marginRight: 0,
        "& .MuiTypography-root": {
          fontSize: "12px",
          color: colors.sidebarGreyDark,
        },
      }}
      checked={asQuery}
      control={<Switch color="primary" onChange={onToggle} />}
      label="SQL format"
    />
  </Box>
);

export interface WorkflowSearchProps {
  /** Classifier filter passed to /workflow/search ("workflow" | "agent"). */
  classifier?: string;
  /** When set, scopes results to a single agent and shows it in the title. */
  agentName?: string;
  /** Page and document title. */
  title?: string;
  /** Header actions; pass `null` to render none. Defaults to workflow actions. */
  headerActions?: ReactNode;
  /**
   * When set, the basic search renders a toggle with this label that excludes
   * sub-executions (those with a parentWorkflowId) — e.g. "Exclude sub-agents".
   */
  excludeSubLabel?: string;
}

export default function WorkflowPanel({
  classifier = "workflow",
  agentName,
  title = "Workflow Executions",
  headerActions,
  excludeSubLabel,
}: WorkflowSearchProps = {}) {
  const [asQuery, setAsQuery] = useQueryState("asQuery", false);
  const [freeText, setFreeText] = useQueryState("freeText", "");
  // Read with an empty default so an untouched SQL box (where advanced search
  // shows the seeded text without writing it to the URL) reads as empty here.
  const [queryText, setQueryText] = useQueryState("query", "");
  // Only the setters are needed here: switching out of SQL format writes these
  // fields from the parsed query. Basic search owns reading them.
  const [, setWorkflowType] = useQueryState<string[]>("workflowType", []);
  const [, setWorkflowId] = useQueryState("workflowId", "");
  const [, setCorrelationIds] = useQueryState<string[]>("correlationIds", []);
  const [, setIdempotencyKey] = useQueryState<string[]>("idempotencyKey", []);
  const [, setModifiedFrom] = useQueryState("modifiedFrom", "");
  const [, setModifiedTo] = useQueryState("modifiedTo", "");
  const [, setExcludeSubExecutions] = useQueryState(
    "excludeSubExecutions",
    false,
  );
  const [discardQueryOpen, setDiscardQueryOpen] = useState(false);
  const [status, setStatus] = useQueryState<string[]>("status", []);
  const [openDateSelect, setOpenDateSelect] = useState(false);
  const [openStartDatePicker, setStartOpenDatePicker] = useState(false);
  const [openEndDatePicker, setEndOpenDatePicker] = useState(false);
  const [startTimeFrom, setStartTimeFrom] = useQueryState(
    "startFrom",
    commonlyUsedDateTime("last72Hours").rangeStart,
  );
  const [startTimeTo, setStartTimeTo] = useQueryState("startTo", "");
  const [endTimeFrom, setEndTimeFrom] = useQueryState("endTimeFrom", "");
  const [endTimeTo, setEndTimeTo] = useQueryState("endTimeTo", "");
  const [fromDisplayTime, setFromDisplayTime] = useState(
    startTimeFrom
      ? getSearchDateTime(startTimeFrom, startTimeTo)
      : "Last 72 Hours",
  );
  const [toDisplayTime, setToDisplayTime] = useState(
    endTimeTo ? getSearchDateTime(endTimeFrom, endTimeTo) : "Select time range",
  );

  const leaveQueryFormat = () => {
    // Drop the param too, so a discarded query cannot reappear the next time
    // SQL format is switched on.
    setQueryText("");
    setAsQuery(false);
  };

  const applyParsedFilters = (parsed: ParsedBasicFilters) => {
    const next = basicFieldsAfterQueryFormat(parsed, {
      status,
      startTimeFrom,
      startTimeTo,
      endTimeFrom,
      endTimeTo,
    });
    setWorkflowType(next.workflowType);
    setWorkflowId(next.workflowId);
    setCorrelationIds(next.correlationIds);
    setIdempotencyKey(next.idempotencyKey);
    setModifiedFrom(next.modifiedFrom);
    setModifiedTo(next.modifiedTo);
    setExcludeSubExecutions(next.excludeSubExecutions);
    setStatus(next.status);
    setStartTimeFrom(next.startTimeFrom);
    setStartTimeTo(next.startTimeTo);
    setEndTimeFrom(next.endTimeFrom);
    setEndTimeTo(next.endTimeTo);
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
      setAsQuery(true);
      return;
    }
    // An empty box means nothing was authored here — the seeded text is shown
    // without being written to the URL — so leave the fields as they were.
    if (_isEmpty(queryText)) {
      leaveQueryFormat();
      return;
    }
    const parsed = parseQueryToBasicFilters(queryText);
    if (parsed) {
      applyParsedFilters(parsed);
      leaveQueryFormat();
      return;
    }
    // Nothing basic search can express; ask before dropping it.
    setDiscardQueryOpen(true);
  };

  const last72HoursTimestamp = Date.now() - 72 * 60 * 60 * 1000;

  const recentSearches =
    (tryToJson(localStorage.getItem("recentTaskSearch")) as {
      start: string;
      end: string;
    }) || {};

  useEffect(() => {
    if (!startTimeFrom) {
      setStartTimeFrom(last72HoursTimestamp.toString());
      setStartTimeTo("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onStartFromChange = (val: string) => {
    setStartTimeFrom(val ? String(dateToEpoch(val)) : "");
  };
  const onStartToChange = (val: string) => {
    setStartTimeTo(val ? String(dateToEpoch(val)) : "");
  };
  const onEndFromChange = (val: string) => {
    setEndTimeFrom(val ? String(dateToEpoch(val)) : "");
  };
  const onEndToChange = (val: string) => {
    setEndTimeTo(val ? String(dateToEpoch(val)) : "");
  };

  const doSearch = ({
    queryFT,
    buildQuery,
    setQueryFT,
    refetch,
    setPage,
    setRecentTaskSearch,
  }: DoSearchProps) => {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    if (_isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
    setRecentTaskSearch?.();
  };

  const pushHistory = usePushHistory();

  const getTableTitle = (resultObj: TaskExecutionResult | undefined) => {
    if (!resultObj?.results) return null;
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

  const isImportBpmnHidden = featureFlags.isEnabled(FEATURES.HIDE_IMPORT_BPMN);

  const defaultActions = (
    <SectionHeaderActions
      buttons={[
        ...(isImportBpmnHidden
          ? []
          : [
              { customButtonElement: <ImportBpmnButton /> },
              {
                customButtonElement: (
                  <Divider
                    orientation="vertical"
                    flexItem
                    sx={{ height: 24, alignSelf: "center" }}
                  />
                ),
              },
            ]),
        {
          label: "Run workflow",
          color: "secondary",
          onClick: () => pushHistory(RUN_WORKFLOW_URL),
          startIcon: <PlayIcon />,
        },
        {
          customButtonElement: <SplitWorkflowDefinitionButton />,
        },
      ]}
    />
  );

  const pageTitle = agentName ? `${title} — ${agentName}` : title;

  return (
    <>
      <Helmet>
        <title>{title}</title>
      </Helmet>
      <SectionHeader
        _deprecate_marginTop={0}
        title={pageTitle}
        actions={headerActions !== undefined ? headerActions : defaultActions}
      />
      <SectionContainer>
        {asQuery ? (
          <AdvancedSearch
            classifier={classifier}
            doSearch={doSearch}
            SwitchComponent={
              <SwitchComponent
                asQuery={asQuery}
                onToggle={handleToggleQueryFormat}
              />
            }
            getTableTitle={getTableTitle}
            freeText={freeText}
            setFreeText={setFreeText}
            status={status}
            setStatus={setStatus}
            startTimeFrom={startTimeFrom}
            setStartTimeFrom={setStartTimeFrom}
            onStartFromChange={onStartFromChange}
            startTimeTo={startTimeTo}
            setStartTimeTo={setStartTimeTo}
            onStartToChange={onStartToChange}
            endTimeFrom={endTimeFrom}
            setEndTimeFrom={setEndTimeFrom}
            onEndFromChange={onEndFromChange}
            endTimeTo={endTimeTo}
            setEndTimeTo={setEndTimeTo}
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
          />
        ) : (
          <BasicSearch
            classifier={classifier}
            agentName={agentName}
            excludeSubLabel={excludeSubLabel}
            doSearch={doSearch}
            SwitchComponent={
              <SwitchComponent
                asQuery={asQuery}
                onToggle={handleToggleQueryFormat}
              />
            }
            getTableTitle={getTableTitle}
            freeText={freeText}
            setFreeText={setFreeText}
            status={status}
            setStatus={setStatus}
            startTimeFrom={startTimeFrom}
            setStartTimeFrom={setStartTimeFrom}
            onStartFromChange={onStartFromChange}
            startTimeTo={startTimeTo}
            setStartTimeTo={setStartTimeTo}
            onStartToChange={onStartToChange}
            endTimeFrom={endTimeFrom}
            setEndTimeFrom={setEndTimeFrom}
            onEndFromChange={onEndFromChange}
            endTimeTo={endTimeTo}
            setEndTimeTo={setEndTimeTo}
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
          />
        )}
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
