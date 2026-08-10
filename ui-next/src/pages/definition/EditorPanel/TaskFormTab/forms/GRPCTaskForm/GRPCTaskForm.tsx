import { Box, Grid, Checkbox, FormControlLabel } from "@mui/material";
import { useContext, useEffect, useMemo, useState } from "react";

import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";

import { TaskType } from "types";

import { ConductorCacheOutput } from "../ConductorCacheOutputForm";
import { MaybeVariable } from "../MaybeVariable";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";

import { GrpcTaskFormProps } from "./types";

import { useInterpret } from "@xstate/react";
import { useAuthHeaders } from "utils/query";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ServiceDefDto, ServiceType } from "types/RemoteServiceTypes";
import { splitHostAndPort } from "utils/remoteServices";

import { serviceMethodsMachine } from "../HTTPTaskForm/state/machine";
import { useServiceMethodsDefinition } from "../HTTPTaskForm/state/hook";
import { updateField } from "utils/fieldHelpers";
import { ConductorAdditionalHeaders } from "../HTTPTaskForm/ConductorAdditionalHeaders";
import { SchemaDefinition } from "types/SchemaDefinition";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { tryToJson } from "utils/utils";
import { featureFlags, FEATURES } from "utils/flags";
import HedgingConfigForm from "../HedgingConfigForm";
import ServiceRegistryPopulator from "../ServiceRegistrySelector";
import MuiTypography from "components/ui/MuiTypography";
import { Link } from "react-router";
import EditTaskDefConfigModal from "../HTTPTaskForm/EditTaskDefConfigModal";
import { MessageContext } from "components/providers/messageContext";
import { HandleUpdateTemplateEvent } from "../HTTPTaskForm/state/types";

