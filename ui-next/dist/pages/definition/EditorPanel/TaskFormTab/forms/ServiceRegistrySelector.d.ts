import { Method, ServiceDefDto } from "types/RemoteServiceTypes";
import { ActorRef } from "xstate";
import { ServiceMethodsMachineEvents } from "./HTTPTaskForm/state/types";
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
declare function ServiceRegistryPopulator({ modalShow, setModalShow, handleSelectService, handleSelectHost, selectedService, services, handleSelectMethod, selectedMethod, selectedServiceMethodsOptions, serviceType, actor, selectedHost, }: ServiceRegistryPopulatorProps): import("react").JSX.Element;
export default ServiceRegistryPopulator;
