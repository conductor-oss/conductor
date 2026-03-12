import { tryFunc } from "utils/utils";
import { ServiceMethodsMachineContext } from "./types";
import { ServiceDefDto } from "types/RemoteServiceTypes";
import { queryClient } from "queryClient";

import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { ErrorObj } from "types/common";

const fetchContext = fetchContextNonHook();

export const fetchServices = async ({
  authHeaders: headers,
}: ServiceMethodsMachineContext) => {
  const schemaPath = `/registry/service`;
  return tryFunc<ServiceDefDto, ErrorObj>({
    fn: async () => {
      return await queryClient.fetchQuery(
        [fetchContext.stack, schemaPath],
        () => fetchWithContext(schemaPath, fetchContext, { headers }),
      );
    },
    customError: {
      message: "Fetching services failed!",
    },
    showCustomError: false,
  });
};

export const fetchSchema = async ({
  authHeaders: headers,
  selectedMethod,
}: ServiceMethodsMachineContext) => {
  const schemaName = selectedMethod?.inputType;
  const schemaPath = `/schema/${schemaName}`;

  if (!schemaName) {
    return {};
  }

  return tryFunc<ServiceDefDto, ErrorObj>({
    fn: async () => {
      return await queryClient.fetchQuery(
        [fetchContext.stack, schemaPath],
        () => fetchWithContext(schemaPath, fetchContext, { headers }),
      );
    },
    customError: {
      message: "Fetching schema failed!",
    },
    showCustomError: false,
  });
};

export const fetchSchemaForServiceRegistry = async ({
  authHeaders: headers,
  selectedService,
}: ServiceMethodsMachineContext) => {
  const schemaName = selectedService?.name;
  const schemaPath = `/registry/service/${schemaName}`;

  if (!schemaName) {
    return {};
  }

  return tryFunc<ServiceDefDto, ErrorObj>({
    fn: async () => {
      return await queryClient.fetchQuery(
        [fetchContext.stack, schemaPath],
        () => fetchWithContext(schemaPath, fetchContext, { headers }),
      );
    },
    customError: {
      message: "Fetching schema failed!",
    },
    showCustomError: false,
  });
};

export const fetchTaskDefinition = async ({
  authHeaders: headers,
  currentTaskDefName,
}: ServiceMethodsMachineContext) => {
  if (!currentTaskDefName) {
    return;
  }
  const taskDefinitionPath = `/metadata/taskdefs/${currentTaskDefName}`;
  return tryFunc<ServiceDefDto, ErrorObj>({
    fn: async () => {
      return await queryClient.fetchQuery(
        [fetchContext.stack, taskDefinitionPath],
        () => fetchWithContext(taskDefinitionPath, fetchContext, { headers }),
      );
    },
    customError: {
      message: "Fetching task definition by name failed!",
    },
    showCustomError: false,
  });
};

export const updateTaskDefinitionService = async ({
  authHeaders,
  modifiedTaskDef,
}: ServiceMethodsMachineContext) => {
  const stringDefinition = JSON.stringify(modifiedTaskDef, null, 2);

  return tryFunc<ServiceDefDto, ErrorObj>({
    fn: async () => {
      return await fetchWithContext(
        "/metadata/taskdefs",
        {},
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: stringDefinition,
        },
      );
    },
    customError: {
      message: "Update task failed!",
    },
    showCustomError: false,
  });
};
