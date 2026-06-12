import { CreateAndDisplayApplicationMachineContext } from "./types";

export const isApplicationCreated = ({
  applicationId,
}: CreateAndDisplayApplicationMachineContext) => {
  return applicationId !== undefined;
};
