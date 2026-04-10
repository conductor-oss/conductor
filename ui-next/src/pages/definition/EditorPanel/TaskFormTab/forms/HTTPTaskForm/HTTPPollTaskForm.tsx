import { Box, FormControlLabel, Grid } from "@mui/material";
import { useInterpret } from "@xstate/react";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { assoc as _assoc, path as _path, pipe as _pipe } from "lodash/fp";
import { mock } from "mock-json-schema";
import { ServiceType } from "types/RemoteServiceTypes";
import { useMemo, useState } from "react";
import { colors } from "theme/tokens/variables";
import { HTTPMethods, PollingStrategy, TaskType } from "types";
import {
  CONTENT_TYPE_SUGGESTIONS,
  HEADERS_PATH,
  HTTP_REQUEST_PATH,
} from "utils/constants/httpSuggestions";
import { updateField } from "utils/fieldHelpers";
import { featureFlags, FEATURES } from "utils/flags";
import { useAuthHeaders } from "utils/query";
import { getBaseUrl } from "utils/utils";
import { ConductorCacheOutput } from "../ConductorCacheOutputForm";
import { MaybeVariable } from "../MaybeVariable";
import { Optional } from "../OptionalFieldForm";
import ServiceRegistryPopulator from "../ServiceRegistrySelector";
import TaskFormSection from "../TaskFormSection";
import { ConductorAdditionalHeaders } from "./ConductorAdditionalHeaders";
import { Encode } from "./Encode";
import { useCreateHttpRequestHandlers } from "./common";
import { useServiceMethodsDefinition } from "./state/hook";
import { serviceMethodsMachine } from "./state/machine";
import { HandleUpdateTemplateEvent } from "./state/types";
import { HttpTaskFormProps } from "./types";

const httpRequestUriPath = "inputParameters.http_request.uri";
const httpRequestTerminationConditionPath =
  "inputParameters.http_request.terminationCondition";
const httpRequestPollingIntervalPath =
  "inputParameters.http_request.pollingInterval";

