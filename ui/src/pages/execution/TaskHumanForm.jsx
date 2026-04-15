import * as Yup from "yup";
import { Form, withFormik } from "formik";
import _ from "lodash";
import FormikInput from "../../components/formik/FormikInput";
import FormikStatusDropdown from "../../components/formik/FormikStatusDropdown";
import React from "react";
import { makeStyles } from "@material-ui/styles";
import { SecondaryButton } from "../../components";
import FormikJsonInput from "../../components/formik/FormikJsonInput";

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
  workflowInstanceId: Yup.string().required("Workflow Instance ID is required"),
  taskId: Yup.string().required("Task ID is required"),
  status: Yup.string().required("Status is required"),
  outputData: Yup.string().isJson(),
});

const useStyles = makeStyles({
  fields: {
    width: "100%",
    padding: 30,
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflowX: "hidden",
    overflowY: "auto",
    gap: 15,
  },
});

export default withFormik({
  enableReinitialize: true,
  mapPropsToValues: ({ initialData }) =>
    initData(initialData),
  validationSchema: validationSchema,
})(TaskHumanForm);

function initData(data) {
  return {
    workflowInstanceId: _.get(data, "workflowInstanceId", ""),
    taskId: _.get(data, "taskId", ""),
    status: _.get(data, "status", ""),
    outputData: _.get(data, "outputData", ""),
  }
}

function TaskHumanForm(props) {
  const {
    values,
    validateForm,
    completeTask
  } = props;
  const classes = useStyles();

  function formDataToRunPayload(form) {
    let payload = {
      workflowInstanceId: form.workflowInstanceId,
      taskId: form.taskId,
      status: form.status
    }

    if (form.outputData) {
      payload.outputData = JSON.parse(form.outputData)
    }

    return payload;
  }

  function handleCompleteTask() {
    validateForm().then((errors) => {
      if (Object.keys(errors).length === 0) {
        const payload = formDataToRunPayload(values);
        completeTask(payload)
      }
    })
  }

  return (
    <Form>
      <div className={classes.fields}>
        <FormikInput fullWidth label="Workflow instance ID" name="workflowInstanceId"/>
        <FormikInput fullWidth label="Task ID" name="taskId"/>
        <FormikStatusDropdown
          fullWidth
          label="Status"
          name="status"
        />
        <FormikJsonInput
          reinitialize
          height={200}
          label="Output data (JSON)"
          name="outputData"
        />
        <SecondaryButton onClick={handleCompleteTask}>
          Complete task
        </SecondaryButton>
      </div>
    </Form>
  );
}