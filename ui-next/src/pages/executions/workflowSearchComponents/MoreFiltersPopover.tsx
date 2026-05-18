import {
  Box,
  FormControlLabel,
  Popover,
  Switch,
  Typography,
} from "@mui/material";
import { Button } from "components";
import SearchIcon from "components/icons/SearchIcon";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";

export interface MoreFiltersValues {
  workflowId: string;
  freeText: string;
  idempotencyKey: string[];
  excludeSubWorkflows: boolean;
  correlationIds: string[];
}

export interface MoreFiltersPopoverProps {
  anchor: HTMLElement | null;
  onClose: () => void;
  // Draft state
  workflowIdDraft: string;
  setWorkflowIdDraft: (val: string) => void;
  freeTextDraft: string;
  setFreeTextDraft: (val: string) => void;
  idempotencyKeyDraft: string[];
  setIdempotencyKeyDraft: (val: string[]) => void;
  excludeSubWorkflowsDraft: boolean;
  setExcludeSubWorkflowsDraft: (val: boolean) => void;
  correlationIds: string[];
  setCorrelationIds: (val: string[]) => void;
  // Correlation ID input validation
  correlationIdHasError: boolean;
  onCorrelationInputChange: (val: string) => void;
  onCorrelationFocus: () => void;
  onCorrelationBlur: () => void;
  // Idempotency key input validation
  idempotencyKeyHasError: boolean;
  onIdempotencyKeyInputChange: (val: string) => void;
  onIdempotencyKeyFocus: () => void;
  onIdempotencyKeyBlur: () => void;
  // Apply
  onApply: (values: MoreFiltersValues) => void;
}

export function MoreFiltersPopover({
  anchor,
  onClose,
  workflowIdDraft,
  setWorkflowIdDraft,
  freeTextDraft,
  setFreeTextDraft,
  idempotencyKeyDraft,
  setIdempotencyKeyDraft,
  excludeSubWorkflowsDraft,
  setExcludeSubWorkflowsDraft,
  correlationIds,
  setCorrelationIds,
  correlationIdHasError,
  onCorrelationInputChange,
  onCorrelationFocus,
  onCorrelationBlur,
  idempotencyKeyHasError,
  onIdempotencyKeyInputChange,
  onIdempotencyKeyFocus,
  onIdempotencyKeyBlur,
  onApply,
}: MoreFiltersPopoverProps) {
  return (
    <Popover
      open={Boolean(anchor)}
      anchorEl={anchor}
      onClose={onClose}
      anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
      transformOrigin={{ vertical: "top", horizontal: "left" }}
      slotProps={{ paper: { sx: { p: 2.5, width: 500, mt: 0.5 } } }}
    >
      <Typography variant="subtitle2" sx={{ mb: 4, fontWeight: 600 }}>
        More Filters
      </Typography>

      <Box sx={{ display: "flex", flexDirection: "column", gap: 4 }}>
        <ConductorInput
          id="workflow-search-id"
          fullWidth
          label="Workflow ID"
          value={workflowIdDraft}
          onTextInputChange={setWorkflowIdDraft}
          showClearButton
        />

        <ConductorAutoComplete
          id="workflow-search-correlation-id"
          fullWidth
          label="Correlation ID"
          options={[]}
          multiple
          freeSolo
          onTextInputChange={onCorrelationInputChange}
          onChange={(evt: any, val: string[]) => {
            if (evt.key === "Backspace" || evt.key === "Enter")
              onCorrelationInputChange("");
            setCorrelationIds(val);
          }}
          onFocus={onCorrelationFocus}
          onBlur={() => {
            onCorrelationBlur();
            onCorrelationInputChange("");
          }}
          value={correlationIds}
          error={correlationIdHasError}
          conductorInputProps={{
            tooltip: {
              title: "Get Workflows by Correlation ID",
              content:
                "Search workflows by Correlation ID. Press Enter for each value.",
            },
            error: correlationIdHasError,
          }}
        />

        <ConductorAutoComplete
          id="workflow-search-idempotency-key"
          fullWidth
          label="Idempotency key"
          options={[]}
          multiple
          freeSolo
          onTextInputChange={onIdempotencyKeyInputChange}
          onChange={(evt: any, val: string[]) => {
            if (evt.key === "Backspace" || evt.key === "Enter")
              onIdempotencyKeyInputChange("");
            setIdempotencyKeyDraft(val);
          }}
          onFocus={onIdempotencyKeyFocus}
          onBlur={() => {
            onIdempotencyKeyBlur();
            onIdempotencyKeyInputChange("");
          }}
          value={idempotencyKeyDraft}
          error={idempotencyKeyHasError}
          conductorInputProps={{
            tooltip: {
              title: "Get Workflows by Idempotency key",
              content:
                "Search workflows by Idempotency key. Press Enter for each value.",
            },
            error: idempotencyKeyHasError,
          }}
        />

        <ConductorInput
          fullWidth
          label="Free text search"
          value={freeTextDraft}
          onTextInputChange={setFreeTextDraft}
          showClearButton
        />

        <FormControlLabel
          sx={{ m: 0 }}
          control={
            <Switch
              color="primary"
              checked={excludeSubWorkflowsDraft}
              onChange={(e) => setExcludeSubWorkflowsDraft(e.target.checked)}
              size="small"
            />
          }
          label="Exclude sub-workflows"
          slotProps={{ typography: { variant: "body2" } }}
        />
      </Box>

      <Box
        sx={{
          display: "flex",
          justifyContent: "flex-end",
          gap: 1,
          mt: 2.5,
          pt: 2,
          borderTop: (theme) => `1px solid ${theme.palette.divider}`,
        }}
      >
        <Button
          variant="text"
          size="small"
          sx={{ textTransform: "none" }}
          onClick={onClose}
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          size="small"
          startIcon={<SearchIcon />}
          sx={{ textTransform: "none" }}
          onClick={() =>
            onApply({
              workflowId: workflowIdDraft,
              freeText: freeTextDraft,
              idempotencyKey: idempotencyKeyDraft,
              excludeSubWorkflows: excludeSubWorkflowsDraft,
              correlationIds,
            })
          }
        >
          Apply
        </Button>
      </Box>
    </Popover>
  );
}
