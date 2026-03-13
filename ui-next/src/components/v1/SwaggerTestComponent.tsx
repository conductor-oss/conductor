import {
  Box,
  Grid,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
} from "@mui/material";
import { Button, Input } from "components";

import React, { useState } from "react";
import { CodeSnippet } from "./CodeSnippet";
import { Method, ServiceDefDto } from "types/RemoteServiceTypes";

function getColorScheme(method: any) {
  switch (method) {
    case "POST":
      return {
        primaryColor: "#49cc90",
        secondaryColor: "rgba(73,204,144,.1)",
      };
    case "GET":
      return {
        primaryColor: "#61affe",
        secondaryColor: "rgba(97,175,254,.1)",
      };
    case "DELETE":
      return {
        primaryColor: "#f93e3e",
        secondaryColor: "rgba(249,62,62,.1)",
      };
    case "PUT":
      return {
        primaryColor: "#fca130",
        secondaryColor: "rgba(252,161,48,.1)",
      };
    default:
      return {
        primaryColor: "#000000", // Default color
        secondaryColor: "rgba(0,0,0,.1)",
      };
  }
}

async function apiFetch(
  methodType: string,
  endpoint: string,
  body?: any,
  token?: string,
) {
  const options = {
    method: methodType,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token || ""}`,
    },
    ...(body && { body: JSON.stringify(body) }),
  };

  const response = await fetch(endpoint, options);

  return response;
}
function replaceDynamicParams(
  url: string,
  params: Record<string, unknown>,
): string {
  return url.replace(/\{(\w+)\}/g, (_, key: string): string => {
    return key in params ? String(params[key]) : `{${key}}`;
  });
}

const SwaggerTestComponent = ({
  data,
  serviceDefinition,
}: {
  data: Method;
  serviceDefinition: Partial<ServiceDefDto>;
}) => {
  const [requestParams, setRequestParams] = useState<Record<string, any>>({});

  const handleExecute = () => {
    if (data?.methodType && serviceDefinition?.serviceURI) {
      const updatedMethodName = replaceDynamicParams(
        data?.methodName,
        requestParams,
      );
      apiFetch(
        data?.methodType,
        serviceDefinition?.serviceURI + updatedMethodName,
      );
    }
  };

  const handleInputChange = (name: string, value: string) => {
    const updatedRequestParams = { ...requestParams, [name]: value };
    setRequestParams(updatedRequestParams);
  };
  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor:
          (data?.methodType &&
            getColorScheme(data?.methodType)?.primaryColor) ??
          "gray",
        backgroundColor:
          (data?.methodType &&
            getColorScheme(data?.methodType)?.secondaryColor) ??
          "gray",
        borderRadius: "4px",
      }}
    >
      {/* header */}
      <Grid
        container
        alignItems={"center"}
        padding="5px"
        borderBottom={"1px solid"}
        borderColor={
          (data?.methodType &&
            getColorScheme(data?.methodType)?.primaryColor) ??
          "gray"
        }
        sx={{ width: "100%" }}
      >
        <Grid>
          <Box
            sx={{
              minWidth: "80px",
              background:
                (data?.methodType &&
                  getColorScheme(data?.methodType)?.primaryColor) ??
                "gray",
              borderRadius: "3px",
              color: "#fff",
              fontWeight: 700,
              padding: "6px 0",
              textAlign: "center",
              fontSize: "14px",
            }}
          >
            {data?.methodType}
          </Box>
        </Grid>
        <Grid>
          <Box
            sx={{
              color: "#3b4151",
              fontWeight: 600,
              padding: "0 10px",
              fontSize: "16px",
            }}
          >
            {data?.methodName}
          </Box>
        </Grid>
      </Grid>
      {/* body */}
      <Box>
        {/* params */}
        <Box
          sx={{
            background: "hsla(0,0%,100%,.8)",
            boxShadow: "0 1px 2px rgba(0,0,0,.1)",
            padding: "0 20px",
          }}
        >
          <Tabs
            value={0}
            onChange={() => {}}
            aria-label="basic tabs"
            TabIndicatorProps={{
              style: {
                backgroundColor:
                  (data?.methodType &&
                    getColorScheme(data?.methodType)?.primaryColor) ??
                  "gray",
                height: "4px",
              },
            }}
            sx={{
              "& .MuiTab-root": {
                color: "#3b4151",
                fontWeight: 800,
                fontSize: "14px",
              },
            }}
          >
            <Tab label="Parameters" />
          </Tabs>
        </Box>
        <Box sx={{ padding: "0 20px" }}>
          <TableContainer component="div">
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
                      borderBottom: "1px solid #ABB5B6", // Single underline for header
                      borderTop: "none", // Remove top border
                      fontSize: "12px",
                      fontWeight: 700,
                      paddingBottom: "5px",
                    },
                  }}
                >
                  <TableCell sx={{ width: "20%" }}>Name</TableCell>
                  <TableCell>Description</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data?.requestParams && data?.requestParams?.length > 0 ? (
                  data?.requestParams?.map((row) => (
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
                        <Input
                          value={requestParams[row.name] ?? ""}
                          onChange={(value) =>
                            handleInputChange(row.name, value)
                          }
                          sx={{
                            maxWidth: "fit-content",
                            background: "#ffffff",
                          }}
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
          <Box display="flex" justifyContent="flex-end" width="100%" pb={1}>
            <Button onClick={handleExecute}>Execute</Button>
          </Box>
        </Box>
        {/* response */}
        <Box>
          <Box
            sx={{
              background: "hsla(0,0%,100%,.8)",
              boxShadow: "0 1px 2px rgba(0,0,0,.1)",
              padding: "0 20px",
            }}
          >
            <Tabs
              value={0}
              onChange={() => {}}
              aria-label="basic tabs"
              TabIndicatorProps={{
                style: {
                  height: "0px",
                },
              }}
              sx={{
                "& .MuiTab-root": {
                  color: "#3b4151",
                  fontWeight: 800,
                  fontSize: "14px",
                },
              }}
            >
              <Tab label="Responses" />
            </Tabs>
          </Box>
          <Box sx={{ padding: "8px 20px" }}>
            <Box sx={{ padding: "0 17px" }}>
              <Box sx={{ fontSize: "12px", fontWeight: 600 }}>Request URL</Box>
              <Box>
                <CodeSnippet
                  code={serviceDefinition?.serviceURI ?? ""}
                  noCopyToClipboard
                />
              </Box>
              <Box sx={{ fontSize: "12px", fontWeight: 600 }}>
                Server response
              </Box>
              <TableContainer component="div">
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
                          borderBottom: "1px solid #ABB5B6", // Single underline for header
                          borderTop: "none", // Remove top border
                          fontSize: "12px",
                          fontWeight: 700,
                          paddingBottom: "5px",
                        },
                      }}
                    >
                      <TableCell sx={{ width: "20%" }}>Code</TableCell>
                      <TableCell>Details</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    <TableRow key={"server-response"}>
                      <TableCell
                        component="th"
                        scope="row"
                        sx={{
                          border: "none",
                          alignContent: "flex-start",
                        }}
                      >
                        <Box>403</Box>
                      </TableCell>
                      <TableCell
                        sx={{
                          border: "none",
                        }}
                      >
                        <CodeSnippet code={``} />
                      </TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
};

export default SwaggerTestComponent;
