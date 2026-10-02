import { Box, useMediaQuery } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { humanizeStatus } from "utils/utils";
import { ReactNode, useState } from "react";
import { FilterPill } from "./FilterPill";
import { NameFilterPanel, StatusFilterPanel } from "./FilterPanels";
import {
  RefreshActionsProps,
  RefreshButton,
  RefreshIconActions,
} from "./RefreshActions";
import { ScopedSearchBar } from "./ScopedSearchBar";
import { FilterChip, SearchChips } from "./SearchChips";
import { SEARCH_SCOPES, SearchScope, SearchScopeConfig } from "./searchScopes";
import { ScopedSearchState } from "./useScopedSearch";

const summarize = (values: string[], format = (value: string) => value) => {
  if (values.length === 0) return "Any";
  if (values.length <= 2) return values.map(format).join(", ");
  return `${values.length} selected`;
};

export interface DateFilter {
  id: string;
  /** e.g. "Started" or "Ended". */
  label: string;
  /** The applied range as shown to the user, e.g. "Last 72 Hours". */
  value: string;
  /** Picker content; call `close` once a range is applied. */
  renderPanel: (close: () => void) => ReactNode;
}

export interface ExecutionSearchFiltersProps {
  /** Scoped search bar and chips. Omit when `query` replaces the search bar. */
  search?: ScopedSearchState;
  /** Replaces the search bar, e.g. the SQL editor in SQL mode. */
  query?: ReactNode;
  scopes?: Record<SearchScope, SearchScopeConfig>;
  nameFilter?: {
    /** Pill label, e.g. "Workflow name" or "Agent name". */
    label: string;
    /** Singular noun for the find box, e.g. "workflow" or "agent". */
    noun: string;
    names: string[];
    selected: string[];
    onApply: (names: string[]) => void;
  };
  statusFilter: {
    selected: string[];
    onApply: (statuses: string[]) => void;
    /** e.g. when a SQL query already filters on status. */
    disabled?: boolean;
  };
  /** Time range pills shown between the name and status pills. */
  dateFilters: DateFilter[];
  /** Optional toggles shown after the pills, e.g. excluding sub-executions. */
  toggles?: ReactNode;
  /**
   * Refresh with a Show as code menu: beside the search bar on larger screens,
   * as icon buttons next to the SQL switch on phones.
   */
  refresh?: RefreshActionsProps;
  /** The basic/SQL mode switch, at the end of the pill row. */
  modeSwitch?: ReactNode;
  /** Shows Clear all after the pills; it resets every filter. */
  hasActiveFilters: boolean;
  onClearAll: () => void;
}

/**
 * The filter form shared by the workflow and agent execution searches, in both
 * basic and SQL mode: a search bar (or SQL editor), then the filter pills and
 * the SQL switch, then the search chips and Clear all. On phones the pills sit
 * three across so the row never scrolls sideways.
 */
