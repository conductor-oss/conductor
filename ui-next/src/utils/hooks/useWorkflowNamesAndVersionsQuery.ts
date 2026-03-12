import { useMemo } from "react";
import {
  useSharedQueryContext,
  useFetch,
  STALE_TIME_WORKFLOW_DEFS,
} from "../query";
import { getUniqueWorkflowsWithVersions } from "../workflow";

export function useWorkflowNamesAndVersionsQuery(): [
  Map<string, number[]>,
  ReturnType<typeof useFetch>,
] {
  const { url } = useSharedQueryContext();
  const fetchResult = useFetch(url, {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });

  return [
    useMemo(
      () => getUniqueWorkflowsWithVersions(fetchResult.data),
      [fetchResult.data],
    ),
    fetchResult,
  ];
}
