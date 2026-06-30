import { Box, FormControlLabel, Grid, Switch, Typography } from "@mui/material";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { Button, IconButton } from "@mui/material";
import { FunctionComponent, useState } from "react";
import { useWorkflowNames, useWorkflowNamesAndVersions } from "utils/query";
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
  version?: number;
  headers?: Record<string, string>;
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
      return "JavaScript expression";
    case "HTTP":
      return "HTTP endpoint URL";
    case "WORKFLOW":
      return "Guardrail workflow name";
    default:
      return "Target";
  }
};

// The text to scrub is passed to every guardrail under `prompt`, and the
// scrubbed text must be returned under `prompt`. Shown as contract hints.
const contractHint = (type?: GuardrailType) => {
  switch (type) {
    case "JAVASCRIPT":
      return "Reads the text as $.prompt and must return the scrubbed string. e.g. $.prompt.replace(/[0-9]{13,19}/g, '[REDACTED]')";
    case "HTTP":
      return 'Method POST · request body { "prompt": "<text>" } · expected response body { "prompt": "<scrubbed text>" }. Failure or a missing prompt is treated per the failure mode.';
    case "WORKFLOW":
      return 'Started with input { "prompt": "<text>" }; must output { "prompt": "<scrubbed text>" }.';
    default:
      return "";
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
  versionsByName: Map<string, number[]>;
}> = ({
  title,
  description,
  paramKey,
  taskJson,
  onChange,
  workflowNames,
  versionsByName,
}) => {
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

  // HTTP headers are edited as key/value rows, persisted as a Record on the config.
  const [headerRows, setHeaderRows] = useState<{ key: string; value: string }[]>(
    () =>
      Object.entries(value.headers ?? {}).map(([k, v]) => ({
        key: k,
        value: String(v),
      })),
  );
  const writeHeaders = (rows: { key: string; value: string }[]) => {
    setHeaderRows(rows);
    const obj: Record<string, string> = {};
    rows.forEach((r) => {
      if (r.key.trim()) obj[r.key.trim()] = r.value;
    });
    patch({ headers: Object.keys(obj).length ? obj : undefined });
  };

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
            {value.type === "JAVASCRIPT" ? (
              <ConductorCodeBlockInput
                label={targetLabel(value.type)}
                language="javascript"
                languageLabel="ECMASCRIPT"
                value={value.target ?? ""}
                onChange={(v) => patch({ target: v })}
                height={160}
                minHeight={140}
                error={!!targetError}
              />
            ) : value.type === "WORKFLOW" ? (
              <Grid container spacing={2}>
                <Grid size={8}>
                  <ConductorFlexibleAutoCompleteVariables
                    label={targetLabel(value.type)}
                    options={workflowNames}
                    value={value.target ?? ""}
                    onChange={(v: any) =>
                      patch({ target: v, version: undefined })
                    }
                  />
                </Grid>
                <Grid size={4}>
                  <ConductorSelect
                    label="Version (optional)"
                    fullWidth
                    items={[
                      { label: "Latest", value: "" },
                      ...(versionsByName.get(value.target ?? "") ?? []).map(
                        (v) => ({ label: `v${v}`, value: String(v) }),
                      ),
                    ]}
                    value={value.version != null ? String(value.version) : ""}
                    onTextInputChange={(v) =>
                      patch({ version: v ? Number(v) : undefined })
                    }
                  />
                </Grid>
              </Grid>
            ) : (
              <ConductorInput
                label={targetLabel(value.type)}
                fullWidth
                placeholder={value.type === "HTTP" ? "https://host/scrub" : undefined}
                value={value.target ?? ""}
                error={!!targetError}
                helperText={targetError ? "Target is required" : undefined}
                onTextInputChange={(v) => patch({ target: v })}
              />
            )}
            {value.type && (
              <Typography
                variant="caption"
                sx={{ display: "block", mt: 0.5, opacity: 0.6 }}
              >
                {contractHint(value.type)}
              </Typography>
            )}
            {value.type === "JAVASCRIPT" && targetError && (
              <Typography
                variant="caption"
                color="error"
                sx={{ display: "block" }}
              >
                Target is required
              </Typography>
            )}
          </Grid>

          {value.type === "HTTP" && (
            <Grid size={12}>
              <Box sx={{ fontWeight: 600, color: "#767676", mb: 1 }}>
                Headers (optional)
              </Box>
              {headerRows.map((row, i) => (
                <Box
                  key={i}
                  sx={{ display: "flex", gap: 1, mb: 1, alignItems: "center" }}
                >
                  <ConductorInput
                    label="Header"
                    placeholder="Authorization"
                    value={row.key}
                    onTextInputChange={(v) =>
                      writeHeaders(
                        headerRows.map((r, j) =>
                          j === i ? { ...r, key: v } : r,
                        ),
                      )
                    }
                  />
                  <ConductorInput
                    label="Value"
                    placeholder="Bearer ..."
                    fullWidth
                    value={row.value}
                    onTextInputChange={(v) =>
                      writeHeaders(
                        headerRows.map((r, j) =>
                          j === i ? { ...r, value: v } : r,
                        ),
                      )
                    }
                  />
                  <IconButton
                    size="small"
                    aria-label="Remove header"
                    onClick={() =>
                      writeHeaders(headerRows.filter((_, j) => j !== i))
                    }
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </Box>
              ))}
              <Button
                size="small"
                onClick={() =>
                  writeHeaders([...headerRows, { key: "", value: "" }])
                }
              >
                + Add header
              </Button>
            </Grid>
          )}
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
  const versionsByName = useWorkflowNamesAndVersions();
  return (
    <Box>
      <Box style={{ opacity: 0.5 }}>
        Guardrails scrub or validate the prompt before it reaches the LLM
        (input) and the response before it returns (output). Each guardrail runs
        as a linked sub-workflow that receives the text as{" "}
        <code>{`{ "prompt": "<text>" }`}</code> and must return{" "}
        <code>{`{ "prompt": "<scrubbed text>" }`}</code>.
      </Box>
      <GuardrailEditor
        title="Input guardrail"
        description="Runs on the prompt (instructions, user input, and messages) before the LLM call."
        paramKey="inputGuardrail"
        taskJson={taskJson}
        onChange={onChange}
        workflowNames={workflowNames}
        versionsByName={versionsByName}
      />
      <GuardrailEditor
        title="Output guardrail"
        description="Runs on the LLM response before it returns to the workflow."
        paramKey="outputGuardrail"
        taskJson={taskJson}
        onChange={onChange}
        workflowNames={workflowNames}
        versionsByName={versionsByName}
      />
    </Box>
  );
};
