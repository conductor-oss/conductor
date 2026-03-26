import { Box, Grid, Paper, SxProps, Theme, useMediaQuery } from "@mui/material";
import { Text } from "components";
import MuiTypography from "components/MuiTypography";
import ConductorInput from "components/v1/ConductorInput";
import {
  formatInTimeZone,
  guessUserTimeZone,
  parseDateInTimeZone,
} from "utils/date";
import CronExpressionHelp from "../CronExpressionHelp";
import { CronTemplateSelector } from "./CronTemplateSelector";
import { TimezonePicker } from "../TimezonePicker";

const utcWinWidth = "180px";
const browserTimeMinWidth = "230px";

interface CronExpressionSectionProps {
  cronExpression: string;
  setCronExpression: (value: string, timezone: string) => void;
  futureMatches: string[];
  humanizedExpression: string;
  highlightedPart: number | null;
  getHighlightedPart: (value: string, selectionStart: number) => void;
  setHighlightedPart: (part: number | null) => void;
  selectedTemplate: string;
  setSelectedTemplate: (template: string) => void;
  timezone: string;
  setZoneId: (value: string) => void;
  cronError?: string;
  minWidthCronExpression: string;
}

export function CronExpressionSection({
  cronExpression,
  setCronExpression,
  futureMatches,
  humanizedExpression,
  highlightedPart,
  getHighlightedPart,
  setHighlightedPart,
  selectedTemplate,
  setSelectedTemplate,
  timezone,
  setZoneId,
  cronError,
  minWidthCronExpression,
}: CronExpressionSectionProps) {
  const isMDWidth = useMediaQuery((theme: Theme) => theme.breakpoints.up("md"));

  const timeListStyle: SxProps<Theme> = {
    flexWrap: isMDWidth ? "nowrap" : "wrap",
    justifyContent: "start",
  };

  return (
    <Grid size={12}>
      <Paper sx={{ marginY: 2 }} variant="outlined">
        <Grid
          flexGrow={1}
          sx={{
            paddingX: 6,
            paddingTop: 6,
          }}
        >
          <Box sx={{ overflow: "hidden" }}>
            <MuiTypography marginBottom="8px" opacity={0.5}>
              Cron Expressions Help
            </MuiTypography>
            <CronExpressionHelp highlightedPart={highlightedPart} />
          </Box>
          <CronTemplateSelector
            selectedTemplate={selectedTemplate}
            onSelectTemplate={(template) => {
              setCronExpression(template, timezone);
              setSelectedTemplate(template);
            }}
          />
        </Grid>
        <Grid
          container
          sx={{
            borderRadius: "4px",
            width: "100%",
          }}
        >
          <Grid
            flexGrow={1}
            flexBasis={"500px"}
            sx={{
              padding: [2, 6],
              minWidth: minWidthCronExpression,
            }}
          >
            <Grid size={12}>
              <ConductorInput
                fullWidth
                label="Cron expression"
                value={cronExpression}
                onTextInputChange={(value) =>
                  setCronExpression(value, timezone)
                }
                onKeyDown={(e: any) => {
                  getHighlightedPart(e.target.value, e.target.selectionStart);
                }}
                onKeyUp={(e: any) => {
                  getHighlightedPart(e.target.value, e.target.selectionStart);
                }}
                onClick={(e: any) => {
                  getHighlightedPart(e.target.value, e.target.selectionStart);
                }}
                onBlur={(_e) => {
                  setHighlightedPart(null);
                }}
                error={cronError !== undefined}
                helperText={cronError}
                inputProps={{
                  sx: {
                    fontSize: "1.3rem",
                  },
                }}
              />
              <Box
                sx={{
                  paddingTop: 4,
                }}
              >
                <TimezonePicker
                  timezone={timezone}
                  error={false}
                  helperText=""
                  onChange={(value) => {
                    setZoneId(value);
                    setCronExpression(cronExpression, value);
                  }}
                />
              </Box>
            </Grid>
            <Grid size={12}>
              {futureMatches && (
                <Paper
                  sx={{ padding: 3, marginTop: 3 }}
                  variant="outlined"
                  color="info"
                >
                  <MuiTypography marginBottom="8px" opacity={0.5}>
                    Next run schedules based on the expression:
                  </MuiTypography>
                  {cronExpression && (
                    <MuiTypography marginBottom="8px" fontWeight={600}>
                      {humanizedExpression} ({timezone})
                    </MuiTypography>
                  )}
                  {futureMatches && futureMatches.length === 0 && (
                    <Text sx={{}}>No schedules possible</Text>
                  )}
                  {futureMatches?.length > 0 && (
                    <Grid
                      id="next-run-schedule-examples-wrapper"
                      container
                      columnGap={2}
                      spacing={2}
                      sx={{ ...timeListStyle }}
                    >
                      <Grid sx={{ minWidth: utcWinWidth }}>
                        <Text mb={2} fontWeight={600} sx={{}}>
                          {timezone} Time
                        </Text>
                        {futureMatches.map((time) => {
                          const parsed = parseDateInTimeZone(time, timezone);
                          const formatted = formatInTimeZone(
                            parsed,
                            "yyyy-MM-dd HH:mm:ss zzz",
                            timezone,
                          );

                          return (
                            <Text
                              key={`keyt-utc-${time}`}
                              sx={{
                                whiteSpace: "nowrap",
                              }}
                            >
                              {formatted}
                            </Text>
                          );
                        })}
                      </Grid>

                      <Grid sx={{ minWidth: browserTimeMinWidth }}>
                        <Text mb={2} fontWeight={600} sx={{}}>
                          Browser local time
                        </Text>
                        {futureMatches.map((time) => {
                          const browserTz = guessUserTimeZone();
                          const formatted = formatInTimeZone(
                            new Date(time),
                            "yyyy-MM-dd HH:mm:ss zzz",
                            browserTz,
                          );

                          return (
                            <Text key={`keyt-${time}`} sx={{}}>
                              {formatted}
                            </Text>
                          );
                        })}
                      </Grid>
                    </Grid>
                  )}
                </Paper>
              )}
            </Grid>
          </Grid>
        </Grid>
      </Paper>
    </Grid>
  );
}
