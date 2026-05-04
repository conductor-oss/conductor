import { useFormikContext } from "formik";
import { useWorkflowVersions } from "../../data/workflow";
import { Select } from "../../components";
import { MenuItem, CircularProgress } from "@material-ui/core";
import { useEffect } from "react";
import _ from "lodash";
import { timestampRenderer } from "../../utils/helpers";

export default function FormikVersionDropdown(props) {
  const { name } = props;
  const {
    setFieldValue,
    values: { workflowName, workflowVersion },
  } = useFormikContext();

  const { data: versions, isLoading } = useWorkflowVersions(workflowName);

  const versionTime = (versionObj) => {
    return (
      versionObj &&
      timestampRenderer(versionObj.updateTime || versionObj.createTime)
    );
  }

  const handleResetVersion = (version) => {
    setFieldValue('workflowVersion', "" + version)
  };

  useEffect(() => {
    if (workflowVersion && versions) {
      const found = versions.find(
        (row) => row.version.toString() === workflowVersion
      );
      if (!found) {
        console.log(
          `Version ${workflowVersion} not found for new workflowName. Clearing dropdown.`
        );
        setFieldValue(name, null, false);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versions, workflowName, workflowVersion]);

  const versionList = versions || [];

  return <Select
    value={_.isUndefined(workflowVersion) ? "" : workflowVersion}
    displayEmpty
    renderValue={(v) =>
      isLoading
        ? "Loading versions..."
        : v === ""
        ? "Latest Version"
        : `Version ${v}`
    }
    onChange={(evt) => handleResetVersion(evt.target.value)}
    disabled={isLoading}
    {...props}
  >
    <MenuItem value="">Latest Version</MenuItem>
    {isLoading && (
      <MenuItem disabled>
        <CircularProgress size={16} style={{ marginRight: 8 }} />
        Loading...
      </MenuItem>
    )}
    {versionList.map((row) => (
      <MenuItem value={row.version} key={row.version}>
        Version {row.version} ({versionTime(row)})
      </MenuItem>
    ))}
  </Select>;
}
