import {
  Box,
  FormControlLabel,
  Grid,
  Paper,
  Stack,
  Switch,
  Tab,
  Tabs,
} from "@mui/material";
import { PanelAccordion } from "components/PanelAccordion";
import { ConductorAutoComplete } from "components/v1";
import ConductorInput from "components/v1/ConductorInput";
import { ConductorStringArrayFormField } from "components/v1/ConductorStringArrayFormField";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import _clone from "lodash/clone";
import { pluginRegistry } from "plugins/registry";
import {
  WorkflowMetadataEvents,
  WorkflowMetadataProvider,
} from "pages/definition/WorkflowMetadata/state";
import { FunctionComponent, useCallback, useState } from "react";
import { MetadataBanner } from "shared/createAndDisplayApplication/MetadataBanner";
import { borders, colors } from "theme/tokens/variables";
import { printableUpdatedTime } from "utils";
import { useEventNameSuggestions } from "utils/hooks";
import { useLazyWorkflowNameAutoComplete } from "utils/useLazyWorkflowNameAutoComplete";
import { ActorRef } from "xstate";
import RateLimitConfigForm from "../TaskFormTab/forms/RateLimitConfigForm";
import { SchemaForm } from "../TaskFormTab/forms/SchemaForm";
import TaskFormSection from "../TaskFormTab/forms/TaskFormSection";
import { ActorToHandlerValue } from "./ActorToHandlerValue";
import { useWorkflowMetadata, useWorkflowMetadataEditorActor } from "./state";

// import { FEATURES, featureFlags } from "utils";

// const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
const ownerChipStyle = {
  padding: "2px 10px",
  background: colors.roleReadOnly,
  borderRadius: "100px",
  fontSize: "12px",
  color: colors.sidebarBlacky,
  fontWeight: 500,
};

export interface WorkflowPropertiesFormProps {
  workflowMetadataActor: ActorRef<WorkflowMetadataEvents>;
}
const minWidthTimeout = "170px";
const timeoutPolicies = [
  {
    label: "Timeout Workflow",
    value: "TIME_OUT_WF",
  },
  {
    label: "Alert Only",
    value: "ALERT_ONLY",
  },
];
const getTimeoutPolicyLabel = (option: any) => {
  const item = timeoutPolicies.find((x) => x.value === option);
  return item ? item.label : "";
};

export const WorkflowPropertiesForm: FunctionComponent<
  WorkflowPropertiesFormProps
