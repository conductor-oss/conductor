import { Box, Grid, Typography } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

/** Config form for CALL_MCP_TOOL — invokes a tool on a Model Context Protocol server. */
export const CallMcpToolTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const headers: Record<string, string> =
    (get("inputParameters.headers") as Record<string, string>) || {};

  // Arguments can be a ${...} variable reference (string) or an inline JSON object.
  const rawArguments = get("inputParameters.arguments");
  const argumentsIsVariable =
    typeof rawArguments === "string" &&
    rawArguments.trim().startsWith("${") &&
    rawArguments.trim().endsWith("}");

  const argumentsJson =
    rawArguments && !argumentsIsVariable
      ? typeof rawArguments === "string"
        ? rawArguments
        : JSON.stringify(rawArguments, null, 2)
      : "";

  return (
    <Box padding={1} width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="MCP Server"
      >
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={{ xs: 12, md: 8 }}>
            <ConductorAutocompleteVariables
              label="MCP server URL"
              value={get("inputParameters.mcpServer")}
              onChange={(v) => set("inputParameters.mcpServer", v)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <ConductorAutocompleteVariables
              label="Tool name (method)"
              value={get("inputParameters.method")}
              onChange={(v) => set("inputParameters.method", v)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Tool arguments">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={12}>
            <Typography variant="body2" color="text.secondary" mb={1}>
              Pass a <code>{"${variable}"}</code> reference to use a dynamic
              object, or enter a JSON object directly.
            </Typography>
          </Grid>
          {/* Variable reference input — allows e.g. ${llmChat.output.params} */}
          <Grid size={12}>
            <ConductorAutocompleteVariables
              label="Arguments (variable reference)"
              value={argumentsIsVariable ? rawArguments : ""}
              onChange={(v) => {
                if (v && v.trim().startsWith("${")) {
                  set("inputParameters.arguments", v);
                } else if (!v) {
                  set("inputParameters.arguments", undefined);
                }
              }}
              placeholder="${workflow.input.toolArgs}"
            />
          </Grid>
          {/* JSON editor — used when not a variable reference */}
          {!argumentsIsVariable && (
            <Grid size={12}>
              <ConductorCodeBlockInput
                label="Arguments (JSON)"
                language="json"
                minHeight={200}
                autoformat
                value={argumentsJson}
                onChange={(v) => {
                  set("inputParameters.arguments", v || undefined);
                }}
              />
            </Grid>
          )}
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Headers">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={12}>
            <ConductorAdditionalHeadersBase
              headers={headers}
              onChangeHeaders={(h) => set("inputParameters.headers", h)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
