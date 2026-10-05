import SearchIcon from "@mui/icons-material/Search";
import { Box, InputBase, NativeSelect } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { KeyboardEvent } from "react";
import { SearchScope, SearchScopeConfig } from "./searchScopes";
import { fieldBorderColor, helperTextColor } from "./styles";

export interface ScopedSearchBarProps {
  scope: SearchScope;
  onScopeChange: (scope: SearchScope) => void;
  value: string;
  onChange: (value: string) => void;
  /** Adds the typed value as a filter and runs the search. */
  onSubmit: () => void;
  /** Hidden on phones once filters exist, since the chips show how it works. */
  hideHintOnPhone?: boolean;
  scopes: SearchScopeConfig[];
  searchButtonId?: string;
}

const focusRing = (theme: Theme) => ({
  "&:focus-within": {
    borderColor: theme.palette.primary.main,
    boxShadow: `0 0 0 1px ${theme.palette.primary.main}`,
  },
});

/**
 * One search input whose field is picked from a dropdown. On larger screens the
 * field picker, input and Search button share one outlined bar; on phones they
 * become separate controls: the picker on its own row, then input and button.
 */
export const ScopedSearchBar = ({
  scope,
  onScopeChange,
  value,
  onChange,
  onSubmit,
  hideHintOnPhone = false,
  scopes,
  searchButtonId = "search-workflow-btn",
}: ScopedSearchBarProps) => {
  const config = scopes.find((s) => s.key === scope) ?? scopes[0];

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" && !event.metaKey && !event.ctrlKey) {
      event.preventDefault();
      onSubmit();
    }
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
      <Box
        sx={(theme) => ({
          [theme.breakpoints.up("sm")]: focusRing(theme),
          display: { xs: "grid", sm: "flex" },
          gridTemplateColumns: "minmax(0, 1fr) auto",
          gap: { xs: 1, sm: 0 },
          alignItems: "stretch",
          height: { xs: "auto", sm: 40 },
          // Width and style are set separately: a responsive "border" value
          // would reset the colour to the text colour.
          borderStyle: "solid",
          borderWidth: { xs: 0, sm: 1 },
          borderColor: fieldBorderColor(theme),
          borderRadius: { sm: 1.5 },
          bgcolor: { sm: "background.paper" },
          overflow: { sm: "hidden" },
        })}
      >
        <NativeSelect
          disableUnderline
          value={scope}
          onChange={(event) => onScopeChange(event.target.value as SearchScope)}
          inputProps={{
            id: "workflow-search-field",
            "aria-label": "Search field",
          }}
          sx={{
            gridColumn: "1 / -1",
            height: { xs: 44, sm: "auto" },
            pl: 3,
            pr: 1.25,
            borderStyle: "solid",
            borderWidth: { xs: 1, sm: "0 1px 0 0" },
            borderColor: fieldBorderColor,
            borderRadius: { xs: 1.5, sm: 0 },
            bgcolor: { xs: "background.paper", sm: "action.hover" },
            fontSize: { xs: 16, sm: 13 },
            fontWeight: 500,
            "& select:focus": { bgcolor: "transparent" },
          }}
        >
          {scopes.map(({ key, label }) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </NativeSelect>
        <Box
          sx={(theme) => ({
            [theme.breakpoints.down("sm")]: focusRing(theme),
            display: "flex",
            alignItems: "center",
            gap: 1.25,
            flex: 1,
            minWidth: 0,
            height: { xs: 44, sm: "auto" },
            px: 1.5,
            borderStyle: "solid",
            borderWidth: { xs: 1, sm: 0 },
            borderColor: fieldBorderColor(theme),
            borderRadius: { xs: 1.5, sm: 0 },
            bgcolor: "background.paper",
          })}
        >
          <SearchIcon
            aria-hidden="true"
            sx={{ fontSize: 18, color: helperTextColor }}
          />
          <InputBase
            fullWidth
            type="search"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={config.placeholder}
            inputProps={{
              id: "workflow-search-input",
              "aria-label": `Search by ${config.label.toLowerCase()}`,
              "aria-describedby": "workflow-search-hint",
            }}
            sx={{ fontSize: { xs: 16, sm: 14 } }}
          />
        </Box>
        <Box
          component="button"
          type="button"
          id={searchButtonId}
          onClick={onSubmit}
          sx={{
            px: 4,
            height: { xs: 44, sm: "auto" },
            border: 0,
            borderRadius: { xs: 1.5, sm: 0 },
            bgcolor: "primary.main",
            color: "primary.contrastText",
            font: "inherit",
            fontSize: 13,
            fontWeight: 500,
            cursor: "pointer",
            "&:hover": { bgcolor: "primary.dark" },
            // A light ring inside the button stays visible on the blue fill.
            "&:focus-visible": {
              outline: "2px solid",
              outlineColor: "primary.contrastText",
              outlineOffset: -4,
            },
          }}
        >
          Search
        </Box>
      </Box>
      <Box
        component="p"
        id="workflow-search-hint"
        sx={{
          display: { xs: hideHintOnPhone ? "none" : "block", sm: "block" },
          m: 0,
          pl: 0.25,
          fontSize: 12,
          lineHeight: "16px",
          color: helperTextColor,
        }}
      >
        {config.hint} · Enter to search
      </Box>
    </Box>
  );
};
