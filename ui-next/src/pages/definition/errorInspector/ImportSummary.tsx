import { Stack, Typography, List, ListItem } from "@mui/material";
import { ErrorInspectorMachineEvents } from "./state";
import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";

const ImportSummaryComponent = ({
  errorInspectorActor,
}: {
  errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>;
}) => {
  const importSummary = useSelector(
    errorInspectorActor,
    (state) => state.context.importSummary,
  );
  return importSummary == null ? null : (
    <Stack p={2}>
      <Typography variant="h5">Successfully imported</Typography>
      <List>
        {importSummary?.workflowResponse.length > 0 && (
          <ListItem>
            <Typography variant="body1" sx={{ fontSize: "1.1rem" }}>
              ⚡ Workflows: {importSummary.workflowResponse.length}
            </Typography>
          </ListItem>
        )}

        {importSummary?.workflowResponse.length > 0 && (
          <ListItem>
            <Typography variant="body1" sx={{ fontSize: "1.1rem" }}>
              ⚙️ Tasks: {importSummary.workflowResponse.length}
            </Typography>
          </ListItem>
        )}

        {importSummary?.userFormsResponse.length > 0 && (
          <ListItem>
            <Typography variant="body1" sx={{ fontSize: "1.1rem" }}>
              📝 User forms: {importSummary?.userFormsResponse.length}
            </Typography>
          </ListItem>
        )}

        {importSummary?.schemasResponse.length > 0 && (
          <ListItem>
            <Typography variant="body1" sx={{ fontSize: "1.1rem" }}>
              📋 Schemas: {importSummary?.schemasResponse.length}
            </Typography>
          </ListItem>
        )}

        {importSummary?.integrationsAndModelsResponse.length > 0 && (
          <ListItem>
            <Typography variant="body1" sx={{ fontSize: "1.1rem" }}>
              🔌 Integrations:{" "}
              {importSummary?.integrationsAndModelsResponse.length}
            </Typography>
          </ListItem>
        )}
      </List>
    </Stack>
  );
};

export default ImportSummaryComponent;
