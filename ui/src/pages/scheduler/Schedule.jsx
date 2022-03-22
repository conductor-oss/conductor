import React, { useMemo } from "react";
import { Button, Paper, Heading, LinearProgress } from "../../components";
import FormikInput from "../../components/formik/FormikInput";
import FormikJsonInput from "../../components/formik/FormikJsonInput";
import FormikDropdown from "../../components/formik/FromikDropdown";
import FormikCronEditor from "../../components/formik/FormikCronEditor";
import clsx from "clsx";
import { makeStyles } from "@material-ui/styles";
import _ from "lodash";
import { useSaveSchedule, useSchedule } from "./schedulerHooks";
import { useWorkflowNamesAndVersions } from "../../utils/query";
import sharedStyles from "../../pages/styles";
import { Helmet } from "react-helmet";
import { useRouteMatch } from "react-router-dom";
import { usePushHistory } from "../../components/NavLink";
import { Formik, Form, useFormikContext } from "formik";

import * as Yup from "yup";
import FormikSwitch from "../../components/formik/FormikSwitch";

const useStyles = makeStyles({
  ...sharedStyles,
  paper: {
    margin: 20,
    padding: 20,
  },
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
  },
  fields: {
    display: "flex",
    flexDirection: "column",
    gap: 15,
  },
});
Yup.addMethod(Yup.string, "isJson", function () {
  return this.test("is-json", "is not valid json", (value) => {
    if (_.isEmpty(value)) return true;

    try {
      JSON.parse(value);
    } catch (e) {
      return false;
    }
    return true;
  });
});
const validationSchema = Yup.object({
  name: Yup.string().required("Schedule Name is required"),
  cronExpression: Yup.string().required(),
  workflowName: Yup.string().required("Workflow Name is required"),
  workflowInput: Yup.string().isJson(),
  taskToDomain: Yup.string().isJson(),
});

export default function Schedule() {
  const classes = useStyles();
  const match = useRouteMatch();
  const navigate = usePushHistory();
  const { data: schedule, isLoading } = useSchedule(match.params.name);
  const { mutate: saveSchedule, isLoading: isSavingSchedule } = useSaveSchedule(
    {
      onSuccess: () => {
        navigate("/scheduler");
      },
    }
  );
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();
  const workflowNames = useMemo(
    () => (namesAndVersions ? Array.from(namesAndVersions.keys()) : []),
    [namesAndVersions]
  );

  const formData = useMemo(
    () =>
      !isLoading
        ? {
            name: _.get(schedule, "name", ""),
            enabled: _.get(schedule, "enabled", true),
            cronExpression: _.get(schedule, "cronExpression", ""),
            workflowName: _.get(schedule, "triggerPayload.name", ""),
            workflowVersion: _.get(schedule, "triggerPayload.version", ""),
            workflowInput: _.has(schedule, "triggerPayload.input")
              ? JSON.stringify(schedule.triggerPayload.input, null, 2)
              : "",
            correlationId: _.get(schedule, "triggerPayload.correlationId", ""),
            taskToDomain: _.has(schedule, "triggerPayload.taskToDomain")
              ? JSON.stringify(schedule.triggerPayload.taskToDomain, null, 2)
              : "",
            workflowDef: _.get(schedule, "triggerPayload.workflowDef", ""),
          }
        : null,
    [isLoading, schedule]
  );

  function handleSubmit(val) {
    const submission = {
      id: _.get(schedule, "id"),
      enabled: val.enabled,
      name: val.name,
      cronExpression: val.cronExpression,
      triggerPayload: {
        name: val.workflowName,
        version: val.workflowVersion,
        input: parse(val.workflowInput),
        correlationId: val.correlationId,
        taskToDomain: parse(val.taskToDomain),
      },
    };

    saveSchedule(submission);
  }

  return formData ? (
    <Formik
      initialValues={formData}
      onSubmit={handleSubmit}
      validationSchema={validationSchema}
    >
      <Form className={classes.wrapper}>
        {(isLoading || isSavingSchedule) && <LinearProgress />}

        <Helmet>
          <title>Conductor UI - Workflow Schedule</title>
        </Helmet>
        <div className={clsx([classes.header, classes.paddingBottom])}>
          <Button
            type="submit"
            className={classes.submitButton}
            disabled={isSavingSchedule}
          >
            Save Changes
          </Button>

          <Heading level={3} gutterBottom>
            Workflow Schedule
          </Heading>
          <Heading level={0}>
            {schedule ? schedule.name : "New Schedule"}
          </Heading>
          <div>{_.get(schedule, "id")}</div>
        </div>

        <div className={clsx([classes.tabContent, classes.fields])}>
          <FormikSwitch name="enabled" label="Enabled" />

          <FormikInput
            className={clsx([classes.field, classes.name])}
            label="Schedule Name"
            name="name"
            fullWidth
          />

          <FormikCronEditor
            className={classes.field}
            label="Cron Expression"
            name="cronExpression"
          />

          <Paper className={classes.padded} variant="outlined">
            <FormikDropdown
              fullWidth
              className={classes.field}
              label="Workflow Name"
              options={workflowNames}
              name="workflowName"
            />

            <VersionDropdown
              fullWidth
              className={classes.field}
              label="Workflow version"
              name="workflowVersion"
            />

            <FormikJsonInput
              fullWidth
              className={classes.field}
              label="Input (JSON)"
              name="workflowInput"
            />

            <FormikInput
              fullWidth
              className={classes.field}
              label="Correlation ID"
              name="correlationId"
            />

            <FormikJsonInput
              className={classes.field}
              label="Task to Domain (JSON)"
              name="taskToDomain"
            />
          </Paper>
        </div>
      </Form>
    </Formik>
  ) : (
    <LinearProgress />
  );
}

function VersionDropdown(props) {
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();
  const {
    values: { workflowName },
  } = useFormikContext();
  const versions =
    workflowName && namesAndVersions.has(workflowName)
      ? namesAndVersions.get(workflowName).map((row) => "" + row.version)
      : [];

  return <FormikDropdown options={versions} {...props} />;
}

function parse(string) {
  if (_.isEmpty(string)) {
    return null;
  } else {
    return JSON.parse(string);
  }
}
