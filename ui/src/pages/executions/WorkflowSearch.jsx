import React, { useState } from "react";
import _ from "lodash";
import { FormControl, Grid, InputLabel } from "@material-ui/core";
import {
  Paper,
  Heading,
  PrimaryButton,
  Dropdown,
  Input,
} from "../../components";

import { workflowStatuses } from "../../utils/constants";
import { useQueryState } from "react-router-use-location-state";
import SearchTabs from "./SearchTabs";
import ResultsTable from "./ResultsTable";
import DateRangePicker from "../../components/DateRangePicker";
import { DEFAULT_ROWS_PER_PAGE } from "../../components/DataTable";
import { useWorkflowSearch, useWorkflowNames } from "../../utils/query";

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

export default function WorkflowPanel() {
  const classes = useStyles();

  const [freeText, setFreeText] = useQueryState("freeText", "");
  const [status, setStatus] = useQueryState("status", []);
  const [workflowType, setWorkflowType] = useQueryState("workflowType", []);
  const [workflowId, setWorkflowId] = useQueryState("workflowId", "");
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [lookback, setLookback] = useQueryState("lookback", "");

  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [queryFT, setQueryFT] = useState(buildQuery);

  const {
    data: resultObj,
    error,
    isFetching,
    refetch,
  } = useWorkflowSearch({
    page,
    rowsPerPage,
    sort,
    query: queryFT.query,
    freeText: queryFT.freeText,
  });

  // For dropdown
  const workflowNames = useWorkflowNames();

  function buildQuery() {
    const clauses = [];
    if (!_.isEmpty(workflowType)) {
      clauses.push(`workflowType IN (${workflowType.join(",")})`);
    }
    if (!_.isEmpty(workflowId)) {
      clauses.push(`workflowId="${workflowId}"`);
    }
    if (!_.isEmpty(status)) {
      clauses.push(`status IN (${status.join(",")})`);
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

  function doSearch() {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    // Only force refetch if query didn't change. Else let react-query detect difference and refetch automatically
    if (_.isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
  }

  const handlePage = (page) => {
    setPage(page);
  };

  const handleSort = (changedColumn, direction) => {
    const sort = `${changedColumn}:${direction.toUpperCase()}`;
    setPage(1);
    setSort(sort);
  };

  const handleRowsPerPage = (rowsPerPage) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

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
        <SearchTabs tabIndex={0} />
        <Grid container spacing={3} className={classes.controls}>
          <Grid item xs={5}>
            <Dropdown
              fullWidth
              label="Workflow Name"
              options={workflowNames}
              multiple
              freeSolo
              onChange={(evt, val) => setWorkflowType(val)}
              value={workflowType}
            />
          </Grid>
          <Grid item xs={3}>
            <Input
              fullWidth
              label="Workflow ID"
              defaultValue={workflowId}
              onBlur={setWorkflowId}
              clearable
            />
          </Grid>
          <Grid item xs={4}>
            <Dropdown
              label="Status"
              fullWidth
              options={workflowStatuses}
              multiple
              onChange={(evt, val) => setStatus(val)}
              value={status}
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
              fullWidth
              label="Lucene-syntax Query (Double-quote strings for Free Text Search)"
              defaultValue={freeText}
              onBlur={setFreeText}
              clearable
            />
          </Grid>
          <Grid item xs={1}>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <PrimaryButton onClick={doSearch}>Search</PrimaryButton>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>
      <ResultsTable
        resultObj={resultObj}
        error={error}
        busy={isFetching}
        page={page}
        rowsPerPage={rowsPerPage}
        sort={sort}
        setPage={handlePage}
        setRowsPerPage={handleRowsPerPage}
        setSort={handleSort}
      />
    </div>
  );
}
