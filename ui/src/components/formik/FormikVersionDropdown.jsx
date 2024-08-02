import { useFormikContext } from "formik";
import { useWorkflowNamesAndVersions } from "../../data/workflow";
import { Select } from "../../components";
import { MenuItem } from "@material-ui/core";
import { useEffect } from "react";
import _ from "lodash";
import { timestampRenderer } from "../../utils/helpers";

export default function FormikVersionDropdown(props) {
  const { name } = props;
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();
  const {
    setFieldValue,
    values: { workflowName, workflowVersion },
  } = useFormikContext();

  const versionTime = (versionObj) => {
    return (
      versionObj &&
      timestampRenderer(versionObj.updateTime || versionObj.createTime)
    );
  }

  // Version Change or Reset
  const handleResetVersion = (version) => {
    setFieldValue('workflowVersion', "" + version)
  };

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
      ? namesAndVersions.get(workflowName)
      : [];

  return <Select
    value={_.isUndefined(workflowVersion) ? "" : workflowVersion}
    displayEmpty
    renderValue={(v) =>
      v === "" ? "Latest Version" : `Version ${v}`
    }
    onChange={(evt) => handleResetVersion(evt.target.value)}
    {...props}
  >
    <MenuItem value="">Latest Version</MenuItem>
    {versions.map((row) => (
      <MenuItem value={row.version} key={row.version}>
        Version {row.version} ({versionTime(row)})
      </MenuItem>
    ))}
  </Select>;
}
