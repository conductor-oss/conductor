export interface RefreshMachineContext {
  elapsed: number;
  duration: number;
  durationSet: number;
}

export enum RefreshMachineEventTypes {
  TICK = "TICK",
  UPDATE_DURATION = "UPDATE_DURATION",
  REFRESH = "REFRESH",
}

export type TickEvent = {
  type: RefreshMachineEventTypes.TICK;
};

export type RefreshEvent = {
  type: RefreshMachineEventTypes.REFRESH;
};

export type UpdateDurationEvent = {
  type: RefreshMachineEventTypes.UPDATE_DURATION;
  value: number;
};

export type TimerEvents = TickEvent | UpdateDurationEvent | RefreshEvent;
