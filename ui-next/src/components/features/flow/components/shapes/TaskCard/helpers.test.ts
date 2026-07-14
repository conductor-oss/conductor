import { dowhileHasAllIterationsInOutput, showIterationChip } from "./helpers";

// this test is meant to check if the outputData of dowhile is not summarized.(no data loss)
describe("dowhileHasAllIterationsInOutput", () => {
  const outputData = {
    "1": {},
    "2": {},
    iteration: 2,
  };
  const outputDataWhileWorkflowInProgress = {
    "1": {},
    "2": {},
    iteration: 3,
  };
  const summarizedOutputData = {
    "119": {},
    "120": {},
    "121": {},
    iteration: 121,
  };

  it("Should return true, as the output data is not summarized as it has all the output from 1 to iteration number", () => {
    const result = dowhileHasAllIterationsInOutput(outputData);
    expect(result).toBe(true);
  });
  it("Should return  false, as the output data is summarized as it doesn't have all the output from 1 to iteration number", () => {
    const result = dowhileHasAllIterationsInOutput(summarizedOutputData);
    expect(result).toBe(false);
  });
  // since the backend sends n-1 iterations in outputData while the workflow is running, we are doing the below test.
  it("Should return  true, as the output data is not summarized as it doesn't have all the output from 1 to (iteration number - 1) when Workflow in progress", () => {
    const result = dowhileHasAllIterationsInOutput(
      outputDataWhileWorkflowInProgress,
    );
    expect(result).toBe(true);
  });
  it("Should return false when iteration is missing", () => {
    expect(dowhileHasAllIterationsInOutput({})).toBe(false);
  });
});

describe("showIterationChip", () => {
  const nodeDataWithKeepLastN = {
    attempts: 20,
    parentLoop: {
      inputData: {
        keepLastN: 10,
      },
      outputData: {
        "11": {},
        "12": {},
        "13": {},
        "14": {},
        "15": {},
        "16": {},
        "17": {},
        "18": {},
        "19": {},
        "20": {},
        iteration: 20,
      },
    },
  };
  const nodeDataWithoutKeepLastNAndSummarized = {
    attempts: 20,
    parentLoop: {
      inputData: {},
      outputData: {
        "11": {},
        "12": {},
        "13": {},
        "14": {},
        "15": {},
        "16": {},
        "17": {},
        "18": {},
        "19": {},
        "20": {},
        iteration: 20,
      },
    },
  };

  const nodeDataWithoutKeepLastNAndNotSummarized = {
    attempts: 10,
    parentLoop: {
      inputData: {},
      outputData: {
        "1": {},
        "2": {},
        "3": {},
        "4": {},
        "5": {},
        "6": {},
        "7": {},
        "8": {},
        "9": {},
        "10": {},
        iteration: 10,
      },
    },
  };
  const nodeDataWithoutKeepLastNAndNotSummarized2 = {
    attempts: 10,
    parentLoop: {
      inputData: {},
      outputData: {
        "1": {},
        "2": {},
        "3": {},
        "4": {},
        "5": {},
        "6": {},
        "7": {},
        "8": {},
        "9": {},
        iteration: 10,
      },
    },
  };
  const nodeDataWithKeepLastNAndNotSummarized = {
    attempts: 10,
    parentLoop: {
      inputData: {
        keepLastN: 10,
      },
      outputData: {
        "1": {},
        "2": {},
        "3": {},
        "4": {},
        "5": {},
        "6": {},
        "7": {},
        "8": {},
        "9": {},
        iteration: 10,
      },
    },
  };

  it("Should return false, as the keepLastN is available - dont show iteration chip", () => {
    const result = showIterationChip(nodeDataWithKeepLastN as any);
    expect(result).toBe(false);
  });
  it("Should return  false, as eventhough the keepLastN is not available, but the output is summarized - dont show iteration chip", () => {
    const result = showIterationChip(
      nodeDataWithoutKeepLastNAndSummarized as any,
    );
    expect(result).toBe(false);
  });
  it("Should return  true, as eventhough it doesn't have keepLastN, but the output is not summarized - show iteration chip", () => {
    const result = showIterationChip(
      nodeDataWithoutKeepLastNAndNotSummarized as any,
    );
    expect(result).toBe(true);
  });
  // since the backend sends n-1 iterations in outputData while the workflow is running, we are doing the below test.
  it("Should return  true, as eventhough it doesn't have keepLastN, and having n-1 iterations data in output.and output is not summarized - show iteration chip", () => {
    const result = showIterationChip(
      nodeDataWithoutKeepLastNAndNotSummarized2 as any,
    );
    expect(result).toBe(true);
  });
  it("Should return false, as eventhough output is not summarized it  has keepLastN. - dont show iteration chip", () => {
    const result = showIterationChip(
      nodeDataWithKeepLastNAndNotSummarized as any,
    );
    expect(result).toBe(false);
  });
  it("Should return true for standalone retries with no parent DO_WHILE", () => {
    const result = showIterationChip({ attempts: 3 } as any);
    expect(result).toBe(true);
  });
  it("Should return false for standalone tasks with only one attempt", () => {
    const result = showIterationChip({ attempts: 1 } as any);
    expect(result).toBe(false);
  });

  it("visibility matrix for attempt badge on task cards", () => {
    expect({
      standaloneRetries: showIterationChip({ attempts: 3 } as any),
      standaloneSingleAttempt: showIterationChip({ attempts: 1 } as any),
      keepLastN: showIterationChip(nodeDataWithKeepLastN as any),
      summarizedWithoutKeepLastN: showIterationChip(
        nodeDataWithoutKeepLastNAndSummarized as any,
      ),
      unsummarizedWithoutKeepLastN: showIterationChip(
        nodeDataWithoutKeepLastNAndNotSummarized as any,
      ),
    }).toMatchInlineSnapshot(`
      {
        "keepLastN": false,
        "standaloneRetries": true,
        "standaloneSingleAttempt": false,
        "summarizedWithoutKeepLastN": false,
        "unsummarizedWithoutKeepLastN": true,
      }
    `);
  });
});