export const ExecutionSearchFilters = ({
  search,
  query,
  scopes = SEARCH_SCOPES,
  nameFilter,
  statusFilter,
  dateFilters,
  toggles,
  refresh,
  modeSwitch,
  hasActiveFilters,
  onClearAll,
}: ExecutionSearchFiltersProps) => {
  const [openPanel, setOpenPanel] = useState<string | null>(null);
  const isPhone = useMediaQuery((theme: Theme) => theme.breakpoints.down("sm"));
  const closePanel = () => setOpenPanel(null);

  // Values added from the search bar. The pills already show the other
  // filters, so they get no chips.
  const chips: FilterChip[] = (search?.chips ?? []).map(
    ({ scope, values }) => ({
      id: scope,
      label: scopes[scope].label,
      values,
      matchesWords: scope === "freeText",
      monospace: true,
      onRemoveValue: (value) => search?.removeValue(scope, value),
      onRemove: () => search?.removeScope(scope),
    }),
  );

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: { xs: 1.5, sm: 2 },
        p: { xs: 2, sm: 3 },
      }}
    >
      <Box sx={{ display: "flex", alignItems: "flex-start", gap: 2 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {search ? (
            <ScopedSearchBar
              scope={search.scope}
              onScopeChange={search.setScope}
              value={search.term}
              onChange={search.setTerm}
              onSubmit={search.submit}
              hideHintOnPhone={search.chips.length > 0}
              scopes={scopes}
            />
          ) : (
            query
          )}
        </Box>
        {refresh && !isPhone && <RefreshButton {...refresh} />}
      </Box>
      <Box
        sx={{
          display: { xs: "grid", sm: "flex" },
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 1.5,
        }}
      >
        {nameFilter && (
          <Box sx={{ minWidth: 0 }}>
            <FilterPill
              id="workflow-search-name-dropdown"
              label={nameFilter.label}
              value={summarize(nameFilter.selected)}
              active={nameFilter.selected.length > 0}
              open={openPanel === "name"}
              onOpen={() => setOpenPanel("name")}
              onClose={closePanel}
            >
              <NameFilterPanel
                names={nameFilter.names}
                noun={nameFilter.noun}
                selected={nameFilter.selected}
                onCancel={closePanel}
                onApply={(names) => {
                  closePanel();
                  nameFilter.onApply(names);
                }}
              />
            </FilterPill>
          </Box>
        )}
        {dateFilters.map((dateFilter) => (
          <Box key={dateFilter.id} sx={{ minWidth: 0 }}>
            <FilterPill
              id={dateFilter.id}
              label={dateFilter.label}
              value={dateFilter.value}
              open={openPanel === dateFilter.id}
              onOpen={() => setOpenPanel(dateFilter.id)}
              onClose={closePanel}
              panelWidth={520}
            >
              {dateFilter.renderPanel(closePanel)}
            </FilterPill>
          </Box>
        ))}
        <Box sx={{ minWidth: 0 }}>
          <FilterPill
            id="workflow-search-status"
            label="Status"
            value={
              statusFilter.disabled
                ? "Set in query"
                : summarize(statusFilter.selected, humanizeStatus)
            }
            active={statusFilter.selected.length > 0}
            disabled={statusFilter.disabled}
            open={openPanel === "status"}
            onOpen={() => setOpenPanel("status")}
            onClose={closePanel}
            panelWidth={260}
          >
            <StatusFilterPanel
              selected={statusFilter.selected}
              onCancel={closePanel}
              onApply={(statuses) => {
                closePanel();
                statusFilter.onApply(statuses);
              }}
            />
          </FilterPill>
        </Box>
        {toggles && (
          <Box
            sx={{
              gridColumn: "1 / -1",
              display: "flex",
              alignItems: "center",
              minHeight: { xs: 44, sm: 36 },
            }}
          >
            {toggles}
          </Box>
        )}
        {hasActiveFilters && (
          <Box
            component="button"
            type="button"
            id="reset-workflow-btn"
            onClick={() => {
              search?.clearTerm();
              onClearAll();
            }}
            sx={{
              // On phones it shares the last row with the SQL switch.
              gridColumn: "1 / 2",
              justifySelf: "start",
              minHeight: { xs: 44, sm: 36 },
              px: 1,
              border: 0,
              borderRadius: 1.5,
              bgcolor: "transparent",
              font: "inherit",
              fontSize: 13,
              fontWeight: 500,
              color: "primary.main",
              whiteSpace: "nowrap",
              cursor: "pointer",
              "&:hover": { bgcolor: "action.hover" },
            }}
          >
            Clear all
          </Box>
        )}
        {(modeSwitch || (refresh && isPhone)) && (
          <Box
            sx={{
              gridColumn: "2 / -1",
              justifySelf: "end",
              whiteSpace: "nowrap",
              display: "flex",
              alignItems: "center",
              gap: 0.5,
              minHeight: { xs: 44, sm: 36 },
              ml: { sm: "auto" },
            }}
          >
            {modeSwitch}
            {refresh && isPhone && <RefreshIconActions {...refresh} />}
          </Box>
        )}
      </Box>
      <SearchChips chips={chips} />
    </Box>
  );
};
