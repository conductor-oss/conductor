import {
  Box,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Switch,
  Theme,
  Tooltip,
  createFilterOptions,
} from "@mui/material";
import { Plus } from "@phosphor-icons/react";
import { ChangeEvent, Fragment } from "react";
import { ActorRef } from "xstate";

import { Button } from "components";
import { ConductorAutoComplete } from "components/v1";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import ConductorInput from "components/v1/ConductorInput";
import ConductorSelect from "components/v1/ConductorSelect";
import { colors } from "theme/tokens/variables";
import { useEventNameSuggestions } from "utils/hooks/useEventNameSuggestions";
import { CompleteTask } from "./ActionForms/CompleteTask";
import { FailTask } from "./ActionForms/FailTask";
import { StartWorkflowActionForm } from "./ActionForms/StartWorkflowTask";
import { TerminateWorkflowForm } from "./ActionForms/TerminateWorkflowTask";
import { UpdateWorkflowForm } from "./ActionForms/UpdateWorkflowTask";
import { useEventHandlerFormActor } from "./state/hook";
import { Action, FormHandlerEvents, actionLabel } from "./state/types";

const containerStyle = {
  maxWidth: 818,
  color: (theme: Theme) =>
    theme.palette?.mode === "dark" ? colors.gray14 : undefined,
  backgroundColor: (theme: Theme) => theme.palette.customBackground.form,
};

const filter = createFilterOptions<string>();

const EventHandlerForm = ({
  actor,
}: {
  actor: ActorRef<FormHandlerEvents>;
}) => {
  const [
    { action, name, condition, actions, event, active, description },
    {
      handleChangeAction,
      handleChange,
      handleAction,
      removeAction,
      handleEventChange,
    },
  ] = useEventHandlerFormActor(actor);

  const suggestions = useEventNameSuggestions();

  return (
    <>
      <Grid container sx={{ width: "100%" }} spacing={2} mt={0}>
        <Grid
          mt={0}
          size={{
            xs: 12,
            lg: 12,
          }}
        >
          <Box sx={{ ...containerStyle }}>
            <Grid
              id="event-handler-form-wrapper"
              container
              spacing={2}
              pl={5}
              pt={3}
              pr={8}
              pb={5}
              sx={{ width: "100%" }}
            >
              <Grid size={12} sx={{ mt: 2 }}>
                <Grid container sx={{ width: "100%" }} spacing={4}>
                  <Grid size={12}>
                    <ConductorInput
                      label="Name"
                      fullWidth
                      required
                      placeholder="Event Handler Name"
                      id="event-name-input"
                      name="name"
                      value={name}
                      onTextInputChange={(val) => handleChange("name", val)}
                    />
                  </Grid>
                  <Grid size={12}>
                    <ConductorInput
                      id="event-description-field"
                      label="Description"
                      name="description"
                      multiline
                      minRows={3}
                      fullWidth
                      onTextInputChange={(value) =>
                        handleChange("description", value)
                      }
                      value={description}
                      placeholder="Enter description"
                    />
                  </Grid>
                  <Grid size={12}>
                    <ConductorAutoComplete
                      label="Event"
                      fullWidth
                      required
                      placeholder="Event String"
                      id="event-string-input"
                      options={suggestions}
                      value={event}
                      onChange={(_, val: any) => handleEventChange(val)}
                      onInputChange={(_, val) => handleEventChange(val)}
                      freeSolo
                      selectOnFocus
                      filterOptions={(options, params) => {
                        const filtered = filter(options, params);

                        const { inputValue } = params;
                        // Suggest the creation of a new value
                        const isExisting = options.some(
                          (option) => inputValue === option,
                        );

                        if (inputValue !== "" && !isExisting) {
                          filtered.push(`${inputValue}`);
                        }

                        return filtered;
                      }}
                    />
                  </Grid>
                  <Grid size={12}>
                    <ConductorCodeBlockInput
                      label="Condition (Trigger if evaluated to true)"
                      language="javascript"
                      value={condition}
                      onChange={(val) => handleChange("condition", val)}
                    />
                  </Grid>

                  <Grid size={12}>
                    <ConductorSelect
                      fullWidth
                      sx={{ width: "100%" }}
                      label="Action"
                      name="action"
                      value={action || ""}
                      onChange={(e: ChangeEvent<HTMLInputElement>) =>
                        handleChange("action", e.target.value)
                      }
                    >
                      {Object.values(Action).map((val) => (
                        <MenuItem key={val} value={val}>
                          {actionLabel[val]}
                        </MenuItem>
                      ))}
                    </ConductorSelect>
                  </Grid>

                  <Grid size={12}>
                    <Button
                      onClick={() => handleAction(action)}
                      disabled={!action}
                      startIcon={<Plus size={12} />}
                      size="small"
                    >
                      Add action
                    </Button>
                  </Grid>

                  <Grid size={12}>
                    <Divider sx={{ marginTop: 6 }} />
                  </Grid>

                  <Grid container sx={{ width: "100%" }} size={12}>
                    {actions?.map((action: any, index: number) => {
                      const renderFormComponent = (
                        Component: any,
                        keyPrefix: string,
                      ) => {
                        const key = `${keyPrefix}-${index}`;
                        return (
                          <Fragment key={key}>
                            <Component
                              onRemove={() => removeAction(index)}
                              handleChangeAction={handleChangeAction}
                              payload={action}
                              index={index}
                            />
                            {index !== actions.length - 1 && (
                              <Grid size={12}>
                                <Divider sx={{ marginTop: 6 }} />
                              </Grid>
                            )}
                          </Fragment>
                        );
                      };

                      switch (action.action) {
                        case Action.FAIL_TASK:
                          return renderFormComponent(FailTask, `fail_task`);
                        case Action.START_WORKFLOW:
                          return renderFormComponent(
                            StartWorkflowActionForm,
                            `startWorkflow`,
                          );
                        case Action.TERMINATE_WORKFLOW:
                          return renderFormComponent(
                            TerminateWorkflowForm,
                            `terminate`,
                          );
                        case Action.UPDATE_WORKFLOW_VARIABLES:
                          return renderFormComponent(
                            UpdateWorkflowForm,
                            `updateWf`,
                          );
                        case Action.COMPLETE_TASK:
                          return renderFormComponent(CompleteTask, `complete`);
                        default:
                          return null;
                      }
                    })}
                  </Grid>
                  <Grid size={12}>
                    <Tooltip title="Activate this event" arrow>
                      <FormControlLabel
                        control={
                          <Switch
                            color="primary"
                            checked={active}
                            name="activateEvent"
                            onChange={(val) =>
                              handleChange("active", val.target.checked)
                            }
                          />
                        }
                        label="Active"
                        sx={{ mb: 3 }}
                      />
                    </Tooltip>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </Box>
        </Grid>
      </Grid>
    </>
  );
};

export default EventHandlerForm;
