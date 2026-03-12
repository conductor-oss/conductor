import { ActorRef } from "xstate";
import {
  ServiceMethodsMachineEvents,
  ServiceMethodsMachineEventTypes,
  ServiceMethodsMachineStates,
} from "./types";
import { useSelector } from "@xstate/react";
import { Method, ServiceDefDto } from "types/RemoteServiceTypes";
import { useCallback, useMemo, useState } from "react";
import { parseApiMethod } from "./helper";
import { useFetch } from "utils/query";

export const useServiceMethodsDefinition = (
  serviceMethodsActor: ActorRef<ServiceMethodsMachineEvents>,
) => {
  const [
    showServiceRegistryPopulatorModal,
    setShowServiceRegistryPopulatorModal,
  ] = useState(false);
  const { send } = serviceMethodsActor;
  const { data: schemas } = useFetch("/schema?short=true");
  const services = useSelector(
    serviceMethodsActor,
    (state) => state.context.services,
  );

  const selectedService = useSelector(
    serviceMethodsActor,
    (state) => state.context.selectedService,
  );

  const selectedMethod = useSelector(
    serviceMethodsActor,
    (state) => state.context.selectedMethod,
  );

  const selectedServiceMethods = useMemo(() => {
    const methods = selectedService?.methods ?? [];
    return methods?.map(
      (item: Method) => `[${item.methodType}]` + item.methodName,
    );
  }, [selectedService]);

  const currentTaskDefinition = useSelector(
    serviceMethodsActor,
    (state) => state.context.currentTaskDefinition,
  );

  const isInIdleState = useSelector(serviceMethodsActor, (state) =>
    state.matches(ServiceMethodsMachineStates.IDLE),
  );

  const selectedHost = useSelector(
    serviceMethodsActor,
    (state) => state.context.selectedHost,
  );

  const handleSelectService = (serviceName: string) => {
    const service = services?.find(
      (item: Partial<ServiceDefDto>) => item.name === serviceName,
    );
    send({
      type: ServiceMethodsMachineEventTypes.SELECT_SERVICE,
      data: service,
    });
  };
  const handleSelectHost = (host: string) => {
    send({
      type: ServiceMethodsMachineEventTypes.SELECT_HOST,
      data: host,
    });
  };

  const handleSelectMethod = (method: string) => {
    const { methodName, methodType } = parseApiMethod(method);
    const result = selectedService?.methods?.find(
      (item: Method) =>
        item.methodName === methodName && item.methodType === methodType,
    );

    send({
      type: ServiceMethodsMachineEventTypes.SELECT_METHOD,
      data: result,
    });
  };

  const handleShowServiceRegistryPopulatorModal = (val: boolean) => {
    setShowServiceRegistryPopulatorModal(val);
  };

  const handleChangeTaskDefName = useCallback(
    (val: string) => {
      if (val) {
        send({
          type: ServiceMethodsMachineEventTypes.SELECT_TASK,
          taskDefName: val,
        });
      }
    },
    [send],
  );

  const handleChangeTaskConfig = (
    name: string,
    value: number | string | null,
  ) => {
    send({
      type: ServiceMethodsMachineEventTypes.HANDLE_CHANGE_TASK_CONFIG,
      name,
      value,
    });
  };

  const handleUpdateTaskConfig = () => {
    send({
      type: ServiceMethodsMachineEventTypes.UPDATE_TASK_CONFIG,
    });
  };

  const handleResetModifiedTaskConfig = () => {
    send({
      type: ServiceMethodsMachineEventTypes.RESET_MODIFIED_TASK_CONFIG,
    });
  };

  const fetchTaskDefinition = useCallback(() => {
    send({
      type: ServiceMethodsMachineEventTypes.FETCH_TASK_DEFINITION_EVENT,
    });
  }, [send]);

  const handleUpdateTemplate = ({
    updatedUrl,
    headers,
  }: {
    updatedUrl: string;
    headers?: Record<string, string>;
  }) => {
    send({
      type: ServiceMethodsMachineEventTypes.HANDLE_UPDATE_TEMPLATE,
      url: updatedUrl,
      headers: headers ?? {},
    });
  };

  return [
    {
      services,
      selectedService,
      selectedServiceMethods,
      selectedMethod,
      schemas,
      showServiceRegistryPopulatorModal,
      currentTaskDefinition,
      isInIdleState,
      selectedHost,
    },
    {
      handleSelectService,
      handleSelectMethod,
      handleSelectHost,
      handleShowServiceRegistryPopulatorModal,
      handleChangeTaskDefName,
      handleChangeTaskConfig,
      handleUpdateTaskConfig,
      handleResetModifiedTaskConfig,
      fetchTaskDefinition,
      handleUpdateTemplate,
    },
  ] as const;
};
