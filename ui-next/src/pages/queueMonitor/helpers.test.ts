import { createQueueMonitorResponse, createQueueSummaries } from "./helpers";
import { PollData, RangeOptions } from "./state";

describe("createQueueSummaries", () => {
  it("treats queue names as opaque keys and uses the latest poll", () => {
    const olderPoll: PollData = {
      queueName: "email.send[v2]",
      domain: "west",
      workerId: "worker-1",
      lastPollTime: 100,
    };
    const newerPoll: PollData = {
      queueName: "email.send[v2]",
      domain: "east",
      workerId: "worker-2",
      lastPollTime: 200,
    };
    const polls = [olderPoll, newerPoll];

    expect(
      createQueueSummaries(
        { "email.send[v2]": polls },
        {
          "email.send[v2]": { size: 7, pollerCount: 2 },
          idle_queue: { size: 3 },
        },
      ),
    ).toEqual([
      {
        ...newerPoll,
        size: 7,
        pollerCount: 2,
      },
      {
        queueName: "idle_queue",
        size: 3,
        pollerCount: 0,
      },
    ]);

    expect(polls).toEqual([olderPoll, newerPoll]);
  });
});

describe("createQueueMonitorResponse", () => {
  const pollData: PollData[] = [
    {
      queueName: "send_email",
      domain: "production",
      workerId: "worker-1",
      lastPollTime: 200,
    },
    {
      queueName: "send_email",
      domain: "production",
      workerId: "worker-2",
      lastPollTime: 300,
    },
  ];

  it("combines the OSS queue-size and poll-data responses", () => {
    expect(
      createQueueMonitorResponse(
        { "production:send_email": 4, pending_task: 2 },
        pollData,
        {},
      ),
    ).toEqual({
      queueData: {
        "production:send_email": { size: 4, pollerCount: 2 },
        pending_task: { size: 2, pollerCount: 0 },
      },
      pollData: [
        { ...pollData[0], queueName: "production:send_email" },
        { ...pollData[1], queueName: "production:send_email" },
      ],
    });
  });

  it("keeps polled queues with no pending tasks and applies filters", () => {
    expect(
      createQueueMonitorResponse({}, pollData, {
        worker: { option: RangeOptions.GT, size: 1 },
      }),
    ).toEqual({
      queueData: {
        "production:send_email": { size: 0, pollerCount: 2 },
      },
      pollData: [
        { ...pollData[0], queueName: "production:send_email" },
        { ...pollData[1], queueName: "production:send_email" },
      ],
    });
  });
});
