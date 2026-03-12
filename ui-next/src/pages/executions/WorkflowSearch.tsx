import { Box, FormControlLabel, Switch } from "@mui/material";
import MuiTypography from "components/MuiTypography";
import PlayIcon from "components/v1/icons/PlayIcon";
import _isEqual from "lodash/isEqual";
import { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import SectionHeaderActions from "shared/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { RUN_WORKFLOW_URL } from "utils/constants/route";
import { dateToEpoch } from "utils/date";
import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { tryToJson } from "utils/utils";
import SplitWorkflowDefinitionButton from "./SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton";
import AdvancedSearch from "./workflowSearchComponents/AdvancedSearch";
import BasicSearch from "./workflowSearchComponents/BasicSearch";

const SwitchComponent = ({
  asQuery,
  setAsQuery,
}: {
  asQuery: boolean;
  setAsQuery: (value: boolean) => void;
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
      control={<Switch color="primary" onChange={() => setAsQuery(!asQuery)} />}
      label="SQL format"
    />
  </Box>
);

export default function WorkflowPanel() {
  const [asQuery, setAsQuery] = useQueryState("asQuery", false);
  const [freeText, setFreeText] = useQueryState("freeText", "");
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

  return (
    <>
      <Helmet>
        <title>Workflow Executions</title>
      </Helmet>
      <SectionHeader
        _deprecate_marginTop={0}
        title="Workflow Executions"
        actions={
          <SectionHeaderActions
            buttons={[
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
        }
      />
      <SectionContainer>
        {asQuery ? (
          <AdvancedSearch
            doSearch={doSearch}
            SwitchComponent={
              <SwitchComponent asQuery={asQuery} setAsQuery={setAsQuery} />
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
            doSearch={doSearch}
            SwitchComponent={
              <SwitchComponent asQuery={asQuery} setAsQuery={setAsQuery} />
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
    </>
  );
}
