import React, { useMemo } from "react";
import { useRouteMatch, useHistory } from "react-router-dom";
import { Grid, MenuItem } from "@material-ui/core";
import sharedStyles from "../styles";
import { useFetch, useWorkflowNamesAndVersions } from "../../utils/query";
import { makeStyles } from "@material-ui/styles";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import WorkflowGraph from "../../components/diagram/WorkflowGraph";
import { Helmet } from "react-helmet";
import { ReactJson, LinearProgress, Heading, Select } from "../../components";
import _ from "lodash";

const useStyles = makeStyles(sharedStyles);

export default function WorkflowDefinition() {
  const classes = useStyles();
  const history = useHistory();
  const match = useRouteMatch();
  const workflowName = _.get(match, "params.name");
  const version = _.get(match, "params.version");

  let path = `/metadata/workflow/${workflowName}`;
  if (version) path += `?version=${version}`;

  const { data: workflow, isFetching } = useFetch(path);
  const dag = useMemo(
    () => workflow && new WorkflowDAG(null, workflow),
    [workflow]
  );

  const namesAndVersions = useWorkflowNamesAndVersions();
  let versions = namesAndVersions.get(workflowName) || [];

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Workflow Definition - {match.params.name}</title>
      </Helmet>
      <div className={classes.header} style={{ paddingBottom: 20 }}>
        <Heading level={1}>Workflow Definition</Heading>
        <Heading level={4} gutterBottom>
          {match.params.name}
        </Heading>

        <Select
          value={_.isUndefined(version) ? "" : version}
          displayEmpty
          renderValue={(v) => (v === "" ? "Latest Version" : v)}
          onChange={(evt) =>
            history.push(
              `/workflowDef/${workflowName}${
                evt.target.value === "" ? "" : "/"
              }${evt.target.value}`
            )
          }
        >
          <MenuItem value="">Latest Version</MenuItem>
          {versions.map((ver) => (
            <MenuItem value={ver} key={ver}>
              Version {ver}
            </MenuItem>
          ))}
        </Select>
      </div>
      {isFetching && <LinearProgress />}
      <div className={classes.tabContent}>
        <Grid container>
          <Grid item xs={6}>
            {dag && <WorkflowGraph dag={dag} />}
          </Grid>
          <Grid item xs={6}>
            {workflow && <ReactJson src={workflow} />}
          </Grid>
        </Grid>
      </div>
    </div>
  );
}
