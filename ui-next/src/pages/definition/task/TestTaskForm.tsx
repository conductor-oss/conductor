import { Box, Grid, Link } from "@mui/material";
import { Button } from "components/index";
import { Play } from "@phosphor-icons/react";
import MuiTypography from "components/MuiTypography";
import ConductorInput from "components/v1/ConductorInput";
import { WORKFLOW_EXECUTION_URL } from "utils/constants/route";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";

export type TestTaskFormProps = {
  handleRunTestTask: () => void;
  isNewTaskDef: boolean;
  setInputParameters: (value: string) => void;
  setTaskDomain: (value: string) => void;
  showTestTask: boolean;
  testInputParameters: string;
  testTaskDomain: string;
  testTaskWorkflowId: string;
};

const TestTaskForm = ({
  handleRunTestTask,
  isNewTaskDef,
  setInputParameters,
  setTaskDomain,
  showTestTask,
  testInputParameters,
  testTaskDomain,
  testTaskWorkflowId,
}: TestTaskFormProps) => {
  return !isNewTaskDef && showTestTask ? (
    <Grid
      container
      spacing={3}
      sx={{
        width: "100%",
        alignItems: "center",
        borderTop: "1px solid rgba(0, 0, 0, .25)",
        marginTop: 4,
      }}
    >
      {
        <>
          <Grid size={12}>
            <ConductorCodeBlockInput
              language="json"
              value={testInputParameters}
              label="Input parameters"
              onChange={(value) => {
                setInputParameters(value);
              }}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 12,
              md: 4,
            }}
          >
            <ConductorInput
              label="Domain"
              value={testTaskDomain}
              onChange={(event) => setTaskDomain(event.target.value)}
              placeholder="Enter domain"
            />
          </Grid>
          <Grid size={12}>
            <Box display="flex" gap={2}>
              <Button
                color="primary"
                disabled={isNewTaskDef}
                onClick={handleRunTestTask}
                sx={{ marginTop: 3, marginBottom: 3 }}
                startIcon={<Play />}
              >
                Run
              </Button>
              {testTaskWorkflowId ? (
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    marginLeft: 2,
                    gap: 2,
                  }}
                >
                  <MuiTypography>Workflow started at:</MuiTypography>
                  <MuiTypography>
                    <Link
                      href={`${WORKFLOW_EXECUTION_URL.BASE}/${testTaskWorkflowId}`}
                    >
                      {testTaskWorkflowId}
                    </Link>
                  </MuiTypography>
                </Box>
              ) : null}
            </Box>
          </Grid>
        </>
      }
    </Grid>
  ) : null;
};

export default TestTaskForm;
