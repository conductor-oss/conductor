import _path from "lodash/fp/path";
import _groupBy from "lodash/groupBy";
import _isNil from "lodash/isNil";
import _isUndefined from "lodash/isUndefined";
import { Entries } from "types/helperTypes";
import { getDifferenceInSeconds, humanizeDuration } from "utils/date";
import {
  FilterOption,
  FilterOptions,
  FetchResponse,
  PollData,
  QueueData,
  RangeOptions,
} from "./state/types";

export type QueueSummary = Partial<PollData> & {
  queueName: string;
  size: number;
  pollerCount: number;
};

const queueKeyForPollData = ({ queueName, domain }: PollData) =>
  domain ? `${domain}:${queueName}` : queueName;

const matchesFilter = (value: number, filter?: FilterOption) => {
  if (!filter) {
    return true;
  }

  return filter.option === RangeOptions.GT
    ? value > filter.size
    : value < filter.size;
};

/**
 * Adapts the two OSS queue endpoints to the combined response shape consumed
 * by the queue monitor. Domain-specific poll records use the same queue key as
 * the queue-size endpoint (`domain:taskType`).
 */
export const createQueueMonitorResponse = (
  queueSizes: Record<string, number>,
  pollData: PollData[],
  filterOptions: FilterOptions,
): FetchResponse => {
  const normalizedPollData = pollData.map((poll) => ({
    ...poll,
    queueName: queueKeyForPollData(poll),
  }));
  const pollsByQueue = _groupBy(normalizedPollData, "queueName");
  const queueNames = new Set([
    ...Object.keys(queueSizes),
    ...Object.keys(pollsByQueue),
  ]);

  const queueData = Array.from(queueNames).reduce<QueueData>(
    (result, queueName) => {
      const queuePolls = pollsByQueue[queueName] ?? [];
      const pollerCount = new Set(
        queuePolls.map(({ workerId }) => workerId).filter(Boolean),
      ).size;
      const lastPollTime = queuePolls.reduce(
        (latest, poll) => Math.max(latest, poll.lastPollTime),
        0,
      );
      const size = queueSizes[queueName] ?? 0;

      if (
        matchesFilter(size, filterOptions.queue) &&
        matchesFilter(pollerCount, filterOptions.worker) &&
        matchesFilter(lastPollTime, filterOptions.lastPollTime)
      ) {
        result[queueName] = { size, pollerCount };
      }

      return result;
    },
    {},
  );
  const includedQueueNames = new Set(Object.keys(queueData));

  return {
    queueData,
    pollData: normalizedPollData.filter(({ queueName }) =>
      includedQueueNames.has(queueName),
    ),
  };
};

/**
 * Combines queue sizes with polling details without interpreting queue names as
 * object paths. Queue names are opaque identifiers and can validly contain
 * dots, brackets, and other path-like characters.
 */
export const createQueueSummaries = (
  pollDataByQueueName: Record<string, PollData[]>,
  queueData: QueueData,
): QueueSummary[] => {
  const queuesWithPollData = new Set(Object.keys(pollDataByQueueName));

  const activeQueues = Object.entries(pollDataByQueueName).map(
    ([queueName, pollData]) => {
      const latestPoll = pollData.reduce<PollData | undefined>(
        (latest, current) =>
          !latest || current.lastPollTime > latest.lastPollTime
            ? current
            : latest,
        undefined,
      );
      const { size = 0, pollerCount = 0 } = queueData[queueName] ?? {};

      return {
        ...latestPoll,
        queueName,
        size,
        pollerCount,
      };
    },
  );

  const inactiveQueues = Object.entries(queueData)
    .filter(([queueName]) => !queuesWithPollData.has(queueName))
    .map(([queueName, { size = 0 }]) => ({
      queueName,
      size,
      pollerCount: 0,
    }));

  return activeQueues.concat(inactiveQueues);
};

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
