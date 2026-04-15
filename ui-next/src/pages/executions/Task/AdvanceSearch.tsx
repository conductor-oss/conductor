import { Monaco } from "@monaco-editor/react";
import { Box, Grid } from "@mui/material";
import { Button } from "components";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import ResetIcon from "components/icons/ResetIcon";
import SearchIcon from "components/icons/SearchIcon";
import { Dispatch, useEffect, useRef } from "react";
import { QueryDispatch, SetStateAction } from "react-router-use-location-state";
import { colors } from "theme/tokens/variables";
import { TaskType } from "types/common";
import { TaskStatus } from "types/TaskStatus";
import {
  TASK_SEARCH_QUERY_SUGGESTIONS,
  WORKFLOW_SEARCH_QUERY_SUGGESTIONS,
} from "utils/constants/common";
import { useLocalStorage } from "utils/localstorage";
import { DateControlComponent } from "../DateControlComponent";
import { ExampleSearchQuery } from "../SearchExampleQuery";

const taskTypes = Object.values(TaskType);
const taskStatuses = Object.values(TaskStatus).sort((a, b) =>
  a.toLowerCase().localeCompare(b.toLowerCase()),
);

interface AdvanceSearchComponentProps {
  queryText: string;
  freeText: string;
  startTime: string;
  startTimeEnd: string;
  fromDisplayTime: string;
  endTimeStart: string;
  endTime: string;
  toDisplayTime: string;
  openDateSelect: boolean;
  openStartDatePicker: boolean;
  setStartOpenDatePicker: Dispatch<SetStateAction<boolean>>;
  setOpenDateSelect: Dispatch<SetStateAction<boolean>>;
  setToDisplayTime: Dispatch<SetStateAction<string>>;
  openEndDatePicker: boolean;
  setFreeText: QueryDispatch<SetStateAction<string>>;
  setQueryText: QueryDispatch<SetStateAction<string>>;
  setShowCodeDialog: QueryDispatch<SetStateAction<string>>;
  handleReset: () => void;
  doSearch: () => void;
  onStartFromChange: (val: string) => void;
  onStartToChange: (val: string) => void;
  onEndFromChange: (val: string) => void;
  onEndToChange: (val: string) => void;
  setFromDisplayTime: Dispatch<SetStateAction<string>>;
  setEndOpenDatePicker: Dispatch<SetStateAction<boolean>>;
  recentSearches: { start: string; end: string };
}

