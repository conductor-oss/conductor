import { Box, Grid } from "@mui/material";
import { Paper, NavLink } from "components";
import { Helmet } from "react-helmet";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import { useQueryState } from "react-router-use-location-state";
import { useCallback, useMemo, useState } from "react";
import DataTable from "components/DataTable/DataTable";
import _isEmpty from "lodash/isEmpty";
import { colors } from "theme/tokens/variables";
import { EventExecutionDto } from "./types";
import TagChip from "components/TagChip";
import Header from "components/Header";
import ConductorInput from "components/v1/ConductorInput";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { LegacyColumn } from "components/DataTable/types";
import { createTableTitle } from "utils/helpers";
import ConductorMultiSelect from "components/v1/ConductorMultiSelect";
import { RefreshEvent } from "./EventMonitorDetail/Refresher/RefreshEvent";
import MuiTypography from "components/MuiTypography";
import { useRefreshMachine } from "./EventMonitorDetail/Refresher/state/hook";
import { PageType } from "./EventMonitorDetail/Refresher/state/types";

export const EVENT_STATUS_FILTER_QUERY_PARAM = "statusFilter";

const statusOptions: { name: string; label: string }[] = [
  {
    label: "Active",
    name: "ACTIVE",
  },
  {
    label: "Inactive",
    name: "INACTIVE",
  },
];

const ActiveChip = ({ active }: { active: boolean }) => {
  const color = active ? colors.successTag : colors.errorTag;
  const chipStyles =
    color == null
      ? {}
      : {
          backgroundColor: color,
        };

  return (
    <TagChip
      style={chipStyles}
      label={`${active ? "Active" : "Inactive"}`}
      id={`${active}-chip`}
    />
  );
};

const columns: LegacyColumn[] = [
  {
    id: "name",
    name: "name",
    label: "Name",
    minWidth: "120px",
    grow: 2,
    tooltip: "Name of the event handler",
    renderer: (name) => (
      <NavLink path={`/eventMonitor/${name}`}>{name}</NavLink>
    ),
  },
  {
    id: "event",
    name: "event",
    label: "Event",
    minWidth: "120px",
    grow: 2,
    tooltip: "Event field of the event handler definition",
  },
  {
    id: "active",
    name: "active",
    label: "Status",
    grow: 2,
    renderer: (active) => <ActiveChip active={active} />,
    tooltip: "The status of the event",
  },
  {
    id: "numberOfMessages",
    name: "numberOfMessages",
    label: "Number of Messages",
    tooltip: "Number of Messages received by the event handler",
    right: true,
  },
  {
    id: "numberOfActions",
    name: "numberOfActions",
    label: "Number of Actions",
    tooltip: "Number of Actions triggered by the event handler",
    right: true,
  },
];

const StatusBadge = ({ label }: { label: string }) => {
  const color = label === "Active" ? colors.successTag : colors.errorTag;
  const chipStyles =
    color == null
      ? {}
      : {
          backgroundColor: color,
        };

  return <TagChip style={chipStyles} label={label} id={`${label}-chip`} />;
};

export const EventMonitor = () => {
  const [
    { eventListData: data, isFetching, elapsed, refreshInterval, isError },
    { changeRefreshRate, handleRefresh },
  ] = useRefreshMachine(PageType.EVENT_LISTING);
  const [
    { pageParam, searchParam },
    { handlePageChange, handleSearchTermChange },
  ] = useCustomPagination();

  const [statusFilterParam, setStatusFilterParam] = useQueryState(
    EVENT_STATUS_FILTER_QUERY_PARAM,
    "",
  );

  const [statusFilter, setStatusFilter] = useState<string[]>(
    _isEmpty(statusFilterParam) ? [] : statusFilterParam.split(","),
  );

  const handleStatusChange = useCallback(
    (values: string[]) => {
      setStatusFilter(values);
      setStatusFilterParam(values.join(","));
    },
    [setStatusFilter, setStatusFilterParam],
  );

  const checkStatusMatch = useCallback(
    (x: EventExecutionDto) => {
      if (statusFilter.length > 0) {
        return statusFilter.includes(x.active ? "Active" : "Inactive");
      }
      return true;
    },
    [statusFilter],
  );

  const eventList = useMemo<EventExecutionDto[]>(() => {
    let result: EventExecutionDto[] = [];

    if (data?.results) {
      const lowerCaseSearchTerm = searchParam.toLowerCase();

      result = data.results.filter(
        (x) =>
          (x.name.toLowerCase()?.includes(lowerCaseSearchTerm) ||
            x.event.toLowerCase()?.includes(lowerCaseSearchTerm)) &&
          checkStatusMatch(x),
      );
    }
    return result;
  }, [checkStatusMatch, data?.results, searchParam]);

  const selectedStatusDisplay = () => {
    if (
      statusFilter.length === 0 ||
      statusFilter.length === statusOptions.length
    ) {
      return "All status";
    }

    return statusFilter.join(", ");
  };

  return (
    <>
      <Helmet>
        <title>Event Monitor</title>
      </Helmet>
      <SectionHeader _deprecate_marginTop={0} title="Event Monitor" />
      <SectionContainer>
        <Paper variant="outlined">
          <Header loading={isFetching} />
          <Box padding={6}>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                size={{
                  xs: 12,
                  md: 4,
                }}
              >
                <ConductorInput
                  id="search-event"
                  fullWidth
                  label="Quick search"
                  placeholder="Search event"
                  showClearButton
                  autoFocus
                  value={searchParam}
                  onTextInputChange={handleSearchTermChange}
                />
              </Grid>

              <Grid
                size={{
                  xs: 12,
                  md: 2,
                }}
              >
                <ConductorMultiSelect
                  dataTestId="event-list-status-filter"
                  label="Select status"
                  options={statusOptions.map((status) => status.label)}
                  onSelected={handleStatusChange}
                  value={statusFilter}
                  allText="All Status"
                  renderer={(option) => (
                    <StatusBadge key={option} label={option} />
                  )}
                />
              </Grid>
              <Grid
                size={{
                  xs: 12,
                  md: 6,
                }}
              >
                <RefreshEvent
                  elapsed={elapsed}
                  isFetching={isFetching}
                  refreshInterval={refreshInterval}
                  changeRefreshRate={changeRefreshRate}
                  handleRefresh={handleRefresh}
                />
              </Grid>
            </Grid>
          </Box>
          <DataTable
            localStorageKey="eventMonitor"
            noDataComponent={
              <Box padding={5} fontWeight={600}>
                {isError ? "Error while fetching events" : "No event found"}
              </Box>
            }
            defaultShowColumns={[
              "name",
              "event",
              "active",
              "numberOfMessages",
              "numberOfActions",
            ]}
            columns={columns}
            hideSearch
            data={eventList}
            title={true}
            titleComponent={
              <Box id="event-listing-data-title">
                {createTableTitle({
                  filteredData: eventList,
                  data: data?.results ? data.results : [],
                })}
              </Box>
            }
            customActions={[
              <MuiTypography key="select-status-caption">
                Showing status for : {selectedStatusDisplay()}
              </MuiTypography>,
            ]}
            onChangePage={handlePageChange}
            paginationDefaultPage={pageParam ? Number(pageParam) : 1}
          />
        </Paper>
      </SectionContainer>
    </>
  );
};