export const GRPCTaskForm = ({ task, onChange }: GrpcTaskFormProps) => {
  const authHeaders = useAuthHeaders();
  const { setMessage } = useContext(MessageContext);
  const currentTask = useMemo(() => task, [task]);

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
        { selectedMethod, selectedService, selectedSchema },
        { url, headers }: HandleUpdateTemplateEvent,
      ) => {
        if (!selectedMethod) {
          return;
        }

        const method = (
          selectedSchema as unknown as ServiceDefDto
        )?.methods?.find(
          ({ methodName }) => methodName === selectedMethod?.methodName,
        );

        const { host: serviceUriHost, port: serviceUriPort } = splitHostAndPort(
          selectedService?.serviceURI,
        );
        const { host: selectedHost, port: selectedPort } =
          splitHostAndPort(url);
        const host = selectedHost ? selectedHost : serviceUriHost;
        const port = selectedPort ? selectedPort : serviceUriPort;
        const operationNamePlusMethodName =
          (selectedMethod?.operationName ? selectedMethod?.operationName : "") +
          "/" +
          (selectedMethod?.methodName ? selectedMethod?.methodName : "");
        onChange({
          ...task,
          inputParameters: {
            ...task?.inputParameters,
            service: selectedService?.name,
            methodType: selectedMethod?.methodType,
            method: operationNamePlusMethodName,
            host: host,
            port: port,
            headers: headers,
            request: method?.exampleInput ?? {},
            inputType: selectedMethod?.inputType,
            outputType: selectedMethod?.outputType,
          },
        });
      },
    },
  });
  const [errorInJsonField, setErrorInJsonField] = useState(false);

  const onChangeHttpRequestBody = (maybeEventOrValue: string) => {
    const json = tryToJson(maybeEventOrValue);
    if (json != null) {
      onChange(updateField("inputParameters.request", json, task));
      setErrorInJsonField(false);
    } else if (json == null && task?.inputParameters?.request != null) {
      setErrorInJsonField(true);
    }
  };

  const onChangeHeaders = (modHttpHeaders: any) =>
    onChange(updateField("inputParameters.headers", modHttpHeaders, task));

  const [
    {
      services,
      selectedService,
      selectedServiceMethods,
      selectedMethod,
      schemas,
      showServiceRegistryPopulatorModal,
      currentTaskDefinition,
      selectedHost,
    },
    {
      handleSelectService,
      handleSelectMethod,
      handleShowServiceRegistryPopulatorModal,
      handleChangeTaskDefName,
      handleSelectHost,
    },
  ] = useServiceMethodsDefinition(serviceMethodsActor);

  const selectedServiceConfig = useMemo(
    () =>
      services?.find(
        (item: Partial<ServiceDefDto>) =>
          item.name === task?.inputParameters?.service,
      )?.config,
    [services, task?.inputParameters?.service],
  );

  useEffect(() => {
    if (showServiceTemplateSelector) {
      handleChangeTaskDefName(task?.name);
    }
  }, [task?.name, handleChangeTaskDefName, showServiceTemplateSelector]);

  return (
    <Box width="100%">
      <MaybeVariable
        value={task?.inputParameters}
        onChange={(val) => onChange(updateField("inputParameters", val, task))}
        path={"inputParameters"}
        taskType={TaskType.GRPC}
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
            serviceType={ServiceType.GRPC}
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
                <Grid size={12}>
                  <Grid container sx={{ width: "100%", mt: 4 }} spacing={3}>
                    {task?.inputParameters?.service && (
                      <Grid
                        size={{
                          xs: 12,
                          md: 6,
                          sm: 12,
                        }}
                      >
                        <ConductorAutocompleteVariables
                          id="grpc-task-service"
                          openOnFocus
                          value={task?.inputParameters?.service ?? ""}
                          onChange={(val) =>
                            onChange(
                              updateField("inputParameters.service", val, task),
                            )
                          }
                          label="Service"
                          disabled
                        />
                      </Grid>
                    )}
                    <Grid
                      size={{
                        xs: 12,
                        md: task?.inputParameters?.service ? 6 : 12,
                        sm: 12,
                      }}
                    >
                      <ConductorAutocompleteVariables
                        id="grpc-task-method-name"
                        fullWidth
                        label="Method name"
                        value={task?.inputParameters?.method ?? ""}
                        onChange={(val) =>
                          onChange(
                            updateField("inputParameters.method", val, task),
                          )
                        }
                      />
                    </Grid>
                  </Grid>
                  {showServiceTemplateSelector &&
                    task?.inputParameters?.service && (
                      <Box pt={2}>
                        <Link
                          to={`/remote-services/${encodeURIComponent(
                            task?.inputParameters?.service,
                          )}`}
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
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 8,
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id="grpc-task-host"
                    openOnFocus
                    onChange={(val) =>
                      onChange(updateField("inputParameters.host", val, task))
                    }
                    value={task?.inputParameters?.host ?? ""}
                    label="Host"
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 4,
                    sm: 12,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id="grpc-task-port"
                    fullWidth
                    label="Port"
                    value={task?.inputParameters?.port ?? ""}
                    onChange={(val) =>
                      onChange(updateField("inputParameters.port", val, task))
                    }
                    coerceTo="integer"
                  />
                </Grid>
                <Grid size={12}>
                  <ConductorCodeBlockInput
                    label={"Request: "}
                    onChange={onChangeHttpRequestBody}
                    value={JSON.stringify(
                      task.inputParameters?.request || {},
                      null,
                      2,
                    )}
                    containerProps={{ sx: { width: "100%" } }}
                    error={errorInJsonField}
                    autoformat={false}
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 8,
                    sm: 12,
                  }}
                >
                  <ConductorAutoComplete
                    fullWidth
                    id="id-select-method-type"
                    options={[
                      "UNARY",
                      "CLIENT_STREAMING",
                      "SERVER_STREAMING",
                      "BIDI_STREAMING",
                    ]}
                    freeSolo={false}
                    onChange={(_, val) =>
                      onChange(
                        updateField("inputParameters.methodType", val, task),
                      )
                    }
                    value={task?.inputParameters?.methodType ?? ""}
                    label="Method type"
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                    sm: 12,
                  }}
                >
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={task?.inputParameters?.useSSL ?? false}
                        onChange={(e) =>
                          onChange(
                            updateField(
                              "inputParameters.useSSL",
                              e.target.checked,
                              task,
                            ),
                          )
                        }
                      />
                    }
                    label="Use SSL"
                  />
                </Grid>
                <Grid
                  size={{
                    xs: 12,
                    md: 6,
                    sm: 12,
                  }}
                >
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={task?.inputParameters?.trustCert ?? false}
                        onChange={(e) =>
                          onChange(
                            updateField(
                              "inputParameters.trustCert",
                              e.target.checked,
                              task,
                            ),
                          )
                        }
                      />
                    }
                    label="Trust Certificate"
                  />
                </Grid>

                {showServiceTemplateSelector && (
                  <>
                    {selectedServiceConfig &&
                      selectedServiceConfig?.circuitBreakerConfig && (
                        <Box padding={"5px 0"} width="100%">
                          <MuiTypography
                            marginTop="4px"
                            marginBottom="14px"
                            color="#767676"
                            fontWeight={600}
                          >
                            CircuitBreaker Configuration:
                          </MuiTypography>
                          <Box mt={4}>
                            <Grid container sx={{ width: "100%" }} spacing={3}>
                              <Grid size={12}>
                                <Grid
                                  container
                                  sx={{ width: "100%" }}
                                  spacing={3}
                                >
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
                        </Box>
                      )}
                    {/* end of circuitBreakerConfig */}

                    {currentTaskDefinition ? (
                      <Box padding={"5px 0"} width="100%">
                        <EditTaskDefConfigModal
                          actor={serviceMethodsActor}
                          hedgingComponent={
                            <HedgingConfigForm
                              hedgingConfig={
                                task?.inputParameters?.hedgingConfig
                              }
                              onChange={(data) => {
                                onChange(
                                  updateField(
                                    "inputParameters.hedgingConfig",
                                    data,
                                    task,
                                  ),
                                );
                              }}
                            />
                          }
                        />
                      </Box>
                    ) : (
                      <Grid
                        size={{
                          xs: 12,
                          md: 6,
                          sm: 12,
                        }}
                      >
                        <HedgingConfigForm
                          hedgingConfig={task?.inputParameters?.hedgingConfig}
                          onChange={(data) => {
                            onChange(
                              updateField(
                                "inputParameters.hedgingConfig",
                                data,
                                task,
                              ),
                            );
                          }}
                        />
                      </Grid>
                    )}
                  </>
                )}
                <Grid pb={2} size={12}>
                  <ConductorAdditionalHeaders
                    headers={task?.inputParameters?.headers ?? {}}
                    onChangeHeaders={onChangeHeaders}
                    taskType={TaskType.HTTP_POLL}
                    path={"inputParameters.headers"}
                    value={task?.inputParameters?.headers ?? {}}
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
                    id="input-type"
                    openOnFocus
                    value={task?.inputParameters?.inputType ?? ""}
                    onChange={(val) =>
                      onChange(
                        updateField("inputParameters.inputType", val, task),
                      )
                    }
                    otherOptions={schemas?.map(
                      (item: SchemaDefinition) => item?.name,
                    )}
                    label="Input type"
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
                    id="output-type-name"
                    fullWidth
                    label="Output type"
                    value={task?.inputParameters?.outputType ?? ""}
                    otherOptions={schemas?.map(
                      (item: SchemaDefinition) => item?.name,
                    )}
                    onChange={(val) =>
                      onChange(
                        updateField("inputParameters.outputType", val, task),
                      )
                    }
                  />
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </TaskFormSection>
      </MaybeVariable>
      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={currentTask} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
