import {
  Box,
  CircularProgress,
  FormControlLabel,
  Grid,
  Radio,
  RadioGroup,
} from "@mui/material";
import MuiTypography from "components/MuiTypography";
import { Button } from "components";
import { useMemo } from "react";
import RefreshIcon from "components/v1/icons/RefreshIcon";
const REFRESH_SECONDS_OPTIONS = [5, 10, 30, 60];

const getRefreshMessage = (
  isFetching: boolean,
  refreshInterval: number,
  elapsed: number,
) => {
  if (isFetching) {
    return "Refreshing...";
  }
  return `Refresh in ${refreshInterval - elapsed}`;
};

export const RefreshEvent = ({
  refreshInterval,
  isFetching,
  elapsed,
  handleRefresh,
  changeRefreshRate,
}: {
  refreshInterval: number;
  isFetching: boolean;
  elapsed: number;
  handleRefresh: () => void;
  changeRefreshRate: (val: number) => void;
}) => {
  const startIcon = useMemo(() => {
    return isFetching ? (
      <CircularProgress size={16} sx={{ color: "white" }} />
    ) : (
      <RefreshIcon />
    );
  }, [isFetching]);

  return (
    <Grid
      id="refresh-event-container"
      sx={{
        width: "100%",
        display: "flex",
        justifyContent: "flex-end",
        alignSelf: "center",
        gap: 4,
      }}
      size={{
        xs: 12,
        sm: 12,
        md: 12,
      }}
    >
      <Box sx={{ display: "flex", gap: 2 }}>
        <MuiTypography margin="auto 0px" variant="caption">
          Refresh seconds
        </MuiTypography>
        <RadioGroup row name="refresh-radio-group-options">
          {REFRESH_SECONDS_OPTIONS.map((op) => (
            <FormControlLabel
              value={op}
              control={
                <Radio
                  onChange={() => changeRefreshRate(op)}
                  checked={op === refreshInterval}
                />
              }
              label={op}
              key={op}
            />
          ))}
        </RadioGroup>
      </Box>
      <Box sx={{ minWidth: "130px" }}>
        <Button
          size="small"
          startIcon={startIcon}
          key="refresh"
          sx={{ whiteSpace: "nowrap", minWidth: "auto" }}
          onClick={handleRefresh}
        >
          {getRefreshMessage(isFetching, refreshInterval, elapsed)}
        </Button>
      </Box>
    </Grid>
  );
};
