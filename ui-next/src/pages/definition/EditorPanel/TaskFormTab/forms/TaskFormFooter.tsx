import { useMemo } from "react";
import { Box, FormControlLabel, Switch, Grid } from "@mui/material";
import { colors } from "theme/tokens/variables";
import JSONField from "./JSONField";
import ArrayForm from "./ArrayForm";
import { Input, Dropdown } from "../../../../../components";

import TaskFormSection from "./TaskFormSection";
import MuiTypography from "components/ui/MuiTypography";

type Props = {
  selectedNode: any;
  onChange: any;
};

const TaskFormFooter = ({ selectedNode, onChange }: Props) => {
  const currentTask = useMemo(() => selectedNode?.data?.task, [selectedNode]);
  return (
    <Box
      sx={{
        borderTop: `1px solid ${colors.blackXXLight}`,
        // padding: 6,
        paddingBottom: "100px",
        width: "100%",
      }}
    >
      <MuiTypography
        fontSize="8pt"
        textTransform="uppercase"
        opacity={0.6}
        padding="24px"
      >
        Additional Parameters
      </MuiTypography>
      <Box padding={6}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <FormControlLabel
              control={
                <JSONField
                  path="optional"
                  onChange={onChange}
                  taskJson={currentTask}
                >
                  <Switch color="primary" style={{ marginRight: 8 }} />
                </JSONField>
              }
              label="Optional"
            />
          </Grid>
        </Grid>
      </Box>
      <TaskFormSection title="Timeout Settings">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="timeoutPolicy"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Dropdown
                label="Timeout Policy"
                options={["RETRY", "TIME_OUT_WF", "ALERT_ONLY"]}
              />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="responseTimeoutSeconds"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Response Timeout (in seconds)" />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="timeoutSeconds"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Timeout (in seconds)" />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection title="Input &amp; Output Keys">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="inputKeys"
              onChange={onChange}
              taskJson={currentTask}
            >
              <ArrayForm
                title="Input Keys"
                valueColumnLabel="Name"
                addItemLabel="Add Key"
              />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="outputKeys"
              onChange={onChange}
              taskJson={currentTask}
            >
              <ArrayForm
                title="Output Keys"
                valueColumnLabel="Name"
                addItemLabel="Add Key"
              />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection title="Retry Settings">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="retryCount"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Retry Count" />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="retryLogic"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Dropdown
                label="Retry Logic"
                options={["FIXED", "EXPONENTIAL_BACKOFF"]}
              />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="retryDelaySeconds"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Retry Delay Seconds" />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection title="Rate Limiting">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="rateLimitPerFrequency"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Rate Limit Per Frequency" />
            </JSONField>
          </Grid>

          <Grid size={12}>
            <JSONField
              path="rateLimitFrequencyInSeconds"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Rate Limit Frequency (in seconds)" />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection title="Concurrency">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="concurrentExecLimit"
              onChange={onChange}
              taskJson={currentTask}
            >
              <Input fullWidth label="Concurrent Executions Limit" />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
    </Box>
  );
};

export default TaskFormFooter;
