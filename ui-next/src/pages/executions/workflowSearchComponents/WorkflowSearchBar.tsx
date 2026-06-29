import { Box } from "@mui/material";
import { ConductorAutoComplete } from "components/ui/inputs";

export interface WorkflowSearchBarProps {
  workflowNames: string[];
  workflowType: string[];
  onWorkflowTypeChange: (val: string[]) => void;
  tooltipShown: boolean;
  onTooltipClose: () => void;
}

export function WorkflowSearchBar({
  workflowNames,
  workflowType,
  onWorkflowTypeChange,
  tooltipShown,
  onTooltipClose,
}: WorkflowSearchBarProps) {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        gap: 1,
        alignItems: "center",
        my: 3,
      }}
    >
      <ConductorAutoComplete
        id="workflow-search-name-dropdown"
        fullWidth
        size="small"
        sx={{
          minWidth: 0,
        }}
        label="Search workflow name"
        options={workflowNames.sort((a, b) =>
          a.toLowerCase().localeCompare(b.toLowerCase()),
        )}
        multiple
        freeSolo
        onChange={(__, val: string[]) => onWorkflowTypeChange(val)}
        value={workflowType}
        autoFocus
        conductorInputProps={{
          tooltip: {
            title: "Partial Name Search",
            content:
              "Search workflows by partial names with a wildcard * in your keyword. Then hit ENTER, and now you can click SEARCH. i.e. Workfl* or *orkfl*w",
            placement: "top",
            showInitial: !tooltipShown,
            initialTimeout: 2000,
            onClose: onTooltipClose,
          },
          autoFocus: true,
        }}
      />
    </Box>
  );
}
