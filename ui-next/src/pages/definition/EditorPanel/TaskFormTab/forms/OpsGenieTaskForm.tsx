import { Box, Grid } from "@mui/material";
import { path as _path } from "lodash/fp";

import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import {
  ConductorFlatMapForm,
  ConductorFlatMapFormBase,
} from "components/FlatMapForm/ConductorFlatMapForm";
import { TaskType } from "types";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { ConductorValueInput } from "./ConductorValueInput";
import { updateField } from "utils/fieldHelpers";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const DEFAULT_VALUES_FOR_ARRAY = { object: [] };

const aliasLocationPath = "inputParameters.alias";
const descriptionPath = "inputParameters.description";
const messagePath = "inputParameters.message";
const priorityPath = "inputParameters.priority";
const entityPath = "inputParameters.entity";
const tokenPath = "inputParameters.token";
const actionsPath = "inputParameters.actions";
const tagsPath = "inputParameters.tags";
const inputParametersPath = "inputParameters";
const detailsPath = "inputParameters.details";

export const OpsGenieTaskForm = ({ task, onChange }: TaskFormProps) => {
  const alias = _path(aliasLocationPath, task);
  const description = _path(descriptionPath, task);
  const message = _path(messagePath, task);
  const priority = _path(priorityPath, task);
  const entity = _path(entityPath, task);
  const token = _path(tokenPath, task);
  const actions = _path(actionsPath, task);
  const tags = _path(tagsPath, task);

  return (
    <Box>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={alias}
              label="Alias"
              onChange={(changes) =>
                onChange(updateField(aliasLocationPath, changes, task))
              }
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={description}
              label="Description"
              onChange={(changes) =>
                onChange(updateField(descriptionPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes={true}
              autoFocusField={false}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              hideButtons
              hiddenKeys={[
                "description",
                "alias",
                "message",
                "details",
                "actions",
                "priority",
                "entity",
                "tags",
                "token",
              ]}
              value={task?.inputParameters}
              onChange={(changes) =>
                onChange(updateField(inputParametersPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapForm
              title="Details"
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add details"
              taskType={TaskType.OPS_GENIE}
              path={detailsPath}
              value={task?.inputParameters?.details}
              onChange={(changes) =>
                onChange(updateField(detailsPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={message}
              label="Message"
              onChange={(changes) =>
                onChange(updateField(messagePath, changes, task))
              }
            />
          </Grid>
          <Grid size={12}>
            <ConductorValueInput
              valueLabel="Actions"
              value={actions}
              onChangeValue={(changes) => {
                onChange(updateField(actionsPath, changes, task));
              }}
              defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
            />
          </Grid>

          <Grid size={6}>
            <ConductorAutocompleteVariables
              value={priority}
              label="Priority"
              onChange={(changes) =>
                onChange(updateField(priorityPath, changes, task))
              }
            />
          </Grid>
          <Grid size={6}>
            <ConductorAutocompleteVariables
              value={entity}
              label="Entity"
              onChange={(changes) =>
                onChange(updateField(entityPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={token}
              label="Token"
              onChange={(changes) =>
                onChange(updateField(tokenPath, changes, task))
              }
            />
          </Grid>
          <Grid size={12}>
            <ConductorValueInput
              valueLabel="Tags"
              value={tags}
              onChangeValue={(changes) => {
                onChange(updateField(tagsPath, changes, task));
              }}
              defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection>
        <ConductorCacheOutput onChange={onChange} taskJson={task} />
      </TaskFormSection>
    </Box>
  );
};
