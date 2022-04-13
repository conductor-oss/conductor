import { useFormikContext } from "formik";
import { useWorkflowNamesAndVersions } from "../../data/workflow";
import FormikDropdown from "./FormikDropdown";
import { useEffect } from "react";

export default function FormikVersionDropdown(props) {
  const { name } = props;
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();
  const {
    setFieldValue,
    values: { workflowName, workflowVersion },
  } = useFormikContext();

  useEffect(() => {
    if (workflowVersion && namesAndVersions.has(workflowName)) {
      const found = namesAndVersions
        .get(workflowName)
        .find((row) => row.version.toString() === workflowVersion);
      if (!found) {
        console.log(
          `Version ${workflowVersion} not found for new workflowName. Clearing dropdown.`
        );
        setFieldValue(name, null, false);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namesAndVersions, workflowName, workflowVersion]);

  const versions =
    workflowName && namesAndVersions.has(workflowName)
      ? namesAndVersions.get(workflowName).map((row) => "" + row.version)
      : [];

  return <FormikDropdown options={versions} {...props} />;
}
