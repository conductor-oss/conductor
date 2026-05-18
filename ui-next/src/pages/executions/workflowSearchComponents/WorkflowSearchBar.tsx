import { Box } from "@mui/material";
import SearchIcon from "components/icons/SearchIcon";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import { ConductorAutoComplete } from "components/ui/inputs";

export interface WorkflowSearchBarProps {
  workflowNames: string[];
  workflowType: string[];
  onWorkflowTypeChange: (val: string[]) => void;
  tooltipShown: boolean;
  onTooltipClose: () => void;
  onSearch: () => void;
  onShowCode: () => void;
}

export function WorkflowSearchBar({
  workflowNames,
  workflowType,
  onWorkflowTypeChange,
  tooltipShown,
  onTooltipClose,
  onSearch,
  onShowCode,
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
      {/* Autocomplete + Search button */}
      <Box
        sx={{
          display: "flex",
          gap: 1,
          alignItems: "stretch",
          flexGrow: 1,
          flexBasis: 280,
          minWidth: 0,
        }}
      >
        <ConductorAutoComplete
          id="workflow-search-name-dropdown"
          fullWidth
          size="small"
          sx={{
            minWidth: 0,
            ".MuiTextField-root .MuiOutlinedInput-root": {
              pt: "8px",
              pb: "8px",
              height: 36,
            },
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

        <SplitButton
          id="search-workflow-btn"
          startIcon={<SearchIcon />}
          options={[{ label: "Show as code", onClick: onShowCode }]}
          primaryOnClick={onSearch}
        >
          Search
        </SplitButton>
      </Box>
    </Box>
  );
}
