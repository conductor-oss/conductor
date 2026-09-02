import { ServiceMethodsMachineContext } from "./types";
import { ServiceDefDto } from "types/RemoteServiceTypes";
export declare const fetchServices: ({ authHeaders: headers, }: ServiceMethodsMachineContext) => Promise<ServiceDefDto>;
export declare const fetchSchema: ({ authHeaders: headers, selectedMethod, }: ServiceMethodsMachineContext) => Promise<{}>;
export declare const fetchSchemaForServiceRegistry: ({ authHeaders: headers, selectedService, }: ServiceMethodsMachineContext) => Promise<{}>;
export declare const fetchTaskDefinition: ({ authHeaders: headers, currentTaskDefName, }: ServiceMethodsMachineContext) => Promise<ServiceDefDto | undefined>;
export declare const updateTaskDefinitionService: ({ authHeaders, modifiedTaskDef, }: ServiceMethodsMachineContext) => Promise<ServiceDefDto>;