export const HTTPPollTaskForm = ({ task, onChange }: HttpTaskFormProps) => {
  const authHeaders = useAuthHeaders();
  const currentTask = useMemo(() => task, [task]);
  const [
    {
      onChangeHttpRequest,
      onChangeHttpRequestBody,
      onChangeMethod,
      onChangeAccept,
      onChangeContentType,
      onChangeHeaders,
      onChangePollingStrategy,
      onChangeEncode,
      generatePath,
      onChangeHttpRequestBodyParameter,
    },
    {
      httpHeaders,
      httpRequestBody,
      accept,
      contentType,
      method,
      pollingStrategy,
      httpRequestEncode,
      errorInJsonField,
    },
  ] = useCreateHttpRequestHandlers({ task, onChange });

  const showServiceTemplateSelector = featureFlags.isEnabled(
    FEATURES.REMOTE_SERVICES,
  );

  const serviceMethodsActor = useInterpret(serviceMethodsMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
    },
    actions: {
      templateUpdate: (
        { selectedMethod, selectedSchema, selectedService },
        { url, headers }: HandleUpdateTemplateEvent,
      ) => {
        if (!selectedMethod) {
          return;
        }
        const schema = selectedSchema?.data ?? {};
        const generatedPayload = mock(schema);
        const payloadBody = generatedPayload ? generatedPayload : {};
        const baseUrl = selectedHost
          ? selectedHost
          : getBaseUrl(selectedService?.serviceURI);
        onChange({
          ...task,
          inputParameters: {
            ...task?.inputParameters,
            http_request: {
              ...task?.inputParameters?.http_request,
              uri: baseUrl + url,
              method: selectedMethod?.methodType,
              body: payloadBody,
              headers: headers,
              accept: selectedMethod?.responseContentType ?? "application/json",
              contentType:
                selectedMethod?.requestContentType ?? "application/json",
            },
          },
          taskDefinition: {
            ...task?.taskDefinition,
            inputSchema: {
              ...(selectedMethod?.inputType
                ? {
                    name: selectedMethod?.inputType,
                    type: "JSON",
                  }
                : {}),
            },
            outputSchema: {
              ...(selectedMethod?.outputType
                ? {
                    name: selectedMethod?.outputType,
                    type: "JSON",
                  }
                : {}),
            },
          },
        });
      },
    },
  });

  const [
    {
      services,
      selectedService,
      selectedServiceMethods,
      selectedMethod,
      showServiceRegistryPopulatorModal,
      selectedHost,
    },
    {
      handleSelectService,
      handleSelectMethod,
      handleShowServiceRegistryPopulatorModal,
      handleSelectHost,
    },
  ] = useServiceMethodsDefinition(serviceMethodsActor);

  const [bodyViewType, setBodyViewType] = useState("JSON");

  return (
    <Box width="100%">
      <MaybeVariable
        value={task?.inputParameters?.http_request}
        onChange={onChangeHttpRequest}
        taskType={TaskType.HTTP_POLL}
        path={HTTP_REQUEST_PATH}
      >
        {/* service selection section */}
        {showServiceTemplateSelector && (
          <ServiceRegistryPopulator
            modalShow={showServiceRegistryPopulatorModal}
            setModalShow={handleShowServiceRegistryPopulatorModal}
            handleSelectService={handleSelectService}
            selectedService={selectedService}
            services={services}
            handleSelectMethod={handleSelectMethod}
            selectedMethod={selectedMethod}
            selectedServiceMethodsOptions={selectedServiceMethods}
            serviceType={ServiceType.HTTP}
            actor={serviceMethodsActor}
            handleSelectHost={handleSelectHost}
            selectedHost={selectedHost}
          />
        )}
        {/* end of service selection section */}
        <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid
                  size={{
                    xs: 12,
                    md: 4,
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    openOnFocus
                    onChange={onChangeMethod}
                    value={method}
                    otherOptions={Object.values(HTTPMethods)}
                    label="Method"
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 8,
                  }}
                >
                  <ConductorAutocompleteVariables
                    fullWidth
                    label="URL"
                    value={_path(httpRequestUriPath, currentTask)}
                    onChange={(value) =>
                      onChange(
                        updateField(httpRequestUriPath, value, currentTask),
                      )
                    }
                  />
                </Grid>
              </Grid>
            </Grid>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                  }}
                >
                  <ConductorAutocompleteVariables
                    openOnFocus
                    onChange={onChangeAccept}
                    value={accept}
                    otherOptions={CONTENT_TYPE_SUGGESTIONS}
                    label="Accept"
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                  }}
                >
                  <ConductorAutocompleteVariables
                    openOnFocus
                    onChange={onChangeContentType}
                    value={contentType}
                    otherOptions={CONTENT_TYPE_SUGGESTIONS}
                    label="Content-Type"
                  />
                </Grid>
              </Grid>
            </Grid>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid size={12}>
                  <ConductorCodeBlockInput
                    label={"Termination condition"}
                    tooltip={{
                      title: "Termination condition",
                      content:
                        "If the condition is evaluated as true, the task will be marked as completed.",
                    }}
                    labelStyle={{
                      pointerEvents: "auto",
                      display: "flex",
                      alignContent: "center",
                    }}
                    language="javascript"
                    minHeight={300}
                    autoformat={false}
                    value={_path(
                      httpRequestTerminationConditionPath,
                      currentTask,
                    )}
                    onChange={(value) =>
                      onChange(
                        updateField(
                          httpRequestTerminationConditionPath,
                          value,
                          currentTask,
                        ),
                      )
                    }
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                  }}
                >
                  <ConductorInput
                    fullWidth
                    label="Polling interval"
                    value={_path(httpRequestPollingIntervalPath, currentTask)}
                    onTextInputChange={(value) =>
                      onChange(
                        updateField(
                          httpRequestPollingIntervalPath,
                          value,
                          currentTask,
                        ),
                      )
                    }
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                  }}
                >
                  <ConductorAutocompleteVariables
                    openOnFocus
                    onChange={onChangePollingStrategy}
                    value={pollingStrategy}
                    otherOptions={Object.values(PollingStrategy)}
                    label="Polling strategy"
                  />
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </TaskFormSection>

        <TaskFormSection
          title="Other headers:"
          accordionAdditionalProps={{ defaultExpanded: true }}
        >
          <Grid container sx={{ width: "100%" }} spacing={1}>
            <Grid size={12}>
              <ConductorAdditionalHeaders
                headers={httpHeaders}
                onChangeHeaders={onChangeHeaders}
                value={httpHeaders}
                taskType={TaskType.HTTP_POLL}
                path={generatePath(HEADERS_PATH)}
              />
            </Grid>
          </Grid>
        </TaskFormSection>

        <TaskFormSection>
          <Grid container sx={{ width: "100%" }}>
            <Grid size={12}>
              <FormControlLabel
                labelPlacement="start"
                control={
                  <RadioButtonGroup
                    items={[
                      {
                        value: "JSON",
                        label: "JSON",
                      },
                      {
                        value: "PARAMETERS",
                        label: "Parameters",
                      },
                    ]}
                    name={"bodyViewType"}
                    value={bodyViewType}
                    onChange={(_event, value) => {
                      setBodyViewType(value);
                    }}
                  />
                }
                label="Body:"
                sx={{
                  marginBottom: 2,
                  marginLeft: 0,
                  "& .MuiFormControlLabel-label": {
                    fontWeight: 600,
                    color: colors.gray07,
                  },
                }}
              />
              {bodyViewType === "JSON" ? (
                <ConductorCodeBlockInput
                  onChange={onChangeHttpRequestBody}
                  value={JSON.stringify(httpRequestBody || {}, null, 2)}
                  containerProps={{ sx: { width: "100%" } }}
                  error={errorInJsonField}
                  autoformat={false}
                />
              ) : (
                <ConductorFlatMapFormBase
                  title=""
                  keyColumnLabel="Key"
                  valueColumnLabel="Value"
                  addItemLabel="Add parameter"
                  showFieldTypes
                  value={httpRequestBody}
                  onChange={(changes) =>
                    onChangeHttpRequestBodyParameter(changes)
                  }
                />
              )}
            </Grid>
          </Grid>
        </TaskFormSection>
      </MaybeVariable>
      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={currentTask} />
          <Optional onChange={onChange} taskJson={task} />
          <Encode value={httpRequestEncode} onChange={onChangeEncode} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
