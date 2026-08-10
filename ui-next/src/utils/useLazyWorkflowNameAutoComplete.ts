import { useMemo, useState } from "react";
import { useWorkflowNames } from "./query";

export const useLazyWorkflowNameAutoComplete = (
  nameFilter = (_x: string) => true,
): [() => void, string[]] => {
  const [fetch, setEnableFetch] = useState(false);
  const workflowNames = useWorkflowNames({ enabled: fetch });
  const names = useMemo((): string[] => {
    return workflowNames
      .filter(nameFilter)
      .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  }, [workflowNames, nameFilter]);
  return [() => setEnableFetch(true), names];
};
