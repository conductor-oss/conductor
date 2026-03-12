import { RefreshMachineContext } from "./types";

export const elapsedIsLessThanDuration = (context: RefreshMachineContext) =>
  context.elapsed < context.duration;

export const elapsedIsBiggerThanDuration = (context: RefreshMachineContext) => {
  return context.elapsed >= context.duration;
};
