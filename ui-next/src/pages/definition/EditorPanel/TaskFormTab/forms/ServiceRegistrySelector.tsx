import {
  Box,
  Button,
  CircularProgress,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from "@mui/material";
import { MagicWand } from "@phosphor-icons/react";
import MuiButton from "components/MuiButton";
import UIModal from "components/UIModal";
import { ConductorAutoComplete } from "components/v1";
import ConductorInput from "components/v1/ConductorInput";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { MessageContext } from "components/v1/layout/MessageContext";
import _every from "lodash/every";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import { replaceDynamicParams } from "utils/remoteServices";
import { Method, ServiceDefDto } from "types/RemoteServiceTypes";
import { useContext, useMemo, useState } from "react";
import { ActorRef } from "xstate";
import { useServiceMethodsDefinition } from "./HTTPTaskForm/state/hook";
import { ServiceMethodsMachineEvents } from "./HTTPTaskForm/state/types";

const TruncatedText = ({
  text,
  maxLines = 3,
}: {
  text?: string;
  maxLines?: number;
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <Box
      sx={{
        fontSize: "12px",
        color: "#364153",
        display: "-webkit-box",
        WebkitLineClamp: isExpanded ? "unset" : maxLines,
        WebkitBoxOrient: "vertical",
        overflow: "hidden",
        textOverflow: "ellipsis",
        cursor: "pointer",
      }}
      onClick={() => setIsExpanded(!isExpanded)}
    >
      {text ?? ""}
    </Box>
  );
};

interface ServiceRegistryPopulatorProps {
  modalShow: boolean;
  setModalShow: (val: boolean) => void;
  handleSelectService: (val: string) => void;
  selectedService: ServiceDefDto;
  services: ServiceDefDto[];
  handleSelectMethod: (val: string) => void;
  selectedMethod: Method;
  selectedServiceMethodsOptions: Method[];
  serviceType: string;
  actor: ActorRef<ServiceMethodsMachineEvents>;
  handleSelectHost?: (val: string) => void;
  selectedHost?: string;
}
function ServiceRegistryPopulator({
  modalShow,
  setModalShow,
  handleSelectService,
  handleSelectHost,
  selectedService,
  services,
  handleSelectMethod,
  selectedMethod,
  selectedServiceMethodsOptions,
  serviceType,
  actor,
  selectedHost,
}: ServiceRegistryPopulatorProps) {
  const { setMessage } = useContext(MessageContext);
  const [requestParams, setRequestParams] = useState<Record<string, any>>({});
  const [{ isInIdleState }, { handleUpdateTemplate }] =
    useServiceMethodsDefinition(actor);

  const isParamsValid = useMemo(() => {
    if (serviceType !== "HTTP") {
      return true;
    }
    if (
      !selectedMethod?.requestParams ||
      _isEmpty(selectedMethod.requestParams)
    ) {
      return true;
    }

    return _every(selectedMethod.requestParams, (param, _key) => {
      if (!param?.required) return true;
      const value = requestParams?.[param.name]?.value;
      return !_isNil(value) && value !== "";
    });
  }, [selectedMethod?.requestParams, requestParams, serviceType]);

  const handleExecute = () => {
    if (selectedMethod?.methodType && selectedService?.serviceURI) {
      const { url: updatedUrl, headers } = replaceDynamicParams(
        selectedMethod?.methodName,
        requestParams,
      );
      const updatedHeaders = {
        ...headers,
        ...(selectedService?.authMetadata &&
          selectedService?.authMetadata?.key &&
          selectedService?.authMetadata?.value && {
            [selectedService?.authMetadata?.key]:
              selectedService?.authMetadata?.value,
          }),
      };
      if (selectedService?.type === "gRPC") {
        handleUpdateTemplate({
          updatedUrl: selectedHost ?? "",
          headers: updatedHeaders,
        });
      } else {
        handleUpdateTemplate({ updatedUrl, headers: updatedHeaders });
      }
      setModalShow(false);
      setMessage({
        severity: "success",
        text: `Applied successfully`,
      });
    }
  };

  const handleInputChange = (
    data: { name: string; type: string; required: boolean },
    value: string,
  ) => {
    const updatedRequestParams = {
      ...requestParams,
      [data.name]: { ...data, value: value },
    };
    setRequestParams(updatedRequestParams);
  };

  return (
    <Box padding="0 13px" mt={-3} display={"flex"} justifyContent={"flex-end"}>
      <MuiButton
        startIcon={<MagicWand />}
        variant="text"
        size="small"
        onClick={() => setModalShow(true)}
      >
        Populate from remote services
      </MuiButton>
      <UIModal
        open={modalShow}
        setOpen={setModalShow}
        title="Populate from remote services"
        enableCloseButton
        icon={<MagicWand />}
      >
        <Grid container spacing={6} sx={{ width: "100%" }}>
          <Grid size={12}>
            <ConductorAutoComplete
              fullWidth
              id="select-service-field"
              freeSolo={false}
              onChange={(_, val) => {
                handleSelectService(val);
                setRequestParams({});
              }}
              value={selectedService?.name ?? ""}
              options={
                services
                  ?.filter((item: ServiceDefDto) => item.type === serviceType)
                  ?.map((item: ServiceDefDto) => item?.name) ?? []
              }
              label="Service"
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutoComplete
              fullWidth
              id="select-service-host"
              freeSolo={false}
              onChange={(_, val) => {
                handleSelectHost?.(val);
              }}
              value={selectedHost ?? ""}
              options={[
                ...(selectedService?.servers?.map((server) => server.url) ??
                  []),
                ...(selectedService?.serviceURI
                  ? [selectedService?.serviceURI]
                  : []),
              ]}
              label={serviceType === "gRPC" ? "Host:Port" : "Host"}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutoComplete
              fullWidth
              id="select-service-method-field"
              freeSolo={false}
              onChange={(_, val) => {
                handleSelectMethod(val);
                setRequestParams({});
              }}
              value={
                selectedMethod
                  ? `[${selectedMethod?.methodType}]` +
                    selectedMethod?.methodName
                  : ""
              }
              options={selectedServiceMethodsOptions ?? []}
              renderOption={(props, option) => (
                <Box component="li" {...props} sx={{ wordBreak: "break-word" }}>
                  {option}
                </Box>
              )}
              label="Service method"
            />
          </Grid>
          {serviceType === "HTTP" && (
            <Grid size={12}>
              <ConductorInput
                fullWidth
                id="select-service-url"
                disabled
                value={
                  (selectedHost ?? selectedService?.serviceURI ?? "") +
                  (selectedMethod?.methodName ?? "")
                }
                label="URL"
              />
            </Grid>
          )}
          {selectedMethod?.description && (
            <Grid size={12}>
              <Box
                sx={{
                  marginTop: "8px",
                  padding: "12px",
                  backgroundColor: "#F9FAFB",
                  borderRadius: "4px",
                  borderLeft: "4px solid #60A5FA",
                }}
              >
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "flex-start",
                    gap: "8px",
                  }}
                >
                  <Box
                    component="span"
                    sx={{
                      color: "#2563EB",
                      flexShrink: 0,
                      "& svg": {
                        width: "16px",
                        height: "16px",
                      },
                    }}
                  >
                    ⓘ
                  </Box>
                  <TruncatedText
                    text={selectedMethod?.description}
                    maxLines={3}
                  />
                </Box>
              </Box>
            </Grid>
          )}
          {selectedMethod?.deprecated && (
            <Grid size={12}>
              <Box
                sx={{
                  padding: "4px 8px",
                  backgroundColor: "#FFEDD5",
                  border: "1px solid #FDBA74",
                  borderRadius: "4px",
                }}
              >
                <Box
                  sx={{
                    fontSize: "12px",
                    color: "#9A3412",
                    display: "flex",
                    alignItems: "center",
                    gap: "4px",
                  }}
                >
                  ⚠️ This method is deprecated and may be removed in future
                  versions.
                </Box>
              </Box>
            </Grid>
          )}
          {serviceType === "HTTP" && (
            <Box
              sx={{
                width: "100%",
                pt: 2,
                paddingLeft: "12px",
              }}
            >
              <TableContainer
                component="div"
                style={{
                  padding: 2,
                  overflow: "scroll",
                  maxHeight: "400px",
                }}
              >
                <Table
                  aria-label="simple table"
                  sx={{
                    borderCollapse: "collapse", // Remove default borders
                  }}
                >
                  <TableHead>
                    <TableRow
                      sx={{
                        "& th": {
                          borderBottom: "1px solid #ABB5B6",
                          borderTop: "none",
                          fontSize: "12px",
                          fontWeight: 700,
                          paddingBottom: "5px",
                        },
                      }}
                    >
                      <TableCell sx={{ width: "30%" }}>Name</TableCell>
                      <TableCell>Description</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {selectedMethod?.requestParams &&
                    selectedMethod?.requestParams?.length > 0 ? (
                      selectedMethod?.requestParams?.map((row) => (
                        <TableRow key={row.name}>
                          <TableCell
                            component="th"
                            scope="row"
                            sx={{
                              border: "none", // Remove all borders for table cells
                            }}
                          >
                            <Box>
                              <Box sx={{ fontSize: "14px", fontWeight: 700 }}>
                                {row.name}
                                {row.required && (
                                  <span
                                    style={{
                                      fontSize: "10px",
                                      color: "rgba(255,0,0,.6)",
                                      top: "-6px",
                                      position: "relative",
                                    }}
                                  >
                                    * required
                                  </span>
                                )}
                              </Box>
                              <Box sx={{ fontSize: "11px" }}>
                                {row?.schema?.type}
                              </Box>
                              <Box
                                sx={{
                                  fontSize: "12px",
                                  fontWeight: 600,
                                  color: "gray",
                                  fontStyle: "italic",
                                }}
                              >
                                ({row.type})
                              </Box>
                            </Box>
                          </TableCell>
                          <TableCell
                            sx={{
                              border: "none", // Remove all borders for table cells
                            }}
                          >
                            <ConductorAutocompleteVariables
                              value={requestParams[row.name]?.value ?? ""}
                              onChange={(val) => handleInputChange(row, val)}
                            />
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <Box sx={{ padding: "4px 15px" }}>No parameters</Box>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          )}
        </Grid>
        <Box display="flex" justifyContent="flex-end" width="100%" pt={3}>
          <Button
            onClick={handleExecute}
            disabled={!isParamsValid || !isInIdleState}
          >
            {isInIdleState ? (
              "Populate"
            ) : (
              <CircularProgress color="inherit" size={20} />
            )}
          </Button>
        </Box>
      </UIModal>
    </Box>
  );
}

export default ServiceRegistryPopulator;
