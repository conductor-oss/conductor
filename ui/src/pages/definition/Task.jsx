import React from "react";
import { useRouteMatch } from "react-router-dom";
import sharedStyles from "../styles";
import { useFetch } from "../../utils/query";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { ReactJson, LinearProgress, Heading } from "../../components";

const useStyles = makeStyles(sharedStyles);

export default function TaskDefinition() {
  const classes = useStyles();
  const match = useRouteMatch();

  const { data: task, isFetching } = useFetch(
    `/metadata/taskdefs/${match.params.name}`
  );
  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Task Definition - ${match.params.name}</title>
      </Helmet>
      <div className={classes.header} style={{ paddingBottom: 20 }}>
        <Heading level={1}>Task Definition</Heading>
        <Heading level={4}>{match.params.name}</Heading>
      </div>
      {isFetching && <LinearProgress />}
      <div className={classes.tabContent}>
        {task && <ReactJson src={task} />}
      </div>
    </div>
  );
}
