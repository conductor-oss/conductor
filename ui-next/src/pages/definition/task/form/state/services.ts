import { TaskDefinitionFormContext } from "pages/definition/task/form/state/types";

import { handleDownloadFile } from "pages/definition/task/state/services";
import { validatingService } from "pages/definition/task/state/validator";

export const validateForm = async ({
  modifiedTaskDefinition,
}: TaskDefinitionFormContext) => {
  return validatingService(modifiedTaskDefinition, false);
};

export { handleDownloadFile };
