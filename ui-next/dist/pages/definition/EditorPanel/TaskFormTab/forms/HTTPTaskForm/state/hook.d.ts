import { ActorRef } from "xstate";
import { ServiceMethodsMachineEvents } from "./types";
export declare const useServiceMethodsDefinition: (serviceMethodsActor: ActorRef<ServiceMethodsMachineEvents>) => readonly [{
    readonly services: any;
    readonly selectedService: any;
    readonly selectedServiceMethods: any;
    readonly selectedMethod: any;
    readonly schemas: any;
    readonly showServiceRegistryPopulatorModal: boolean;
    readonly currentTaskDefinition: any;
    readonly isInIdleState: any;
    readonly selectedHost: any;
}, {
    readonly handleSelectService: (serviceName: string) => void;
    readonly handleSelectMethod: (method: string) => void;
    readonly handleSelectHost: (host: string) => void;
    readonly handleShowServiceRegistryPopulatorModal: (val: boolean) => void;
    readonly handleChangeTaskDefName: (val: string) => void;
    readonly handleChangeTaskConfig: (name: string, value: number | string | null) => void;
    readonly handleUpdateTaskConfig: () => void;
    readonly handleResetModifiedTaskConfig: () => void;
    readonly fetchTaskDefinition: () => void;
    readonly handleUpdateTemplate: ({ updatedUrl, headers, }: {
        updatedUrl: string;
        headers?: Record<string, string>;
    }) => void;
}];
