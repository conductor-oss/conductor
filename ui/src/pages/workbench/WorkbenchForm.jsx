import { Text, Pill } from "../../components";
import { Toolbar, IconButton, Tooltip } from "@material-ui/core";
import FormikInput from "../../components/formik/FormikInput";
import FormikJsonInput from "../../components/formik/FormikJsonInput";
import { makeStyles } from "@material-ui/styles";
import _ from "lodash";
import { Form, setNestedObjectValues, withFormik } from "formik";
import { useWorkflowDef } from "../../data/workflow";
import FormikVersionDropdown from "../../components/formik/FormikVersionDropdown";
import PlayArrowIcon from "@material-ui/icons/PlayArrow";
import PlaylistAddIcon from "@material-ui/icons/PlaylistAdd";
import SaveIcon from "@material-ui/icons/Save";
import { colors } from "../../theme/variables";
import { timestampRenderer } from "../../utils/helpers";
import * as Yup from "yup";
import FormikWorkflowNameInput from "../../components/formik/FormikWorkflowNameInput";

const useStyles = makeStyles({
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
  },
  toolbar: {
    backgroundColor: colors.gray14,
  },
  workflowName: {
    fontWeight: "bold",
  },
  main: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflow: "auto",
  },
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
  workflowName: Yup.string().required("Workflow Name is required"),
  workflowInput: Yup.string().isJson(),
  taskToDomain: Yup.string().isJson(),
});

export default withFormik({
  enableReinitialize: true,
  mapPropsToValues: ({ selectedRun }) =>
    runPayloadToFormData(_.get(selectedRun, "runPayload")),
  validationSchema: validationSchema,
})(WorkbenchForm);

function WorkbenchForm(props) {
  const {
    values,
    validateForm,
    setTouched,
    setFieldValue,
    dirty,
    selectedRun,
    saveRun,
    executeRun,
  } = props;
  const classes = useStyles();
  const { workflowName, workflowVersion } = values;
  const createTime = selectedRun ? selectedRun.createTime : undefined;

  const { refetch } = useWorkflowDef(workflowName, workflowVersion, null, {
    onSuccess: populateInput,
    enabled: false,
  });

  function triggerPopulateInput() {
    refetch();
  }

  function populateInput(workflowDef) {
    let bootstrap = {};

    if (!_.isEmpty(values.workflowInput)) {
      const existing = JSON.parse(values.workflowInput);
      bootstrap = _.pickBy(existing, (v) => v !== "");
    }

    if (workflowDef.inputParameters) {
      for (let param of workflowDef.inputParameters) {
        if (!_.has(bootstrap, param)) {
          bootstrap[param] = "";
        }
      }

      setFieldValue("workflowInput", JSON.stringify(bootstrap, null, 2));
    }
  }

  function handleRun() {
    validateForm().then((errors) => {
      if (Object.keys(errors).length === 0) {
        const payload = formDataToRunPayload(values);
        if (!dirty && createTime) {
          console.log("Executing pre-existing run. Append workflowRecord");
          executeRun(createTime, payload);
        } else {
          console.log("Executing new run. Save first then execute");
          const newRun = saveRun(payload);
          executeRun(newRun.createTime, payload);
        }
      } else {
        // Handle validation error manually (not using handleSubmit)
        setTouched(setNestedObjectValues(errors, true));
      }
    });
  }

  function handleSave() {
    validateForm().then((errors) => {
      if (Object.keys(errors).length === 0) {
        const payload = formDataToRunPayload(values);
        saveRun(payload);
      } else {
        setTouched(setNestedObjectValues(errors, true));
      }
    });
  }

  return (
    <Form className={classes.main}>
      <Toolbar className={classes.toolbar}>
        <Text className={classes.workflowName}>Workflow Workbench</Text>
        <Tooltip title="Execute Workflow">
          <IconButton onClick={handleRun}>
            <PlayArrowIcon />
          </IconButton>
        </Tooltip>

        <Tooltip title="Save Workflow Trigger">
          <div>
            <IconButton disabled={!dirty} onClick={handleSave}>
              <SaveIcon />
            </IconButton>
          </div>
        </Tooltip>

        <Tooltip title="Populate Input Parameters">
          <div>
            <IconButton
              disabled={!values.workflowName}
              onClick={triggerPopulateInput}
            >
              <PlaylistAddIcon />
            </IconButton>
          </div>
        </Tooltip>

        {dirty && <Pill label="Modified" />}
        {createTime && <Text>Created: {timestampRenderer(createTime)}</Text>}
      </Toolbar>

      <div className={classes.fields}>
        <FormikWorkflowNameInput
          fullWidth
          label="Workflow Name"
          name="workflowName"
        />

        <FormikVersionDropdown
          fullWidth
          label="Workflow version"
          name="workflowVersion"
        />

        <FormikJsonInput
          reinitialize
          height={200}
          label="Input (JSON)"
          name="workflowInput"
        />

        <FormikInput fullWidth label="Correlation ID" name="correlationId" />

        <FormikJsonInput
          className={classes.field}
          height={200}
          label="Task to Domain (JSON)"
          name="taskToDomain"
        />
      </div>
    </Form>
  );
}

function runPayloadToFormData(runPayload) {
  return {
    workflowName: _.get(runPayload, "name", ""),
    workflowVersion: _.get(runPayload, "version", ""),
    workflowInput: _.has(runPayload, "input")
      ? JSON.stringify(runPayload.input, null, 2)
      : "",
    correlationId: _.get(runPayload, "correlationId", ""),
    taskToDomain: _.has(runPayload, "taskToDomain")
      ? JSON.stringify(runPayload.taskToDomain, null, 2)
      : "",
  };
}

function formDataToRunPayload(form) {
  let runPayload = {
    name: form.workflowName,
  };
  if (form.workflowVersion) {
    runPayload.version = form.workflowVersion;
  }
  if (form.workflowInput) {
    runPayload.input = JSON.parse(form.workflowInput);
  }
  if (form.correlationId) {
    runPayload.correlationId = form.correlationId;
  }
  if (form.taskToDomain) {
    runPayload.taskToDomain = JSON.parse(form.taskToDomain);
  }
  return runPayload;
}

//  runHistoryRef.current.pushRun(runPayload);
