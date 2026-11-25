import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import { Button } from "components";
import MuiTypography from "components/ui/MuiTypography";
import { useState } from "react";

interface ReasonForIncompletionProps {
  reason: string;
}

export const ReasonForIncompletion = ({
  reason,
}: ReasonForIncompletionProps) => {
  const [isFullMessageOpen, setIsFullMessageOpen] = useState(false);

  if (!reason) return null;

  if (reason.length >= 300) {
    return (
      <>
        <Box>
          {reason.substr(0, 60)}... [
          <MuiTypography
            component="span"
            color="#1976d2"
            fontWeight="bold"
            cursor="pointer"
            onClick={() => setIsFullMessageOpen(true)}
          >
            View full message
          </MuiTypography>
          ]
        </Box>
        <Dialog
          open={isFullMessageOpen}
          onClose={() => setIsFullMessageOpen(false)}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>Failure reason</DialogTitle>
          <DialogContent dividers>
            {/* Preserve server-provided newlines so API failures remain readable. */}
            <Box component="pre" sx={{ m: 0, whiteSpace: "pre-wrap" }}>
              {reason}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setIsFullMessageOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  }

  return <>{reason}</>;
};
