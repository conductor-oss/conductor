import React, { useState, useMemo } from "react";
import _ from "lodash";
import { FormControl, Grid, InputLabel } from "@material-ui/core";
import {
  Paper,
  PrimaryButton,
  Heading,
  Dropdown,
  Input,
} from "../../components";

import {
  useTaskSearch,
  useTaskNames,
  useWorkflowNames,
} from "../../utils/query";

import DateRangePicker from "../../components/DateRangePicker";
import { useQueryState } from "react-router-use-location-state";
import SearchTabs from "./SearchTabs";
import ResultsTable from "./ResultsTable";
import { DEFAULT_ROWS_PER_PAGE } from "../../components/DataTable";

import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import executionsStyles from "./executionsStyles";
import sharedStyles from "../styles";

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const DEFAULT_SORT = "startTime:DESC";
const MS_IN_DAY = 86400000;

export default function TaskSearchPanel() {
  const classes = useStyles();

  const [workflowType, setWorkflowType] = useQueryState("workflowType", []);
  const [tasks, setTasks] = useQueryState("tasks", []);
  const [taskId, setTaskId] = useQueryState("taskId", "");
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [freeText, setFreeText] = useQueryState("taskText", "");
  const [lookback, setLookback] = useQueryState("lookback", "");
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [queryFT, setQueryFT] = useState(buildQuery);

  // For dropdowns
  const workflowNames = useWorkflowNames();
  const taskNames = useTaskNames();

  const searchReady = !(
    _.isEmpty(workflowType) &&
    _.isEmpty(tasks) &&
    _.isEmpty(taskId) &&
    _.isEmpty(freeText)
  );

  const { data, isFetching, fetchNextPage, refetch } = useTaskSearch({
    sort,
    query: queryFT.query,
    freeText: queryFT.freeText,
    rowsPerPage: DEFAULT_ROWS_PER_PAGE,
    searchReady,
  });
  const results = useMemo(
    () =>
      data
        ? [].concat.apply(
            [],
            data.pages.map((page) => page.results)
          )
        : [],
    [data]
  );

  function buildQuery() {
    const clauses = [];
    if (!_.isEmpty(workflowType)) {
      clauses.push(`workflowType IN (${workflowType.join(",")})`);
    }
    if (!_.isEmpty(taskId)) {
      clauses.push(`taskId="${taskId}"`);
    }
    if (!_.isEmpty(tasks)) {
      clauses.push(`taskType IN (${tasks.join(",")})`);
    }
    if (!_.isEmpty(lookback)) {
      clauses.push(`startTime>${new Date().getTime() - lookback * MS_IN_DAY}`);
    }
    if (!_.isEmpty(startFrom)) {
      clauses.push(`startTime>${new Date(startFrom).getTime()}`);
    }
    if (!_.isEmpty(startTo)) {
      clauses.push(`startTime<${new Date(startTo).getTime()}`);
    }

    return {
      query: clauses.join(" AND "),
      freeText: _.isEmpty(freeText) ? "*" : freeText,
    };
  }

  function handleSearch() {
    const oldQuery = queryFT;
    const newQuery = buildQuery();
    setQueryFT(newQuery);

    if (oldQuery === newQuery) {
      console.log("refetching");
      refetch();
    }
  }

  function handlePage(page) {
    fetchNextPage();
  }

  function handleSort(changedColumn, direction) {
    setSort(`${changedColumn}:${direction.toUpperCase()}`);
  }

  const handleLookback = (val) => {
    setStartFrom("");
    setStartTo("");
    setLookback(val);
  };

  const handleStartFrom = (val) => {
    setLookback("");
    setStartFrom(val);
  };

  const handleStartTo = (val) => {
    setLookback("");
    setStartTo(val);
  };

  return (
    <div className={clsx([classes.wrapper, classes.padded])}>
      <Heading level={3} className={classes.heading}>
        Workflow Executions
      </Heading>

      <Paper className={classes.paper}>
        <SearchTabs tabIndex={1} />
        <Grid container spacing={3} className={classes.controls}>
          <Grid item xs={5}>
            <Dropdown
              label="Workflow Name"
              fullWidth
              freeSolo
              options={workflowNames}
              multiple
              onChange={(evt, val) => setWorkflowType(val)}
              value={workflowType}
            />
          </Grid>
          <Grid item xs={3}>
            <Input
              fullWidth
              label="Includes Task ID"
              defaultValue={taskId}
              onBlur={setTaskId}
              clearable
            />
          </Grid>
          <Grid item xs={4}>
            <Dropdown
              label="Includes Task Name"
              fullWidth
              freeSolo
              options={taskNames}
              multiple
              onChange={(evt, val) => setTasks(val)}
              value={tasks}
            />
          </Grid>
          <Grid item xs={4}>
            <DateRangePicker
              disabled={!_.isEmpty(lookback)}
              label="Start Time"
              from={startFrom}
              to={startTo}
              onFromChange={handleStartFrom}
              onToChange={handleStartTo}
            />
          </Grid>
          <Grid item xs={1}>
            <Input
              fullWidth
              label="Lookback (days)"
              defaultValue={lookback}
              onBlur={handleLookback}
              type="number"
              clearable
              disabled={!_.isEmpty(startFrom) || !_.isEmpty(startTo)}
            />
          </Grid>
          <Grid item xs={6}>
            <Input
              label="Lucene-syntax Query (Double-quote strings for Free Text Search)"
              defaultValue={freeText}
              fullWidth
              onBlur={setFreeText}
              clearable
            />
          </Grid>

          <Grid item xs={1}>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <PrimaryButton onClick={handleSearch}>Search</PrimaryButton>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>
      <ResultsTable
        resultObj={{ results: results }}
        busy={isFetching}
        page={0}
        setPage={handlePage}
        setSort={handleSort}
        rowsPerPage={DEFAULT_ROWS_PER_PAGE}
        sort={sort}
        showMore
      />
    </div>
  );
}
