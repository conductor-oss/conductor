import { QueueMonitorMachineContext } from "./types";
import _isNil from "lodash/isNil";

export const noQueueNameSelected = (context: QueueMonitorMachineContext) =>
  _isNil(context.selectedQueueName);