> = ({ workflowMetadataActor }) => {
  const [
    {
      // DEPRECATED. DONT USE THIS HOOK ANYMORE, USE THE ONE BELOW
      // This was from an old version the spawning of the actors made sense back then given the posistion of the title and other attributes
      // This changed the metadata is a tab now.
      inputParametersActor,
      outputParametersActors,
      restartableActors,
      timeoutSecondsActors,
      timeoutPolicyActors,
      failureWorkflowActors,
      isReady,
      nameFieldActor,
      descriptionFieldActor,
      workflowStatusListenerEnabledActor,
      workflowStatusListenerSinkActor,
      rateLimitConfigActor,
    },
  ] = useWorkflowMetadataEditorActor(workflowMetadataActor);

  const [
    {
      currentWorkflowName,
      ownerEmail,
      wUpdateTime,
      workflowStatusListenerEnabled,
      fastAppCreation,
      installScriptMetadata,
      readmeMetadata,
      inputSchema,
      outputSchema,
      enforceSchema,
    },
    { removeMetadataAttribs, updateSchemaForm },
  ] = useWorkflowMetadata(workflowMetadataActor);

  const updatedTime: string = printableUpdatedTime(wUpdateTime);

  const filterCurrentWorkflowOut = useCallback(
    (x: string) => x !== currentWorkflowName,
    [currentWorkflowName],
  );

  const [fetch, wfNameOptions] = useLazyWorkflowNameAutoComplete(
    filterCurrentWorkflowOut,
  );

  const workflowListenerSinkSuggestions = useEventNameSuggestions();

  const [activeTab, setActiveTab] = useState(0);

  const createAndDisplayAppActor =
    workflowMetadataActor.getSnapshot().children[
      "createAndDisplayApplicationMachine"
    ];
  return (
    <Box id="workflow-properties-form" sx={{ width: "100%", height: "100%" }}>
      <Paper
        square
        sx={{
          width: "100%",
          bgcolor: "customBackground.form",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {isReady && (
          <WorkflowMetadataProvider
            workflowMetadataActor={workflowMetadataActor}
          >
            <Box sx={{ px: 6, width: "100%", paddingTop: 1 }}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid marginTop={2} size={12}>
                  <Stack
                    flexDirection="row"
                    alignItems="center"
                    justifyContent="space-between"
                    columnGap={1}
                  >
                    <Box id="used_to_be_integrations"></Box>

                    <Stack
                      flexDirection="row"
                      alignItems="center"
                      justifyContent="flex-end"
                      columnGap={1}
                    >
                      <Box
                        id="update-time-display"
                        sx={{ fontSize: "11px", opacity: 0.6 }}
                      >{`Last updated ${updatedTime}`}</Box>
                      <Box sx={ownerChipStyle}>{ownerEmail}</Box>
                    </Stack>
                  </Stack>
                </Grid>
                <Grid size={12}>
                  {fastAppCreation &&
                    createAndDisplayAppActor &&
                    (() => {
                      const GeneratedKeyDialog =
                        pluginRegistry.getGeneratedKeyDialog();
                      // Only show MetadataBanner if the GeneratedKeyDialog is available (enterprise)
                      if (!GeneratedKeyDialog) return null;
                      return (
                        <MetadataBanner
                          readme={readmeMetadata}
                          createAndDisplayAppActor={createAndDisplayAppActor}
                          onClose={() => removeMetadataAttribs()}
                          installScript={installScriptMetadata}
                          KeysDisplayerComponent={({ onClose, accessKeys }) => (
                            <GeneratedKeyDialog
                              handleClose={onClose}
                              applicationAccessKey={accessKeys}
                              setIsToastOpen={() => {}}
                            />
                          )}
                        />
                      );
                    })()}
                </Grid>
              </Grid>
            </Box>
            <Stack
              sx={{
                px: 6,
                pb: 6,
                width: "100%",
              }}
            >
              <Box
                sx={{
                  border: "1px solid #ddd",
                  borderRadius: borders.radiusSmall,
                  "& > .MuiAccordion-root:not(:first-of-type)": {
                    borderTop: "1px solid #ddd",
                  },
                  "& > .MuiAccordion-root:first-of-type, & > .MuiAccordion-root:first-of-type:hover":
                    {
                      borderTopLeftRadius: borders.radiusSmall,
                      borderTopRightRadius: borders.radiusSmall,
                    },
                  "& > .MuiAccordion-root:first-of-type .MuiAccordionSummary-root, & > .MuiAccordion-root:first-of-type:hover .MuiAccordionSummary-root":
                    {
                      borderTopLeftRadius: borders.radiusSmall,
                      borderTopRightRadius: borders.radiusSmall,
                    },
                  "& > .MuiAccordion-root:last-of-type, & > .MuiAccordion-root:last-of-type:hover":
                    {
                      borderBottomLeftRadius: borders.radiusSmall,
                      borderBottomRightRadius: borders.radiusSmall,
                    },
                  "& > .MuiAccordion-root:last-of-type .MuiAccordionSummary-root, & > .MuiAccordion-root:last-of-type:hover .MuiAccordionSummary-root":
                    {
                      borderBottomLeftRadius: borders.radiusSmall,
                      borderBottomRightRadius: borders.radiusSmall,
                    },
                }}
              >
                <PanelAccordion
                  id="workflow-details-panel-accordion"
                  title="Workflow Details"
                  defaultExpanded
                >
                  <TaskFormSection title="Name and Description">
                    <Grid container spacing={3} sx={{ width: "100%" }}>
                      <Grid size={12}>
                        <ActorToHandlerValue actor={nameFieldActor}>
                          {({ onChange, value: name }) => (
                            <ConductorInput
                              required
                              label="Name"
                              value={name ? name : ""}
                              onTextInputChange={(value) => onChange(value)}
                              helperText="Workflow name must be unique."
                              fullWidth
                              id="workflow-name-field"
                            />
                          )}
                        </ActorToHandlerValue>
                      </Grid>
                      <Grid size={12}>
                        <ActorToHandlerValue actor={descriptionFieldActor}>
                          {({ onChange, value: description }) => (
                            <ConductorInput
                              id="workflow-description-field"
                              label="Description"
                              value={description ? description : ""}
                              onTextInputChange={(value) => onChange(value)}
                              fullWidth
                              required
                              multiline={true}
                              rows={3}
                              error={!description}
                              autoFocus
                              placeholder="Enter description"
                            />
                          )}
                        </ActorToHandlerValue>
                      </Grid>
                    </Grid>
                  </TaskFormSection>
                </PanelAccordion>
                <PanelAccordion
                  id="schema-params-panel-accordion"
                  title="Schema and Parameters"
                  defaultExpanded
                >
                  <Box>
                    <Tabs
                      value={activeTab}
                      onChange={(_, newValue) => setActiveTab(newValue)}
                      aria-label="schema and parameters tabs"
                    >
                      <Tab label="Input and Output Parameters" />
                      <Tab label="Workflow Schema" />
                    </Tabs>
                  </Box>
                  {activeTab === 0 && (
                    <>
                      <TaskFormSection
                        title="Input parameters"
                        // accordionAdditionalProps={{ defaultExpanded: true }}
                      >
                        <ActorToHandlerValue actor={inputParametersActor}>
                          {({ onChange, value: inputParameters, someKey }) => (
                            <ConductorStringArrayFormField
                              inputParameters={_clone(inputParameters)}
                              onChange={onChange}
                              someKey={someKey}
                              label="Key"
                              addButtonLabel="Add parameter"
                              emptyListMessage="These values serve as an indicator of what inputs this workflow expects."
                              compact
                            />
                          )}
                        </ActorToHandlerValue>
                      </TaskFormSection>
                      <TaskFormSection
                        title="Output parameters"
                        // accordionAdditionalProps={{ defaultExpanded: true }}
                      >
                        <ActorToHandlerValue actor={outputParametersActors}>
                          {({ onChange, value: outputParameters, someKey }) => (
                            <ConductorFlatMapFormBase
                              value={
                                _clone(outputParameters) as Record<
                                  string,
                                  string
                                >
                              } //Patch this aint A FIX you can put json as an output param
                              keyColumnLabel="Parameter"
                              valueColumnLabel="Value"
                              addItemLabel="Add parameter"
                              onChange={onChange}
                              someKey={someKey}
                              emptyListMessage="These values serve as an indicator of what outputs this workflow will produce."
                              compact
                            />
                          )}
                        </ActorToHandlerValue>
                      </TaskFormSection>
                    </>
                  )}
                  {activeTab === 1 && (
                    <SchemaForm
                      value={{
                        inputSchema,
                        outputSchema,
                        enforceSchema,
                      }}
                      onChange={(value) => {
                        updateSchemaForm(
                          value?.inputSchema,
                          value?.outputSchema,
                          value?.enforceSchema,
                        );
                      }}
                    />
                  )}
                </PanelAccordion>

                <PanelAccordion
                  id="workflow-execution-parameters-panel-accordion"
                  title="Execution Parameters"
                  defaultExpanded
                >
                  <TaskFormSection>
                    <Grid container spacing={2} pt={3}>
                      <Grid size={12}>
                        <ActorToHandlerValue
                          actor={workflowStatusListenerEnabledActor}
                        >
                          {({
                            onChange,
                            value: workflowStatusListenerEnabledValue,
                          }) => (
                            <FormControlLabel
                              checked={workflowStatusListenerEnabledValue}
                              control={
                                <Switch
                                  color="primary"
                                  style={{ marginRight: 8 }}
                                  onChange={({ target: { checked } }) =>
                                    onChange(checked)
                                  }
                                />
                              }
                              label="Enable workflow status listener"
                            />
                          )}
                        </ActorToHandlerValue>
                      </Grid>
                      {workflowStatusListenerEnabled && (
                        <Grid size={12}>
                          <ActorToHandlerValue
                            actor={workflowStatusListenerSinkActor}
                          >
                            {({ onChange, value: workflowListenerSink }) => (
                              <ConductorAutocompleteVariables
                                onChange={onChange}
                                value={workflowListenerSink}
                                otherOptions={workflowListenerSinkSuggestions}
                                label="Workflow listener sink"
                              />
                            )}
                          </ActorToHandlerValue>
                          <Box pt={2} style={{ opacity: 0.5 }}>
                            {/* description */}
                          </Box>
                        </Grid>
                      )}
                    </Grid>
                  </TaskFormSection>

                  <TaskFormSection
                    title="Timeout Settings"
                    // accordionAdditionalProps={{ defaultExpanded: true }}
                  >
                    <Grid
                      container
                      sx={{ width: "100%" }}
                      spacing={3}
                      justifyContent="flex-start"
                    >
                      <Grid sx={{ minWidth: minWidthTimeout }}>
                        <ActorToHandlerValue actor={timeoutSecondsActors}>
                          {({ onChange, value: timeoutSeconds }) => (
                            <ConductorInput
                              label="Timeout seconds"
                              type="number"
                              value={timeoutSeconds > -1 ? timeoutSeconds : ""}
                              onTextInputChange={(timeout) =>
                                onChange(parseInt(timeout))
                              }
                            />
                          )}
                        </ActorToHandlerValue>
                      </Grid>
                      <Grid flexGrow={1}>
                        <ActorToHandlerValue actor={timeoutPolicyActors}>
                          {({ onChange, value: timeoutPolicy }) => (
                            <ConductorAutoComplete
                              fullWidth
                              value={timeoutPolicy}
                              onChange={(_evt, val) => {
                                onChange(val);
                              }}
                              getOptionLabel={(option) =>
                                getTimeoutPolicyLabel(option)
                              }
                              renderOption={(props, option) => (
                                <li {...props}>
                                  {getTimeoutPolicyLabel(option)}
                                </li>
                              )}
                              options={timeoutPolicies.map((x) => x.value)}
                              autoComplete
                              includeInputInList
                              label="Timeout policy"
                            />
                          )}
                        </ActorToHandlerValue>
                      </Grid>
                    </Grid>
                  </TaskFormSection>
                  <TaskFormSection
                    title="Restartable"
                    // accordionAdditionalProps={{ defaultExpanded: true }}
                  >
                    <Grid flexGrow={1}>
                      <Box>
                        <ActorToHandlerValue actor={restartableActors}>
                          {({ onChange, value: restartable }) => (
                            <FormControlLabel
                              checked={restartable}
                              control={
                                <Switch
                                  color="primary"
                                  onChange={({ target: { checked } }) =>
                                    onChange(checked)
                                  }
                                />
                              }
                              label="Allow workflow restarts"
                            />
                          )}
                        </ActorToHandlerValue>
                        <Box pt={2} style={{ opacity: 0.5 }} mb={3}>
                          When enabled, completed workflows can be restarted.
                          Disable this option if restarting a workflow could
                          cause side effects.
                        </Box>
                      </Box>
                    </Grid>
                  </TaskFormSection>
                  <TaskFormSection
                    title="Failure/Compensation"
                    // accordionAdditionalProps={{ defaultExpanded: true }}
                  >
                    <Grid size={12}>
                      <Box>
                        <ActorToHandlerValue actor={failureWorkflowActors}>
                          {({ onChange, value: failureWorkflow }) => (
                            <ConductorAutocompleteVariables
                              onChange={onChange}
                              value={failureWorkflow}
                              otherOptions={wfNameOptions}
                              label="Failure/Compensation workflow name"
                              onFocus={fetch}
                            />
                          )}
                        </ActorToHandlerValue>
                        <Box pt={2} style={{ opacity: 0.5 }}>
                          If present, this workflow will be triggered upon a
                          failure of the execution of this workflow.
                        </Box>
                      </Box>
                    </Grid>
                  </TaskFormSection>

                  <TaskFormSection title="">
                    <Grid size={12}>
                      <ActorToHandlerValue actor={rateLimitConfigActor}>
                        {({ onChange, value: rateLimitConfig }) => (
                          <RateLimitConfigForm
                            onChange={onChange}
                            value={rateLimitConfig}
                          />
                        )}
                      </ActorToHandlerValue>
                    </Grid>
                  </TaskFormSection>
                </PanelAccordion>
              </Box>
            </Stack>
          </WorkflowMetadataProvider>
        )}
      </Paper>
    </Box>
  );
};
