import {
  Add as AddIcon,
  DeleteOutline as DeleteIcon,
} from "@mui/icons-material";
import { Box, Grid, IconButton, Paper } from "@mui/material";
import { Button, Text } from "components";
import MuiTypography from "components/MuiTypography";
import ConductorInput from "components/v1/ConductorInput";
import { useMemo } from "react";
import { ICronSchedule } from "types/Schedulers";
import {
  formatInTimeZone,
  guessUserTimeZone,
  parseDateInTimeZone,
} from "utils/date";
import { useCronExpression } from "../hooks/useCronExpression";
import { DEFAULT_CRON_ZONE } from "../utils/cronSchedules";
import { CronTemplateSelector } from "./CronTemplateSelector";
import { cronTemplateSamples } from "./cronTemplateSamples";
import { TimezonePicker } from "../TimezonePicker";

type MultiCronExpressionSectionProps = {
  cronSchedules: ICronSchedule[];
  onAdd: () => void;
  onRemove: (index: number) => void;
  onCronExpressionChange: (index: number, cronExpression: string) => void;
  onZoneIdChange: (index: number, zoneId: string) => void;
  cronErrorsByIndex?: Record<number, string>;
  zoneErrorsByIndex?: Record<number, string>;
};

function MultiCronScheduleEntry({
  index,
  entry,
  onRemove,
  onCronExpressionChange,
  onZoneIdChange,
  cronError,
  zoneError,
  canRemove,
}: {
  index: number;
  entry: ICronSchedule;
  onRemove: (index: number) => void;
  onCronExpressionChange: (index: number, cronExpression: string) => void;
  onZoneIdChange: (index: number, zoneId: string) => void;
  cronError?: string;
  zoneError?: string;
  canRemove: boolean;
}) {
  const zoneId = entry.zoneId || DEFAULT_CRON_ZONE;
  const cron = useCronExpression(entry.cronExpression || "", zoneId);
  const selectedTemplate = cronTemplateSamples.includes(entry.cronExpression)
    ? entry.cronExpression
    : "";

  const localTimes = useMemo(() => {
    const browserTz = guessUserTimeZone();
    return cron.futureMatches.slice(0, 3).map((time) => {
      const parsed = parseDateInTimeZone(time, zoneId);
      return {
        inZone: formatInTimeZone(parsed, "yyyy-MM-dd HH:mm:ss zzz", zoneId),
        inBrowser: formatInTimeZone(
          parsed,
          "yyyy-MM-dd HH:mm:ss zzz",
          browserTz,
        ),
      };
    });
  }, [cron.futureMatches, zoneId]);

  return (
    <Paper variant="outlined" sx={{ p: 3, mt: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Text sx={{}} fontWeight={600}>
          Cron #{index + 1}
        </Text>
        <IconButton
          aria-label={`remove-cron-${index}`}
          onClick={() => onRemove(index)}
          size="small"
          color="error"
          disabled={!canRemove}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Box>

      <CronTemplateSelector
        selectedTemplate={selectedTemplate}
        onSelectTemplate={(value) => {
          onCronExpressionChange(index, value);
          cron.setCronExpression(value, zoneId);
        }}
      />
      <Box sx={{ mt: 3 }}>
        <ConductorInput
          fullWidth
          required
          label="Cron expression"
          value={entry.cronExpression}
          onTextInputChange={(value) => {
            onCronExpressionChange(index, value);
            cron.setCronExpression(value, zoneId);
          }}
          error={!!cronError || !!cron.cronError}
          helperText={cronError || cron.cronError || ""}
        />
      </Box>
      <Box sx={{ mt: 3 }}>
        <TimezonePicker
          timezone={zoneId}
          error={!!zoneError}
          helperText={zoneError || ""}
          onChange={(value) => {
            const nextZone = value || DEFAULT_CRON_ZONE;
            onZoneIdChange(index, nextZone);
            cron.setCronExpression(entry.cronExpression || "", nextZone);
          }}
        />
      </Box>

      <Paper sx={{ padding: 2, marginTop: 2 }} variant="outlined">
        <MuiTypography marginBottom="8px" opacity={0.5}>
          Next run schedules based on the expression:
        </MuiTypography>
        {!!entry.cronExpression && (
          <Text sx={{}} fontWeight={600}>
            {cron.humanizedExpression || entry.cronExpression}
          </Text>
        )}
        {!cron.futureMatches.length && (
          <Text sx={{ opacity: 0.8, mt: 1 }}>No schedules possible</Text>
        )}
        {localTimes.map((item, index) => (
          <Box key={`${item.inZone}-${item.inBrowser}-${index}`} sx={{ mt: 1 }}>
            <Text sx={{}}>{item.inZone}</Text>
            <Text sx={{ opacity: 0.7 }}>{item.inBrowser}</Text>
          </Box>
        ))}
      </Paper>
    </Paper>
  );
}

export function MultiCronExpressionSection({
  cronSchedules,
  onAdd,
  onRemove,
  onCronExpressionChange,
  onZoneIdChange,
  cronErrorsByIndex,
  zoneErrorsByIndex,
}: MultiCronExpressionSectionProps) {
  return (
    <Grid size={12}>
      <Paper sx={{ marginY: 2, p: 3 }} variant="outlined">
        <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
          <MuiTypography opacity={0.7}>
            Configure multiple cron expressions for this schedule.
          </MuiTypography>
          <Button
            size="small"
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={onAdd}
          >
            Add cron
          </Button>
        </Box>
        {cronSchedules.map((entry, index) => (
          <MultiCronScheduleEntry
            key={`multi-cron-entry-${entry.cronExpression}-${index}-${entry.zoneId || DEFAULT_CRON_ZONE}-${index === 0 ? "first" : "next"}`}
            index={index}
            entry={entry}
            onRemove={onRemove}
            onCronExpressionChange={onCronExpressionChange}
            onZoneIdChange={onZoneIdChange}
            cronError={cronErrorsByIndex?.[index]}
            zoneError={zoneErrorsByIndex?.[index]}
            canRemove={cronSchedules.length > 1}
          />
        ))}
      </Paper>
    </Grid>
  );
}
