import {
  materialCells,
  materialRenderers,
} from "@jsonforms/material-renderers";
import { JsonForms } from "@jsonforms/react";
import {
  Box,
  CircularProgress,
  Grid,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import { GearIcon } from "@phosphor-icons/react";
import { ConductorAutoComplete } from "components/ui/inputs";
import { IntegrationIcon } from "components/IntegrationIcon";
import { useNavigate } from "react-router";
import { colors } from "theme/tokens/variables";
import { TaskType } from "types";
import { updateField } from "utils/fieldHelpers";
import {
  useMCPIntegrations,
  useMCPTools,
} from "utils/hooks/useMCPIntegrations";
import { downgradeSchemaToDraft7 } from "utils/json";
import { useFetch } from "utils/query";
import { MaybeVariable } from "./MaybeVariable";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useMemo } from "react";

export const MCPTaskForm = ({ task, onChange }: TaskFormProps) => {
  const navigate = useNavigate();
  const customTheme = createTheme({
    components: {
      MuiTextField: {
        styleOverrides: {
          root: {
            backgroundColor: "#FFFFFF",
            fontSize: "14px",
            fontWeight: 200,
            minHeight: "unset",
            marginBottom: "16px",

            // Remove autofill background's input
            "& input:-webkit-autofill": {
              WebkitBoxShadow: "0 0 0 100px #ffffff inset",
            },

            "& ::placeholder": {
              color: "#AFAFAF",
            },
          },
        },
      },
      MuiFormControl: {
        styleOverrides: {
          root: {
            marginBottom: "16px",
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            fontSize: "14px",
            fontWeight: 200,
            pointerEvents: "auto",
            transform: "translate(12px, -9px) scale(0.857)",
            color: "#494949",

            "&.Mui-focused": {
              fontWeight: 500,
              color: "#1976D2",
            },

            "&.Mui-error": {
              color: "#D6423B",
            },

            "&.Mui-disabled": {
              color: "#858585",
            },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            backgroundColor: "#FFFFFF",
            fontSize: "14px",
            fontWeight: 200,
            color: "#060606",
            minHeight: "unset",

            ".MuiInputBase-input": {
              padding: "14px 8px 8px 8px",
              "&.Mui-disabled": {
                WebkitTextFillColor: "#494949",
              },
            },

            ".MuiOutlinedInput-notchedOutline": {
              borderWidth: 1,
              borderStyle: "solid",
              borderRadius: "4px",
              borderColor: "#AFAFAF",

              // This will make the legend has same size with the label
              "& legend": {
                maxWidth: "100%",
                fontSize: "0.857em",
                fontWeight: 200,
              },
            },

            "&:hover .MuiOutlinedInput-notchedOutline": {
              borderColor: "#1876D1",
              borderWidth: 1,
            },

            "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
              borderWidth: 1,
              borderColor: "#1876D1",

              "& legend": {
                fontWeight: 500,
              },
            },

            "&.Mui-focused": {
              backgroundColor: "#FFFFFF",
            },

            "&.Mui-error": {
              color: "#D6423B",

              ".MuiOutlinedInput-notchedOutline": {
                borderColor: "#D6423B",
              },
            },

            "&.Mui-disabled": {
              WebkitTextFillColor: "#494949",
              borderColor: "#AFAFAF",
              backgroundColor: "#ECECEC",
            },

            ".MuiInputBase-inputMultiline": {
              p: 0,
            },

            "&.MuiInputBase-multiline": {
              p: "14px 8px 8px 8px",
            },
          },
        },
      },
      MuiFormHelperText: {
        styleOverrides: {
          root: {
            fontSize: "0.857em",
            color: "#494949",
            paddingLeft: "8px",
            marginTop: "4px",
            marginLeft: "0px",

            "&.Mui-error": {
              color: "#D6423B",
            },

            "&.Mui-disabled": {
              color: "#060606",
            },
          },
        },
      },
      MuiIconButton: {
        styleOverrides: {
          root: {
            // Clear button visibility control
            "&[aria-label='clear value']": {
              visibility: "visible",
            },
          },
        },
      },
    },
  });

  const { integrations, isLoading: isLoadingIntegrations } =
    useMCPIntegrations();
  const { tools, isLoading: isLoadingTools } = useMCPTools(
    task?.inputParameters?.integrationName,
  );

  const hasValidIntegration = Boolean(
    task?.inputParameters?.integrationName &&
    task?.inputParameters?.integrationName !== null,
  );
  const hasValidMethod = Boolean(
    task?.inputParameters?.method && task?.inputParameters?.method !== null,
  );

  const { data: toolData, isLoading: isToolDataLoading } = useFetch(
    `/integrations/${task?.inputParameters?.integrationName}/def/api/${task?.inputParameters?.method}`,
    {
      enabled: hasValidIntegration && hasValidMethod,
    },
  );

  // Prepare and validate the schema for JsonForms
  const processedSchema = useMemo(() => {
    if (!toolData?.inputSchema?.data) return null;

    const schemaData = toolData.inputSchema.data;
    if (
      typeof schemaData !== "object" ||
      Object.keys(schemaData).length === 0
    ) {
      return null;
    }

    // Check if schema has properties (actual fields to render)
    if (
      !schemaData.properties ||
      Object.keys(schemaData.properties).length === 0
    ) {
      return null;
    }

    try {
      return downgradeSchemaToDraft7(schemaData);
    } catch (error) {
      console.error("[MCPTaskForm] Error processing schema:", error);
      return null;
    }
  }, [toolData?.inputSchema?.data]);

  return (
    <Box width="100%">
      <MaybeVariable
        value={task?.inputParameters}
        onChange={(val) => onChange(updateField("inputParameters", val, task))}
        path={"inputParameters"}
        taskType={TaskType.MCP}
      >
        <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
          {isToolDataLoading ? (
            <Grid
              size={{
                xs: 12,
                md: 12,
                sm: 12,
              }}
            >
              <Box
                sx={{
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  textAlign: "center",
                  height: "300px",
                  width: "100%",
                }}
              >
                <CircularProgress size={30} />
              </Box>
            </Grid>
          ) : (
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                sx={{
                  "&.MuiGrid-item": {
                    paddingTop: "0px",
                  },
                }}
                size={{
                  xs: 12,
                  md: 12,
                  sm: 12,
                }}
              >
                <Box
                  sx={{ display: "flex", alignItems: "center", gap: "10px" }}
                >
                  <Box
                    sx={{
                      width: "35px",
                      height: "35px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <IntegrationIcon
                      integrationName={task?.inputParameters?.integrationType}
                    />
                  </Box>
                  <Typography sx={{ fontSize: "16px", fontWeight: 500 }}>
                    {task?.inputParameters?.method}
                  </Typography>
                  <Box sx={{ display: "flex", gap: 2, ml: "auto" }}>
                    <Box
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: "4px",
                        color: colors.blueLightMode,
                        cursor: "pointer",
                        fontSize: "12px",
                        fontWeight: 500,
                      }}
                      onClick={() => {
                        if (task?.inputParameters?.integrationName) {
                          navigate(
                            `/integrations/${encodeURIComponent(
                              task.inputParameters.integrationName,
                            )}/configuration`,
                          );
                        }
                      }}
                    >
                      Configuration{" "}
                      <GearIcon
                        size={16}
                        weight="bold"
                        color={colors.blueLightMode}
                      />
                    </Box>
                  </Box>
                </Box>
                {/* <Typography
                  sx={{
                    pl: "10px",
                    fontSize: "12px",
                    color: "#494949",
                    whiteSpace: "pre-line",
                  }}
                >
                  {toolData?.description}
                </Typography> */}
              </Grid>
              <Grid size={12} mt={1}>
                <Grid container spacing={3}>
                  <Grid size={6}>
                    <ConductorAutoComplete
                      id="integration-name-dropdown"
                      fullWidth
                      label="Integration"
                      options={(integrations || [])
                        .filter((i) => i.status === "active")
                        .map((i) => i.name)}
                      onChange={(__, val) => {
                        // Get the selected integration's type
                        const selectedIntegration = (integrations || []).find(
                          (i) => i.name === val,
                        );

                        // Only keep integration name and type in inputParameters
                        onChange({
                          ...task,
                          inputParameters: {
                            integrationName: val,
                            integrationType: selectedIntegration?.type || null,
                          },
                        });
                      }}
                      value={task?.inputParameters?.integrationName}
                      autoFocus
                      required
                      disableClearable
                      getOptionLabel={(option) => option}
                      loading={isLoadingIntegrations}
                    />
                  </Grid>
                  <Grid size={6}>
                    <ConductorAutoComplete
                      id="integration-tool-dropdown"
                      fullWidth
                      label="Tool"
                      options={tools || []}
                      onChange={(__, val) => {
                        onChange({
                          ...task,
                          inputParameters: {
                            integrationName:
                              task?.inputParameters?.integrationName,
                            integrationType:
                              task?.inputParameters?.integrationType,
                            method: val?.api,
                          },
                        });
                      }}
                      value={
                        tools?.find(
                          (t: { api: string }) =>
                            t.api === task?.inputParameters?.method,
                        ) || null
                      }
                      autoFocus
                      required
                      disableClearable
                      getOptionLabel={(option) => option?.api || ""}
                      loading={isLoadingTools}
                      disabled={!task?.inputParameters?.integrationName}
                    />
                  </Grid>
                </Grid>
              </Grid>

              <Grid mt={1} size={12}>
                <Grid container sx={{ width: "100%" }} spacing={3}>
                  {processedSchema && (
                    <Grid
                      size={{
                        xs: 12,
                        md: 12,
                        sm: 12,
                      }}
                    >
                      <ThemeProvider theme={customTheme}>
                        <JsonForms
                          schema={processedSchema}
                          data={task?.inputParameters}
                          onChange={({ data }) =>
                            onChange(updateField("inputParameters", data, task))
                          }
                          renderers={materialRenderers}
                          cells={materialCells}
                        />
                      </ThemeProvider>
                    </Grid>
                  )}
                </Grid>
              </Grid>
            </Grid>
          )}
        </TaskFormSection>
      </MaybeVariable>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
