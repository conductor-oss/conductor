import { NodeTaskData } from "components/features/flow/nodes/mapper";

export const shouldHide = (
  {
    status = undefined,
    withinExpandedSubWorkflow = false,
  }: Partial<NodeTaskData> = {
    status: undefined,
    withinExpandedSubWorkflow: false,
  },
) => !status && !withinExpandedSubWorkflow;

export function dowhileHasAllIterationsInOutput(
  outputData: Record<string, unknown>,
): boolean {
  const max = outputData?.iteration as number;
  const iterationKeyCount = Object.keys(outputData).filter((k) =>
    Number.isInteger(Number(k)),
  ).length;
  return iterationKeyCount >= max - 1;
}

/**
 * Returns true when the backend has replaced old iteration payloads with a
 * lightweight sentinel ({"_summarized": true}) to keep the response small.
 * All iteration keys are still present so the dropdown can enumerate them,
 * but the full output data is only available for the most recent iterations.
 */
export function dowhileHasSummarizedIterations(
  outputData: Record<string, unknown>,
): boolean {
  return Object.values(outputData).some(
    (val) =>
      val !== null &&
      typeof val === "object" &&
      (val as Record<string, unknown>)["_summarized"] === true,
  );
}

export function showIterationChip(nodeData: NodeTaskData): boolean {
  const keepLastN = nodeData?.parentLoop?.inputData?.keepLastN;
  return (
    !keepLastN &&
    dowhileHasAllIterationsInOutput(nodeData?.parentLoop?.outputData ?? {}) &&
    typeof nodeData?.attempts === "number" &&
    nodeData.attempts > 1
  );
}

// Helper function to check if a string is a valid URI
export const isValidUri = (uriString: string) => {
  try {
    new URL(uriString);
    return true;
  } catch {
    return false;
  }
};
