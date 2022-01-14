import React, { useMemo } from "react";
import { useRouteMatch } from "react-router-dom";
import sharedStyles from "../styles";
import { useFetch } from "../../utils/query";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { ReactJson, LinearProgress, Heading } from "../../components";

const useStyles = makeStyles(sharedStyles);

export default function EventHandlerDefinition() {
  const classes = useStyles();
  const match = useRouteMatch();

  // TODO: Need API that returns individual event handler by name.
  const { data, isFetching } = useFetch("/event");

  const eventHandler = useMemo(
    () => data && data.find((row) => row.name === match.params.name),
    [data, match.params.name]
  );

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>
          Conductor UI - Event Handler Definition - ${match.params.name}
        </title>
      </Helmet>
      <div className={classes.header} style={{ paddingBottom: 20 }}>
        <Heading level={1}>Event Handler Definition</Heading>
        <Heading level={4}>{match.params.name}</Heading>
      </div>
      {isFetching && <LinearProgress />}
      <div className={classes.tabContent}>
        {eventHandler && <ReactJson src={eventHandler} />}
      </div>
    </div>
  );
}
