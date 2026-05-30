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
  for (let i = 1; i < max; i++) {
    if (!Object.prototype.hasOwnProperty.call(outputData, String(i))) {
      return false;
    }
  }
  return true;
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
