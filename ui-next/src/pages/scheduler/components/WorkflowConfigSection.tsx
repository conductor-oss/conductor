import { Grid } from "@mui/material";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { SMALL_EDITOR_DEFAULT_OPTIONS } from "utils/constants";
import { IdempotencyValuesProp } from "../../definition/RunWorkflow/state";
import IdempotencyForm from "../../runWorkflow/IdempotencyForm";

interface WorkflowConfigSectionProps {
  workflowType: string | null;
  setWorkflowType: (workflowType: string) => void;
  workflowVersion: string | null;
  setWorkflowVersion: (workflowVersion: string | null) => void;
  workflowVersions: string[];
  workflowNames: string[];
  workflowInputTemplate: string;
  setWorkflowInputTemplate: (value: string) => void;
  workflowCorrelationId: string;
  setWorkflowCorrelationId: (value: string) => void;
  idempotencyValues: {
    idempotencyKey?: string;
    idempotencyStrategy?: any;
  };
  handleIdempotencyValues: (data: IdempotencyValuesProp) => void;
  errors?: any;
}

export function WorkflowConfigSection({
  workflowType,
  setWorkflowType,
  workflowVersion,
  setWorkflowVersion,
  workflowVersions,
  workflowNames,
  workflowInputTemplate,
  setWorkflowInputTemplate,
  workflowCorrelationId,
  setWorkflowCorrelationId,
  idempotencyValues,
  handleIdempotencyValues,
  errors,
}: WorkflowConfigSectionProps) {
  return (
    <>
      <Grid size={{ xs: 12, md: 9 }}>
        <ConductorAutoComplete
          fullWidth
          required
          label="Workflow name"
          options={workflowNames}
          onChange={(__, val: any) => setWorkflowType(val)}
          value={workflowType}
          error={errors?.["startWorkflowRequest.name"]}
          helperText={errors ? errors["startWorkflowRequest.name"] : undefined}
        />
      </Grid>
      <Grid size={{ xs: 12, md: 3 }}>
        <ConductorAutoComplete
          fullWidth
          disableClearable
          label="Workflow version"
          options={[...workflowVersions, "Latest version"]}
          onChange={(_, val: any) => setWorkflowVersion(val)}
          value={workflowVersion === "" ? "Latest version" : workflowVersion}
          conductorInputProps={{
            tooltip: {
              title: "Workflow version",
              content: "Optional, by default the latest version is triggered",
            },
          }}
        />
      </Grid>
      <Grid size={12}>
        <ConductorCodeBlockInput
          label="Input params"
          minHeight={350}
          defaultLanguage="json"
          value={workflowInputTemplate}
          onChange={setWorkflowInputTemplate}
          options={SMALL_EDITOR_DEFAULT_OPTIONS}
        />
      </Grid>
      <Grid size={12}>
        <ConductorInput
          fullWidth
          label="Correlation id"
          value={workflowCorrelationId}
          onTextInputChange={setWorkflowCorrelationId}
        />
      </Grid>
      <Grid size={12}>
        <Grid container spacing={3} paddingBottom={2}>
          <IdempotencyForm
            idempotencyValues={idempotencyValues}
            onChange={handleIdempotencyValues}
          />
        </Grid>
      </Grid>
    </>
  );
}
