import {
  Box,
  Button,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import { ReactNode } from "react";

import AddIcon from "./icons/AddIcon";

export const ConductorEmptyGroupField = ({
  addButtonLabel,
  handleAddItem,
  id,
  loading = false,
  compact = false,
  emptyListMessage,
}: {
  addButtonLabel?: ReactNode;
  handleAddItem: () => void;
  id?: string;
  loading?: boolean;
  compact?: boolean;
  emptyListMessage?: ReactNode;
}) => {
  if (compact) {
    return (
      <Stack gap={2}>
        {emptyListMessage && (
          <Typography
            sx={{
              color: "#363636",
              fontSize: 12,
              fontWeight: 400,
              lineHeight: "16px",
            }}
          >
            {emptyListMessage}
          </Typography>
        )}

        <Button
          id={id}
          variant="text"
          size="small"
          onClick={handleAddItem}
          startIcon={loading ? <CircularProgress size={12} /> : <AddIcon />}
          disabled={loading}
        >
          {addButtonLabel}
        </Button>
      </Stack>
    );
  }
  return (
    <Box
      sx={{
        width: "100%",
        position: "relative",
        display: "flex",
        alignItems: "center",
        justifyContent: "start",
      }}
    >
      <Button
        id={id}
        size="small"
        onClick={handleAddItem}
        startIcon={loading ? <CircularProgress size={12} /> : <AddIcon />}
        disabled={loading}
      >
        {addButtonLabel}
      </Button>
    </Box>
  );
};
