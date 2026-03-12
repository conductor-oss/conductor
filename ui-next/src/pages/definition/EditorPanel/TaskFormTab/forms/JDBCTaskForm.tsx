import { Box, Grid } from "@mui/material";
import RadioButtonGroup from "components/RadioButtonGroup";
import { ConductorArrayField } from "components/v1/ConductorArrayField";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { assoc as _assoc, path as _path } from "lodash/fp";
import { ChangeEvent } from "react";
import { UseQueryResult } from "react-query";
import { IntegrationCategory, IntegrationDef, JDBCType, TaskType } from "types";
import { updateField } from "utils/fieldHelpers";
import { useIntegrationProviders } from "utils/useIntegrationProviders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";

import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const connectionIdPath = "inputParameters.connectionId";
const integrationNamePath = "inputParameters.integrationName";
const expectedUpdateCountPath = "inputParameters.expectedUpdateCount";
const jdbcTypePath = "inputParameters.type";
const queryParametersPath = "inputParameters.parameters";
const statementPath = "inputParameters.statement";

export const JDBCTaskForm = ({ task, onChange }: TaskFormProps) => {
  const { data: integrationDBNames }: UseQueryResult<IntegrationDef[]> =
    useIntegrationProviders({
      category: IntegrationCategory.RELATIONAL_DB,
      activeOnly: false,
    });

  const queryParameters = _path(queryParametersPath, task);

  const changeQueryParameters = (value: string[]) => {
    onChange(updateField(queryParametersPath, value, task));
  };

  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Connection"
      >
        <Grid container sx={{ width: "100%" }} spacing={2}>
          {!!task.inputParameters?.connectionId && (
            <Grid
              alignSelf={"center"}
              size={{
                xs: 12,
                sm: 12,
                md: 6,
              }}
            >
              <ConductorAutocompleteVariables
                onChange={(changes) =>
                  onChange(updateField(connectionIdPath, changes, task))
                }
                value={_path(connectionIdPath, task)}
                label="Connection id (Deprecated)"
                disabled
              />
            </Grid>
          )}
          <Grid
            size={{
              xs: 12,
              sm: 12,
              md: 6,
            }}
          >
            <ConductorAutocompleteVariables
              otherOptions={integrationDBNames?.map((item) => item.name) || []}
              onChange={(changes) =>
                onChange(updateField(integrationNamePath, changes, task))
              }
              value={_path(integrationNamePath, task)}
              label="Integration name"
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Statement"
      >
        <Grid container spacing={2}>
          <Grid container sx={{ width: "100%" }} size={12}>
            <Grid
              size={{
                xs: 12,
                sm: 12,
                md: 8,
              }}
            >
              <RadioButtonGroup
                value={task?.inputParameters?.type}
                onChange={(val: ChangeEvent<HTMLInputElement>) =>
                  onChange(_assoc(jdbcTypePath, val.target.value, task))
                }
                items={[
                  {
                    value: JDBCType.SELECT,
                    label: "SELECT",
                  },
                  {
                    value: JDBCType.UPDATE,
                    label: "INSERT/UPDATE/DELETE",
                  },
                ]}
                name="jdbcType"
              />
            </Grid>
            {_path(jdbcTypePath, task) === JDBCType.UPDATE && (
              <Grid alignSelf={"end"} marginLeft={"auto"} width={"160px"}>
                <ConductorAutocompleteVariables
                  onChange={(changes) =>
                    onChange(
                      updateField(expectedUpdateCountPath, changes, task),
                    )
                  }
                  value={_path(expectedUpdateCountPath, task)}
                  label="Expected update count"
                  inputProps={{
                    tooltip: {
                      title: "Expected update count",
                      content:
                        "If you have chosen ‘UPDATE’ as the statement type, provide the number of rows you need to update in the database.",
                    },
                  }}
                />
              </Grid>
            )}
          </Grid>
        </Grid>
        <Grid container spacing={2} mt={4} sx={{ width: "100%" }}>
          <Grid size={12}>
            <ConductorCodeBlockInput
              label="Statement"
              language="sql"
              minHeight={300}
              autoformat={false}
              value={_path(statementPath, task)}
              onChange={(changes) =>
                onChange(updateField(statementPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Query parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorArrayField
              value={queryParameters}
              onChange={changeQueryParameters}
              showType
              taskType={TaskType.JDBC}
              path={queryParametersPath}
              enableAutocomplete
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
