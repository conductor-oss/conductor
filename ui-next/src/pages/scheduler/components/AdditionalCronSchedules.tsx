import { Box, Grid, Paper, Tooltip } from "@mui/material";
import { PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { Button, IconButton, Text } from "components";
import MuiTypography from "components/ui/MuiTypography";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { colors } from "theme/tokens/variables";
import { CRON_SAMPLES } from "../constants";
import { findDuplicateCrons } from "../utils/duplicateCrons";
import { CronSchedule } from "types/Schedulers";
import { useCronExpression } from "../hooks/useCronExpression";
import { TimezonePicker } from "../TimezonePicker";
import { CronTemplateSelector } from "./CronTemplateSelector";

/**
 * Cron expressions beyond the first.
 *
 * A schedule can fire on several expressions, each read in its own timezone. The first stays in
 * the section above and is sent as `cronExpression`; these are sent alongside it as
 * `cronSchedules`, so a schedule with a single cron is unchanged on the wire.
 */

type RowProps = {
  index: number;
  schedule: CronSchedule;
  /** Position of the earlier expression this one repeats, if any. */
  duplicateOf?: number;
  onChange: (index: number, schedule: CronSchedule) => void;
  onRemove: (index: number) => void;
};

function CronScheduleRow({
  index,
  schedule,
  duplicateOf,
  onChange,
  onRemove,
}: RowProps) {
  // One hook per row — it holds the expression it validates, so it cannot be called in a loop.
  // It syncs off its arguments, so the row's value stays owned by the parent. That also lets
  // the rows keep plain index keys: a removed row leaves the one below it re-seeded, not stale.
  const { humanizedExpression, cronError } = useCronExpression(
    schedule.cronExpression,
    schedule.zoneId,
  );

  const edit = (next: CronSchedule) => onChange(index, next);

  // Derived rather than remembered: picking a template and then editing the expression by hand
  // should leave the dropdown showing nothing, which it does once the two no longer match.
  const selectedTemplate = CRON_SAMPLES.includes(schedule.cronExpression)
    ? schedule.cronExpression
    : "";

  return (
    <Grid
      container
      spacing={4}
      alignItems="flex-start"
      sx={
        // Each row is three controls tall now, so the rows after the first are ruled off to
        // keep it clear which expression a timezone belongs to.
        index === 0
          ? { paddingTop: 0 }
          : {
              marginTop: 4,
              paddingTop: 4,
              borderTop: `1px solid ${colors.gray13}`,
            }
      }
    >
      <Grid size={12}>
        <CronTemplateSelector
          id={`additional-cron-template-${index}`}
          label={`Choose a template for expression ${index + 2}`}
          selectedTemplate={selectedTemplate}
          onSelectTemplate={(template) =>
            edit({ ...schedule, cronExpression: template })
          }
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <ConductorInput
          fullWidth
          id={`additional-cron-expression-${index}`}
          label={`Cron expression ${index + 2}`}
          value={schedule.cronExpression}
          onTextInputChange={(value: string) =>
            edit({ ...schedule, cronExpression: value })
          }
          error={cronError !== undefined || duplicateOf !== undefined}
          helperText={
            cronError ??
            (duplicateOf !== undefined
              ? `Same as cron expression ${duplicateOf} — the workflow would run twice`
              : humanizedExpression)
          }
        />
      </Grid>
      <Grid size={{ xs: 11, md: 5 }}>
        <TimezonePicker
          id={`additional-cron-timezone-${index}`}
          timezone={schedule.zoneId}
          error={false}
          helperText=""
          onChange={(value: string) => edit({ ...schedule, zoneId: value })}
        />
      </Grid>
      <Grid size={{ xs: 1 }}>
        <Tooltip title="Remove this expression">
          <IconButton
            id={`remove-cron-expression-${index}`}
            onClick={() => onRemove(index)}
            sx={{ marginTop: 2 }}
          >
            <TrashIcon size={18} />
          </IconButton>
        </Tooltip>
      </Grid>
    </Grid>
  );
}

type Props = {
  schedules: CronSchedule[];
  /** The expression from the section above, which these are numbered and compared against. */
  firstCron: CronSchedule;
  onChange: (schedules: CronSchedule[]) => void;
  /** The first expression's timezone, so a new row starts where the others are. */
  defaultZoneId?: string;
};

export function AdditionalCronSchedules({
  schedules,
  firstCron,
  onChange,
  defaultZoneId,
}: Props) {
  // Compared against the whole schedule, so a row repeating the expression above is caught too.
  const duplicates = findDuplicateCrons([firstCron, ...schedules]);
  const replaceAt = (index: number, schedule: CronSchedule) =>
    onChange(schedules.map((each, i) => (i === index ? schedule : each)));

  const removeAt = (index: number) =>
    onChange(schedules.filter((_, i) => i !== index));

  const append = () =>
    onChange([
      ...schedules,
      { cronExpression: "", zoneId: defaultZoneId || "UTC" },
    ]);

  return (
    <Grid size={12}>
      <Paper sx={{ marginY: 2, padding: 6 }} variant="outlined">
        <MuiTypography marginBottom="8px" opacity={0.5}>
          Additional cron expressions
        </MuiTypography>
        <Text sx={{ color: colors.gray06, marginBottom: 4 }}>
          The workflow runs on every expression listed, including the one above.
        </Text>
        {schedules.map((schedule, index) => (
          <CronScheduleRow
            key={index}
            index={index}
            schedule={schedule}
            duplicateOf={duplicates[index + 1]}
            onChange={replaceAt}
            onRemove={removeAt}
          />
        ))}
        <Box sx={{ paddingTop: schedules.length ? 4 : 0 }}>
          <Button
            id="add-cron-expression-btn"
            variant="outlined"
            startIcon={<PlusIcon size={16} />}
            onClick={append}
          >
            Add expression
          </Button>
        </Box>
      </Paper>
    </Grid>
  );
}
