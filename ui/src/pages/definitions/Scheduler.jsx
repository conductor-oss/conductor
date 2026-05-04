import React from "react";
import { NavLink, DataTable, Button } from "../../components";
import { makeStyles } from "@material-ui/styles";
import Header from "./Header";
import sharedStyles from "../styles";
import { Helmet } from "react-helmet";
import {
  useSchedulerDefs,
  useDeleteScheduler,
  usePauseScheduler,
  useResumeScheduler,
} from "../../data/scheduler";
import { useQueryClient } from "react-query";
import AddIcon from "@material-ui/icons/Add";
import IconButton from "@material-ui/core/IconButton";
import DeleteIcon from "@material-ui/icons/Delete";
import PauseIcon from "@material-ui/icons/Pause";
import PlayArrowIcon from "@material-ui/icons/PlayArrow";
import SchedulerDisabledBanner, {
  isSchedulerDisabled,
} from "../../components/SchedulerDisabledBanner";

const useStyles = makeStyles(sharedStyles);

export default function SchedulerDefinitions() {
  const classes = useStyles();
  const queryClient = useQueryClient();

  const { data: schedules, isFetching, error } = useSchedulerDefs();

  const invalidate = () => queryClient.invalidateQueries(["schedulerDefs"]);

  const { mutate: deleteSchedule } = useDeleteScheduler({
    onSuccess: invalidate,
  });

  const { mutate: pauseSchedule } = usePauseScheduler({
    onSuccess: invalidate,
  });

  const { mutate: resumeSchedule } = useResumeScheduler({
    onSuccess: invalidate,
  });

  const columns = [
    {
      name: "name",
      renderer: (name) => (
        <NavLink path={`/schedulerDef/${name}`}>{name}</NavLink>
      ),
      grow: 2,
    },
    {
      name: "cronExpression",
    },
    {
      name: "startWorkflowRequest",
      renderer: (val) => (val ? val.name : ""),
      label: "Workflow Name",
    },
    {
      name: "paused",
      renderer: (val) => (val ? "Paused" : "Active"),
      label: "Status",
    },
    {
      name: "zoneId",
      label: "Timezone",
    },
    {
      id: "actions",
      name: "name",
      renderer: (name, row) => (
        <div style={{ display: "flex", gap: 4 }}>
          {row.paused ? (
            <IconButton
              size="small"
              title="Resume"
              onClick={(e) => {
                e.stopPropagation();
                resumeSchedule({ name });
              }}
            >
              <PlayArrowIcon fontSize="small" />
            </IconButton>
          ) : (
            <IconButton
              size="small"
              title="Pause"
              onClick={(e) => {
                e.stopPropagation();
                pauseSchedule({ name });
              }}
            >
              <PauseIcon fontSize="small" />
            </IconButton>
          )}
          <IconButton
            size="small"
            title="Delete"
            onClick={(e) => {
              e.stopPropagation();
              if (window.confirm(`Delete schedule "${name}"?`)) {
                deleteSchedule({ name });
              }
            }}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </div>
      ),
      label: "Actions",
    },
  ];

  return (
    <div className={classes.wrapper}>
      <Header tabIndex={3} loading={isFetching} />
      <Helmet>
        <title>Conductor UI - Scheduler Definitions</title>
      </Helmet>

      <div className={classes.tabContent}>
        {isSchedulerDisabled(error) ? (
          <SchedulerDisabledBanner />
        ) : (
        <>
        <div className={classes.buttonRow}>
          <Button
            component={NavLink}
            path="/schedulerDef"
            startIcon={<AddIcon />}
          >
            New Schedule
          </Button>
        </div>

        {schedules && (
          <DataTable
            title={`${schedules.length} results`}
            localStorageKey="schedulerDefsTable"
            defaultShowColumns={[
              "name",
              "cronExpression",
              "startWorkflowRequest",
              "paused",
              "zoneId",
              "actions",
            ]}
            keyField="name"
            data={schedules}
            columns={columns}
          />
        )}
        </>
        )}
      </div>
    </div>
  );
}
