import _path from "lodash/fp/path";
import _isNil from "lodash/isNil";
import _isUndefined from "lodash/isUndefined";
import { Entries } from "types/helperTypes";
import { getDifferenceInSeconds, humanizeDuration } from "utils/date";
import { FilterOption, FilterOptions, RangeOptions } from "./state/types";

interface QueueMonitorRoute {
  workerSize?: string;
  workerOpt?: RangeOptions;
  queueSize?: string;
  queueOpt?: RangeOptions;
  lastPollTimeSize?: string;
  lastPollTimeOpt?: RangeOptions;
}

export const filterOptionOrNot = (
  prefix: string,
  matchParams: QueueMonitorRoute,
): FilterOption | undefined => {
  const size = _path(`${prefix}Size`, matchParams);
  const option = _path(`${prefix}Opt`, matchParams);
  return [size, option].every(_isNil)
    ? undefined
    : {
        size,
        option,
      };
};
export const renameKeys = (
  someObj: Record<string, unknown>,
  newNames: Record<string, string>,
): Record<string, unknown> =>
  Object.fromEntries(
    Object.entries(someObj).map(([key, value]) => [
      _path(key, newNames),
      value,
    ]),
  );

export const filterOptionToQueryParams = (
  filterOptions: FilterOptions,
): string =>
  (Object.entries(filterOptions) as Entries<Record<string, FilterOption>>)
    .reduce((acc: string[], [key, value]): string[] => {
      if (_isNil(value)) {
        return acc;
      }
      let size = value.size || 0;
      if (key === "lastPollTime") {
        size = size || Date.now();
      }
      return acc.concat(`${key}Size=${size}&${key}Opt=${value?.option}`);
    }, [])
    .join("&");

export const hasNoQueryParams = (filterOptions: FilterOptions) =>
  Object.values(filterOptions).every(_isUndefined);

export const lastPollTimeColumnRenderer = (lastPollTime: number) => {
  if (lastPollTime) {
    const now = Date.now();
    const durationInMillis = now - lastPollTime;
    const secondsDiff = getDifferenceInSeconds(now, lastPollTime);
    return secondsDiff > 0
      ? humanizeDuration(lastPollTime, now)
      : `${Math.abs(durationInMillis)} millis`;
  }
  return "N/A";
};
