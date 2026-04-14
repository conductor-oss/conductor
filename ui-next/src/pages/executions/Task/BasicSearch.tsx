import { Box, Grid } from "@mui/material";
import { Button } from "components";
import StatusBadge from "components/StatusBadge";
import { renderStatusTagChip } from "components/StatusTagChip";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import ResetIcon from "components/icons/ResetIcon";
import SearchIcon from "components/icons/SearchIcon";
import { Dispatch } from "react";
import { QueryDispatch, SetStateAction } from "react-router-use-location-state";
import { TaskType } from "types/common";
import { TaskStatus } from "types/TaskStatus";
import { DateControlComponent } from "../DateControlComponent";

const taskTypes = Object.values(TaskType).filter(
  (type) => ![TaskType.START, TaskType.SWITCH_JOIN].includes(type),
);
const taskStatuses = Object.values(TaskStatus)
  .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
  .filter((status) => status !== TaskStatus.PENDING);
interface BasicSearchComponentProps {
  taskDefName: string;
  taskExecutionId: string;
  taskRefName: string;
  workflowName: string;
  freeText: string;
  startTime: string;
  startTimeEnd: string;
  fromDisplayTime: string;
  endTimeStart: string;
  endTime: string;
  status: string[];
  toDisplayTime: string;
  openDateSelect: boolean;
  openStartDatePicker: boolean;
  setStartOpenDatePicker: Dispatch<SetStateAction<boolean>>;
  setOpenDateSelect: Dispatch<SetStateAction<boolean>>;
  setToDisplayTime: Dispatch<SetStateAction<string>>;
  taskType: string[];
  openEndDatePicker: boolean;
  setTaskDefName: QueryDispatch<SetStateAction<string>>;
  setTaskExecutionId: QueryDispatch<SetStateAction<string>>;
  setTaskRefName: QueryDispatch<SetStateAction<string>>;
  setWorkflowName: QueryDispatch<SetStateAction<string>>;
  setFreeText: QueryDispatch<SetStateAction<string>>;
  setStatus: QueryDispatch<SetStateAction<string[]>>;
  setTaskType: QueryDispatch<SetStateAction<string[]>>;
  setShowCodeDialog: QueryDispatch<SetStateAction<string>>;
  setFromDisplayTime: Dispatch<SetStateAction<string>>;
  setEndOpenDatePicker: Dispatch<SetStateAction<boolean>>;
  handleReset: () => void;
  doSearch: () => void;
  onStartFromChange: (val: string) => void;
  onStartToChange: (val: string) => void;
  onEndFromChange: (val: string) => void;
  onEndToChange: (val: string) => void;
  queryText: string;
  recentSearches: { start: string; end: string };
}

export const BasicSearch = ({
  taskDefName,
  taskExecutionId,
  taskRefName,
  workflowName,
  freeText,
  startTime,
  toDisplayTime,
  setToDisplayTime,
  setOpenDateSelect,
  setStartOpenDatePicker,
  startTimeEnd,
  openDateSelect,
  endTime,
  endTimeStart,
  status,
  queryText,
  openEndDatePicker,
  taskType,
  fromDisplayTime,
  openStartDatePicker,
  setTaskDefName,
  setFromDisplayTime,
  setTaskExecutionId,
  setEndOpenDatePicker,
  setTaskRefName,
  setWorkflowName,
  setFreeText,
  setStatus,
  setTaskType,
  setShowCodeDialog,
  handleReset,
  doSearch,
  onStartFromChange,
  onEndFromChange,
  onEndToChange,
  onStartToChange,
  recentSearches,
}: BasicSearchComponentProps) => {
  return (
    <Grid container sx={{ width: "100%" }} spacing={3} px={6} pb={6} pt={2}>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorInput
          fullWidth
          label="Task definition name"
          onTextInputChange={setTaskDefName}
          value={taskDefName}
          autoFocus
        />
      </Grid>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorAutoComplete
          id="task-type-dropdown"
          fullWidth
          label="Task type"
          options={taskTypes}
          multiple
          onChange={(__, val: string[]) => setTaskType(val)}
          value={taskType}
        />
      </Grid>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorInput
          fullWidth
          label="Task execution id"
          onTextInputChange={setTaskExecutionId}
          value={taskExecutionId}
        />
      </Grid>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorInput
          fullWidth
          label="Task reference name"
          onTextInputChange={setTaskRefName}
          value={taskRefName}
        />
      </Grid>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorInput
          fullWidth
          label="Workflow name"
          onTextInputChange={setWorkflowName}
          value={workflowName}
        />
      </Grid>
      <Grid
        size={{
          xs: 6,
          md: 4,
          lg: 2,
        }}
      >
        <ConductorAutoComplete
          id="task-status-dropdown"
          label="Status"
          fullWidth
          options={taskStatuses}
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
        size={{
          xs: 12,
          sm: 12,
          md: 6,
          lg: 6,
        }}
      >
        <DateControlComponent
          startTime={startTime}
          onStartFromChange={onStartFromChange}
          startTimeEnd={startTimeEnd}
          onStartToChange={onStartToChange}
          endTimeStart={endTimeStart}
          onEndFromChange={onEndFromChange}
          endTime={endTime}
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
            queryText.includes("startTime") || queryText.includes("endTime")
          }
          recentSearches={recentSearches}
          startDialogTitle="Task Start Time"
          startDialogHelpText="Select a date range within which the Task execution has started."
          endDialogTitle="Task End Time"
          endDialogHelpText="Select a date range within which the Task execution has ended."
          startTimeLabel="Task Start Time"
          endTimeLabel="Task End Time"
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 6,
          md: 3,
          lg: 3.5,
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
        justifyContent="end"
        size={{
          xs: 12,
          sm: 6,
          md: 3,
          lg: 2.5,
        }}
      >
        <Grid alignSelf="center" size={5}>
          <Button
            id="reset-task-btn"
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
            id="search-task-btn"
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
    </Grid>
  );
};
