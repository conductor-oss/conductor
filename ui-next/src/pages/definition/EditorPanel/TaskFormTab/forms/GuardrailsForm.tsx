import { Box, FormControlLabel, Grid, Switch } from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { FunctionComponent, useState } from "react";
import { useWorkflowNames } from "utils/query";
import ConductorFlexibleAutoCompleteVariables from "./ConductorFlexibleAutoCompleteVariables";

interface GuardrailsFormProps {
  onChange: (value: any) => void;
  taskJson: any;
}

type GuardrailType = "JAVASCRIPT" | "HTTP" | "WORKFLOW";
type FailureMode = "FAIL" | "WARN";

interface GuardrailConfig {
  type?: GuardrailType;
  target?: string;
  failureMode?: FailureMode;
}

const TYPE_ITEMS = ["JAVASCRIPT", "HTTP", "WORKFLOW"];
const FAILURE_ITEMS = [
  { label: "FAIL (hard gate)", value: "FAIL" },
  { label: "WARN (soft gate)", value: "WARN" },
];

const targetLabel = (type?: GuardrailType) => {
  switch (type) {
    case "JAVASCRIPT":
      return "JavaScript expression (reads $.prompt)";
    case "HTTP":
      return "HTTP endpoint URL (POST {prompt})";
    case "WORKFLOW":
      return "Guardrail workflow name";
    default:
      return "Target";
  }
};

/**
 * Editor for a single guardrail (input or output) stored at
 * inputParameters.<paramKey> as { type, target, failureMode }.
 */
const GuardrailEditor: FunctionComponent<{
  title: string;
  description: string;
  paramKey: "inputGuardrail" | "outputGuardrail";
  taskJson: any;
  onChange: (value: any) => void;
  workflowNames: string[];
}> = ({ title, description, paramKey, taskJson, onChange, workflowNames }) => {
  const existing: GuardrailConfig | undefined =
    taskJson?.inputParameters?.[paramKey];
  const [enabled, setEnabled] = useState<boolean>(!!existing);

  const value: GuardrailConfig = existing ?? {};

  const writeConfig = (next: GuardrailConfig | undefined) => {
    const inputParameters = { ...(taskJson.inputParameters ?? {}) };
    if (next === undefined) {
      delete inputParameters[paramKey];
    } else {
      inputParameters[paramKey] = next;
    }
    onChange({ ...taskJson, inputParameters });
  };

  const toggle = () => {
    if (enabled) {
      writeConfig(undefined);
      setEnabled(false);
    } else {
      writeConfig({ failureMode: "FAIL" });
      setEnabled(true);
    }
  };

  const patch = (p: Partial<GuardrailConfig>) => writeConfig({ ...value, ...p });

  // Client-side validation mirrors the server's GuardrailConfigValidator.
  const typeError = enabled && !value.type;
  const targetError =
    enabled && (!value.target || `${value.target}`.trim() === "");

  return (
    <Box sx={{ mt: 2 }}>
      <FormControlLabel
        labelPlacement="end"
        checked={enabled}
        control={<Switch color="primary" onChange={toggle} />}
        label={<Box sx={{ fontWeight: 600, color: "#767676" }}>{title}</Box>}
      />
      <Box style={{ opacity: 0.5 }}>{description}</Box>
      {enabled && (
        <Grid
          container
          spacing={2}
          marginTop={1}
          justifyContent="flex-start"
          alignItems="flex-start"
        >
          <Grid size={6}>
            <ConductorSelect
              label="Type"
              fullWidth
              items={TYPE_ITEMS}
              value={value.type ?? ""}
              error={!!typeError}
              helperText={typeError ? "Type is required" : undefined}
              onTextInputChange={(v) => patch({ type: v as GuardrailType })}
            />
          </Grid>
          <Grid size={6}>
            <ConductorSelect
              label="Failure mode"
              fullWidth
              items={FAILURE_ITEMS}
              value={value.failureMode ?? "FAIL"}
              onTextInputChange={(v) => patch({ failureMode: v as FailureMode })}
            />
          </Grid>
          <Grid size={12}>
            {value.type === "WORKFLOW" ? (
              <ConductorFlexibleAutoCompleteVariables
                label={targetLabel(value.type)}
                options={workflowNames}
                value={value.target ?? ""}
                onChange={(v: any) => patch({ target: v })}
              />
            ) : (
              <ConductorInput
                label={targetLabel(value.type)}
                fullWidth
                multiline={value.type === "JAVASCRIPT"}
                minRows={value.type === "JAVASCRIPT" ? 2 : 1}
                value={value.target ?? ""}
                error={!!targetError}
                helperText={targetError ? "Target is required" : undefined}
                onTextInputChange={(v) => patch({ target: v })}
              />
            )}
          </Grid>
        </Grid>
      )}
    </Box>
  );
};

export const GuardrailsForm: FunctionComponent<GuardrailsFormProps> = ({
  onChange,
  taskJson,
}) => {
  const workflowNames = useWorkflowNames();
  return (
    <Box>
      <Box style={{ opacity: 0.5 }}>
        Guardrails scrub or validate the prompt before it reaches the LLM
        (input) and the response before it returns (output). Each guardrail runs
        as a linked sub-workflow.
      </Box>
      <GuardrailEditor
        title="Input guardrail"
        description="Runs on the prompt (instructions, user input, and messages) before the LLM call."
        paramKey="inputGuardrail"
        taskJson={taskJson}
        onChange={onChange}
        workflowNames={workflowNames}
      />
      <GuardrailEditor
        title="Output guardrail"
        description="Runs on the LLM response before it returns to the workflow."
        paramKey="outputGuardrail"
        taskJson={taskJson}
        onChange={onChange}
        workflowNames={workflowNames}
      />
    </Box>
  );
};
