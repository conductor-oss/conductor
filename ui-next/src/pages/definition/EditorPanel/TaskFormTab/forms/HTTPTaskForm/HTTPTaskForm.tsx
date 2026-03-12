import { Box, Grid, Switch } from "@mui/material";
import { useInterpret } from "@xstate/react";
import RadioButtonGroup from "components/RadioButtonGroup";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { MessageContext } from "components/v1/layout/MessageContext";
import { assoc as _assoc, pipe as _pipe } from "lodash/fp";
import { mock } from "mock-json-schema";
import { ServiceType, ServiceDefDto } from "types/RemoteServiceTypes";
import { useContext, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { HTTPMethods, TaskType } from "types";
import {
  CONTENT_TYPE_SUGGESTIONS,
  HEADERS_PATH,
} from "utils/constants/httpSuggestions";
import { featureFlags, FEATURES } from "utils/flags";
import { useAuthHeaders } from "utils/query";
import { getBaseUrl } from "utils/utils";
import { ConductorCacheOutput } from "../ConductorCacheOutputForm";
import HedgingConfigForm from "../HedgingConfigForm";
import { MaybeVariable } from "../MaybeVariable";
import { Optional } from "../OptionalFieldForm";
import ServiceRegistryPopulator from "../ServiceRegistrySelector";
import { TaskFormHeaderEventTypes } from "../TaskFormHeader/state";
import TaskFormSection from "../TaskFormSection";
import { ConductorAdditionalHeaders } from "./ConductorAdditionalHeaders";
import EditTaskDefConfigModal from "./EditTaskDefConfigModal";
import { Encode } from "./Encode";
import { useCreateHttpRequestHandlers } from "./common";
import { useServiceMethodsDefinition } from "./state/hook";
import { serviceMethodsMachine } from "./state/machine";
import { HandleUpdateTemplateEvent } from "./state/types";
import { HttpTaskFormProps } from "./types";

export const HTTPTaskForm = ({
  task,
  onChange,
  taskFormHeaderActor,
}: HttpTaskFormProps) => {
  const authHeaders = useAuthHeaders();
  const { setMessage } = useContext(MessageContext);
  const currentTask = useMemo(() => task, [task]);
  const [
    {
      onChangeHttpRequest,
      onChangeMethod,
      onChangeService,
      onChangeAccept,
      onChangeContentType,
      onChangeHeaders,
      onChangeUri,
      onChangeAsyncComplete,
      onChangeHttpRequestBody,
      onChangeEncode,
      generatePath,
      onChangeHttpRequestBodyParameter,
      onChangeHedgingConfig,
    },
    {
      httpHeaders,
      accept,
      contentType,
      method,
      uri,
      service,
      httpRequestBody,
      HTTP_REQUEST_PATH,
      httpRequestEncode,
      errorInJsonField,
      hedgingConfig,
    },
  ] = useCreateHttpRequestHandlers({ task, onChange });

  const showServiceTemplateSelector = featureFlags.isEnabled(
    FEATURES.REMOTE_SERVICES,
  );

  const serviceMethodsActor = useInterpret(serviceMethodsMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      currentTaskDefName: task?.name,
    },
    actions: {
      setErrorMessage: (_context, event: any) => {
        setMessage({
          severity: "error",
          text: event?.data?.message ?? "Something went wrong",
        });
      },
      setSuccessMessage: (_context, event: any) => {
        setMessage({
          severity: "success",
          text: event?.data?.message ?? "Task definition updated successfully",
        });
      },
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
            service: selectedService?.name,
            uri: baseUrl + url,
            method: selectedMethod?.methodType,
            body: payloadBody,
            headers: headers,
            accept: selectedMethod?.responseContentType ?? "application/json",
            contentType:
              selectedMethod?.requestContentType ?? "application/json",
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
      currentTaskDefinition,
      selectedHost,
    },
    {
      handleSelectService,
      handleSelectMethod,
      handleShowServiceRegistryPopulatorModal,
      handleChangeTaskDefName,
      fetchTaskDefinition,
      handleSelectHost,
    },
  ] = useServiceMethodsDefinition(serviceMethodsActor);

  const [bodyViewType, setBodyViewType] = useState("JSON");

  const selectedServiceConfig = useMemo(
    () =>
      services?.find((item: Partial<ServiceDefDto>) => item.name === service)
        ?.config,
    [services, service],
  );

  useEffect(() => {
    if (showServiceTemplateSelector) {
      handleChangeTaskDefName(task?.name);
    }
  }, [task?.name, handleChangeTaskDefName, showServiceTemplateSelector]);

  useEffect(() => {
    if (taskFormHeaderActor) {
      const subscription = taskFormHeaderActor.subscribe((state) => {
        if (
          state.event.type ===
          TaskFormHeaderEventTypes.TASK_CREATED_SUCCESSFULLY
        ) {
          fetchTaskDefinition();
        }
      });

      return () => {
        subscription.unsubscribe();
      };
    }
  }, [taskFormHeaderActor, fetchTaskDefinition]);

  return (
    <Box width="100%">
      <MaybeVariable
        value={task?.inputParameters?.http_request}
        onChange={onChangeHttpRequest}
        path={HTTP_REQUEST_PATH}
        taskType={TaskType.HTTP}
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
        <TaskFormSection
          title="Request Details"
          accordionAdditionalProps={{ defaultExpanded: true }}
        >
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                {showServiceTemplateSelector && service && (
                  <Grid
                    size={{
                      xs: 12,
                      md: 4,
                      sm: 12,
                    }}
                  >
                    <ConductorAutocompleteVariables
                      id="http-task-service"
                      disabled
                      onChange={onChangeService}
                      value={service}
                      label="Service"
                    />
                  </Grid>
                )}
                <Grid
                  size={{
                    xs: 12,
                    md: showServiceTemplateSelector && service ? 8 : 4,
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id="http-task-method"
                    openOnFocus
                    onChange={onChangeMethod}
                    value={method}
                    otherOptions={Object.values(HTTPMethods)}
                    label="Method"
                  />
                </Grid>
                {(!showServiceTemplateSelector || !service) && (
                  <Grid
                    size={{
                      xs: 12,
                      md: 8,
                      sm: 12,
                    }}
                  >
                    <ConductorAutocompleteVariables
                      id="http-task-url"
                      fullWidth
                      label="URL"
                      value={uri}
                      onChange={onChangeUri}
                    />
                  </Grid>
                )}
              </Grid>
            </Grid>
            {showServiceTemplateSelector && service && (
              <Box pt={2} pl={3}>
                <Link
                  to={`/remote-services/${encodeURIComponent(service)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    display: "flex",
                    alignItems: "center",
                    fontSize: "9pt",
                    textDecoration: "none",
                    opacity: 0.7,
                  }}
                >
                  Edit service definition
                </Link>
              </Box>
            )}
            {showServiceTemplateSelector && service && (
              <Grid size={12}>
                <Grid container sx={{ width: "100%" }} spacing={3}>
                  <Grid
                    size={{
                      xs: 12,
                      md: 12,
                      sm: 12,
                    }}
                  >
                    <ConductorAutocompleteVariables
                      id="http-task-url"
                      fullWidth
                      label="URL"
                      value={uri}
                      onChange={onChangeUri}
                    />
                  </Grid>
                </Grid>
              </Grid>
            )}
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id="http-task-accept"
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
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id="http-task-content-type"
                    openOnFocus
                    onChange={onChangeContentType}
                    value={contentType}
                    otherOptions={CONTENT_TYPE_SUGGESTIONS}
                    label="Content-Type"
                  />
                </Grid>
              </Grid>
              <Grid size={12} mt={3}>
                <Encode
                  title="Encode URI"
                  value={httpRequestEncode}
                  onChange={onChangeEncode}
                />
              </Grid>
            </Grid>
          </Grid>
        </TaskFormSection>

        {showServiceTemplateSelector && (
          <>
            {/* circuitBreakerConfig */}
            {selectedServiceConfig &&
              selectedServiceConfig?.circuitBreakerConfig && (
                <TaskFormSection
                  title="CircuitBreaker Configuration:"
                  accordionAdditionalProps={{ defaultExpanded: true }}
                >
                  <Box mt={4}>
                    <Grid container sx={{ width: "100%" }} spacing={3}>
                      <Grid size={12}>
                        <Grid container sx={{ width: "100%" }} spacing={3}>
                          {Object.entries(
                            selectedServiceConfig?.circuitBreakerConfig,
                          ).map(([key, value]) => (
                            <Grid key={key} size={6}>
                              <ConductorAutocompleteVariables
                                disabled
                                onChange={() => {}}
                                value={value as string}
                                label={key}
                              />
                            </Grid>
                          ))}
                        </Grid>
                      </Grid>
                    </Grid>
                  </Box>
                </TaskFormSection>
              )}

            {currentTaskDefinition ? (
              <TaskFormSection
                title=""
                accordionAdditionalProps={{ defaultExpanded: true }}
              >
                <EditTaskDefConfigModal
                  actor={serviceMethodsActor}
                  hedgingComponent={
                    <HedgingConfigForm
                      hedgingConfig={hedgingConfig}
                      onChange={onChangeHedgingConfig}
                    />
                  }
                />
              </TaskFormSection>
            ) : (
              <TaskFormSection
                title=""
                accordionAdditionalProps={{ defaultExpanded: true }}
              >
                <Grid container sx={{ width: "100%" }}>
                  <Grid
                    size={{
                      xs: 12,
                      md: 6,
                      sm: 12,
                    }}
                  >
                    <Box mt={2} mr={1}>
                      <HedgingConfigForm
                        hedgingConfig={hedgingConfig}
                        onChange={onChangeHedgingConfig}
                      />
                    </Box>
                  </Grid>
                </Grid>
              </TaskFormSection>
            )}
          </>
        )}
        <TaskFormSection
          title="Additional headers"
          accordionAdditionalProps={{ defaultExpanded: true }}
        >
          <Grid container sx={{ width: "100%" }} spacing={1}>
            <Grid size={12}>
              <ConductorAdditionalHeaders
                headers={httpHeaders}
                onChangeHeaders={onChangeHeaders}
                taskType={TaskType.HTTP_POLL}
                path={generatePath(HEADERS_PATH)}
                value={httpHeaders}
              />
            </Grid>
          </Grid>
        </TaskFormSection>

        <TaskFormSection title="Request Body">
          <Grid container sx={{ width: "100%" }}>
            <Grid size={12}>
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
          <Box>
            <Box sx={{ fontWeight: 600, color: "#767676", ml: -2.2 }}>
              <Switch
                color="primary"
                checked={currentTask.asyncComplete ?? false}
                onChange={onChangeAsyncComplete}
              />
              Async complete
            </Box>
            <Box sx={{ opacity: 0.5 }}>
              When turned on, task completion occurs asynchronously, with the
              task remaining in progress while waiting for external APIs or
              events to complete the task.
            </Box>
          </Box>
        </Box>
      </TaskFormSection>
    </Box>
  );
};
