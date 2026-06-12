import {
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  Switch,
  Tooltip,
} from "@mui/material";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorDateRangePicker from "components/ui/date-time/ConductorDateRangePicker";
import { baseLabelStyle } from "theme/styles";
import { SMALL_EDITOR_DEFAULT_OPTIONS } from "utils/constants";

interface ScheduleTimingSectionProps {
  scheduleStartTime: string | number;
  scheduleEndTime: string | number;
  handleScheduleStartTime: (value: number) => void;
  handleScheduleEndTime: (value: number) => void;
  taskToDomain: string;
  setWorkflowTasksToDomainState: (value: string) => void;
  paused: boolean;
  setCronPausedState: () => void;
}

export function ScheduleTimingSection({
  scheduleStartTime,
  scheduleEndTime,
  handleScheduleStartTime,
  handleScheduleEndTime,
  taskToDomain,
  setWorkflowTasksToDomainState,
  paused,
  setCronPausedState,
}: ScheduleTimingSectionProps) {
  return (
    <>
      <Grid size={12}>
        <ConductorDateRangePicker
          labelFrom="Schedule start (local)"
          labelTo="Schedule end (local)"
          from={scheduleStartTime ? new Date(scheduleStartTime) : null}
          to={scheduleEndTime ? new Date(scheduleEndTime) : null}
          onFromChange={handleScheduleStartTime}
          onToChange={handleScheduleEndTime}
        />
      </Grid>
      <Grid size={12}>
        <ConductorCodeBlockInput
          label="Tasks to domain mapping"
          minHeight={100}
          defaultLanguage="json"
          value={taskToDomain}
          onChange={setWorkflowTasksToDomainState}
          options={SMALL_EDITOR_DEFAULT_OPTIONS}
        />
      </Grid>
      <Grid size={12}>
        <FormControl>
          <InputLabel sx={baseLabelStyle}>Start schedule paused?</InputLabel>
        </FormControl>
        <Tooltip
          title="Turn this on to pause the schedule before it starts."
          arrow
        >
          <FormControlLabel
            control={
              <Switch
                color="primary"
                checked={paused}
                name="pausedSchedule"
                onChange={() => setCronPausedState()}
              />
            }
            label="Pause schedule"
            sx={{ mt: 3, mb: 3 }}
          />
        </Tooltip>
      </Grid>
    </>
  );
}
