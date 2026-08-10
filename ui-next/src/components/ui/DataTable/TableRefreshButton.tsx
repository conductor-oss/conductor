import { Tooltip } from "@mui/material";
import { Button } from "components";
import { colors } from "theme/tokens/variables";
import { ArrowClockwise as RefreshIcon } from "@phosphor-icons/react";

interface TableRefreshButtonProps {
  tooltipTitle: string;
  refetch: () => void;
}

export const TableRefreshButton = ({
  tooltipTitle,
  refetch,
}: TableRefreshButtonProps) => {
  return (
    <Tooltip title={tooltipTitle}>
      <Button
        variant="text"
        size="small"
        startIcon={<RefreshIcon />}
        onClick={() => refetch()}
        sx={{
          color: (theme) =>
            theme.palette.mode === "dark" ? colors.gray13 : colors.gray02,
        }}
      >
        Refresh
      </Button>
    </Tooltip>
  );
};
