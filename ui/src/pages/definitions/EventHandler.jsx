import React from "react";
import { NavLink, DataTable, Button } from "../../components";
import { makeStyles } from "@material-ui/styles";
import Header from "./Header";
import sharedStyles from "../styles";
import { Helmet } from "react-helmet";
import { useEventHandlers } from "../../data/misc";
import AddIcon from "@material-ui/icons/Add";

const useStyles = makeStyles(sharedStyles);

const columns = [
  {
    name: "name",
    renderer: (name, row) => (
      <NavLink path={`/eventHandlerDef/${row.event}/${name}`}>{name}</NavLink>
    ),
  },
  {
    name: "event"
  },
  { name: "active", renderer: (val) => val ? "Yes" : "No"  },
  { name: "condition" },
  {
    name: "actions",
    renderer: (val) => JSON.stringify(val.map((action) => action.action)),
  },
];

export default function EventHandlers() {
  const classes = useStyles();

  const { data: eventHandlers, isFetching } = useEventHandlers();

  return (
    <div className={classes.wrapper}>
      <Header tabIndex={2} loading={isFetching} />
      <Helmet>
        <title>Conductor UI - Event Handler Definitions</title>
      </Helmet>

      <div className={classes.tabContent}>
        <div className={classes.buttonRow}>
          <Button component={NavLink} path="/eventHandlerDef" startIcon={<AddIcon />}>
            New Event Handler Definition
          </Button>
        </div>

        {eventHandlers && (
          <DataTable
            title={`${eventHandlers.length} results`}
            localStorageKey="eventHandlersTable"
            defaultShowColumns={["event", "active", "name", "condition", "actions"]}
            keyField="name"
            data={eventHandlers}
            columns={columns}
          />
        )}
      </div>
    </div>
  );
}