export const AdvanceSearch = ({
  queryText,
  freeText,
  startTime,
  endTime,
  setQueryText,
  onStartFromChange,
  onStartToChange,
  setFreeText,
  handleReset,
  doSearch,
  setShowCodeDialog,
  toDisplayTime,
  setToDisplayTime,
  setOpenDateSelect,
  setStartOpenDatePicker,
  startTimeEnd,
  openDateSelect,
  endTimeStart,
  openEndDatePicker,
  fromDisplayTime,
  openStartDatePicker,
  setFromDisplayTime,
  setEndOpenDatePicker,
  onEndFromChange,
  onEndToChange,
  recentSearches,
}: AdvanceSearchComponentProps) => {
  const disposeRef = useRef<null | (() => void)>(null);

  useEffect(() => {
    return () => {
      if (disposeRef.current) {
        disposeRef.current();
      }
    };
  }, []);

  // for tooltip flag in localstorage
  const [tooltipFlags, setTooltipFlags] = useLocalStorage("tooltipFlags", {});
  const handleToolTipOnClose = () => {
    if (tooltipFlags && !tooltipFlags.executionSearch) {
      setTooltipFlags({ ...tooltipFlags, executionSearch: true });
    }
  };
  return (
    <Grid container sx={{ width: "100%" }} spacing={3} px={6} pb={6} pt={2}>
      <Grid size={12}>
        <ConductorCodeBlockInput
          label="Search"
          language="sql"
          minHeight={30}
          value={queryText}
          onChange={setQueryText}
          autoFocus
          tooltip={{
            placement: "top",
            title: "Search",
            content: (
              <Box>
                Search tasks by query parameters. Then hit ENTER, and now you
                can click SEARCH.
                <Box
                  sx={{
                    border: "1px solid lightgrey",
                    padding: 2,
                    color: colors.black,
                    borderRadius: "4px",
                    marginTop: 1,
                    fontWeight: 400,
                  }}
                >
                  <MuiTypography fontWeight={400} color={colors.greyText}>
                    Sample:
                  </MuiTypography>
                  <ExampleSearchQuery />
                </Box>
              </Box>
            ),
            showInitial: !tooltipFlags.executionSearch,
            initialTimeout: 2000,
            onClose: handleToolTipOnClose,
          }}
          beforeMount={(monaco: Monaco) => {
            if (disposeRef.current) {
              disposeRef.current();
              disposeRef.current = null;
            }
            const disposable = monaco.languages.registerCompletionItemProvider(
              "sql",
              {
                provideCompletionItems: () => {
                  const propertyKeys = [
                    ...WORKFLOW_SEARCH_QUERY_SUGGESTIONS,
                    ...TASK_SEARCH_QUERY_SUGGESTIONS,
                    ...taskTypes,
                    ...taskStatuses,
                  ];

                  // Provide suggestions for properties that start with the current text
                  const propertySuggestions = propertyKeys.map((property) => ({
                    label: property,
                    kind: monaco.languages.CompletionItemKind.Value,
                    insertText: property,
                  }));
                  // Merge custom suggestions with property suggestions
                  const suggestions = [...propertySuggestions];
                  return { suggestions };
                },
              },
            );
            // IMPORTANT: keep `dispose()` bound to its disposable context.
            // Destructuring `dispose` can lose `this` and throw "Unbound disposable context".
            disposeRef.current = () => disposable.dispose();
          }}
          options={{
            lineNumbers: "off",
          }}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 5.5,
        }}
      >
        <DateControlComponent
          startTime={startTime}
          onStartFromChange={onStartFromChange}
          startTimeEnd={startTimeEnd}
          onStartToChange={onStartToChange}
          endTimeStart={endTimeStart}
          onEndFromChange={onEndFromChange}
          endTime={endTime}
          onEndToChange={onEndToChange}
          fromDisplayTime={fromDisplayTime}
          setFromDisplayTime={setFromDisplayTime}
          toDisplayTime={toDisplayTime}
          setToDisplayTime={setToDisplayTime}
          openDateSelect={openDateSelect}
          setOpenDateSelect={setOpenDateSelect}
          openStartDatePicker={openStartDatePicker}
          setStartOpenDatePicker={setStartOpenDatePicker}
          openEndDatePicker={openEndDatePicker}
          setEndOpenDatePicker={setEndOpenDatePicker}
          disabled={
            queryText.includes("startTime") || queryText.includes("endTime")
          }
          recentSearches={recentSearches}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 6,
          md: 3.5,
          lg: 4,
        }}
      >
        <ConductorInput
          fullWidth
          label="Free text search"
          value={freeText}
          onTextInputChange={setFreeText}
          showClearButton
        />
      </Grid>
      <Grid
        display="flex"
        justifyContent="end"
        size={{
          xs: 12,
          sm: 6,
          md: 3,
          lg: 2.5,
        }}
      >
        <Grid alignSelf="center" size={5}>
          <Button
            id="reset-task-btn"
            variant="text"
            onClick={handleReset}
            style={{ width: "100%" }}
            startIcon={<ResetIcon />}
          >
            Reset
          </Button>
        </Grid>
        <Grid alignSelf="center">
          <SplitButton
            id="search-task-btn"
            startIcon={<SearchIcon />}
            options={[
              {
                label: "Show as code",
                onClick: () => setShowCodeDialog("active"),
              },
            ]}
            primaryOnClick={doSearch}
          >
            Search
          </SplitButton>
        </Grid>
      </Grid>
    </Grid>
  );
};
