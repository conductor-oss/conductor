import { tryToJson } from "utils/utils";
import { DataTableMachineContext } from "./types";
import { logger } from "utils/logger";

export const saveOrderAndVisibility = async (
  context: DataTableMachineContext,
) => {
  const { localStorageKey, columnOrderAndVisibility } = context;
  if (localStorageKey) {
    window.localStorage.setItem(
      localStorageKey,
      JSON.stringify(columnOrderAndVisibility),
    );

    return columnOrderAndVisibility;
  }
  throw Error("No local storage key has been set");
};

export const maybePullOrderAndVisibility = async (
  context: DataTableMachineContext,
) => {
  const { localStorageKey, columnOrderAndVisibility } = context;
  if (localStorageKey) {
    const savedOrder = window.localStorage.getItem(localStorageKey);
    if (savedOrder) {
      const parsedSavedOrder = tryToJson(savedOrder);
      if (parsedSavedOrder !== undefined) {
        return parsedSavedOrder;
      } else {
        window.localStorage.removeItem(localStorageKey);
        logger.log(
          "Couldn't parse savedOrder hence removing it from localStorage and returning columnOrderAndVisibility.",
        );
      }
    }
  }
  return columnOrderAndVisibility;
};
