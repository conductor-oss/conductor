import { Box, Paper, Typography } from "@mui/material";
import { useSelector } from "@xstate/react";
import { WorkflowEditContext } from "pages/definition/state";
import { pluginRegistry } from "plugins/registry";
import { WorkflowDependencies } from "plugins/registry/types";
import { useContext, useMemo } from "react";
import { scanTasksForDependenciesInWorkflow } from "utils/workflow";
import TaskFormSection from "../TaskFormTab/forms/TaskFormSection";

const DependenciesTab = () => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const workflowChanges = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.workflowChanges,
  );

  // Extract all dependencies from the workflow
  const rawDependencies = useMemo(
    () => scanTasksForDependenciesInWorkflow(workflowChanges),
    [workflowChanges],
  );

  // Map to the plugin interface shape
  const dependencies: WorkflowDependencies = useMemo(
    () => ({
      integrationNames: rawDependencies.integrationNames || [],
      promptNames: rawDependencies.promptNames || [],
      userFormsNameVersion: rawDependencies.userFormsNameVersion || [],
      schemas: rawDependencies.schemas || [],
      secrets: rawDependencies.secrets || [],
      env: rawDependencies.env || [],
      workflowName: rawDependencies.workflowName,
      workflowVersion: rawDependencies.workflowVersion,
    }),
    [rawDependencies],
  );

  // Get dependency sections from plugins
  const sections = pluginRegistry.getDependencySections();

  if (sections.length === 0) {
    return (
      <Box sx={{ width: "100%", pb: 6, p: 3 }}>
        <Typography variant="body2" color="text.secondary">
          No dependency sections available.
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ width: "100%", pb: 6 }}>
      <Paper
        square
        sx={{
          width: "100%",
          background: (theme) => theme.palette.customBackground.form,
        }}
      >
        {sections.map((section) => {
          const SectionComponent = section.component;
          return (
            <TaskFormSection key={section.id} title={section.title} collapsible>
              <SectionComponent dependencies={dependencies} />
            </TaskFormSection>
          );
        })}
      </Paper>
    </Box>
  );
};

export default DependenciesTab;
