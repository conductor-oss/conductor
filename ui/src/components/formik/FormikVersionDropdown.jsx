import { useFormikContext } from "formik";
import { useWorkflowNamesAndVersions } from "../../data/workflow";
import FormikDropdown from "./FormikDropdown";
import { useEffect } from "react";
import _ from "lodash";

export default function FormikVersionDropdown(props) {
  const { name } = props;
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();
  const {
    setFieldValue,
    values: { workflowName, workflowVersion },
  } = useFormikContext();

  useEffect(() => {
    if (workflowVersion && workflowName) {
      const found = _.get(namesAndVersions, workflowName, []).find(
        (row) => row.version.toString() === workflowVersion
      );

      if (!found) {
        console.log(
          `Version ${workflowVersion} not found for new workflowName ${workflowName}. Clearing version dropdown.`
        );
        setFieldValue(name, null, false);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namesAndVersions, workflowName, workflowVersion]);

  const versions = _.get(namesAndVersions, workflowName, []).map(
    (row) => "" + row.version
  );

  return <FormikDropdown options={versions} {...props} />;
}
