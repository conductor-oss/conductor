import { Box, Typography } from "@mui/material";
import { fetchDoWhileIterations, DoWhileIterationOutput } from "commonServices";
import { useCallback, useEffect, useMemo } from "react";
import { useInfiniteQuery, useQueryClient } from "react-query";
import { colors } from "theme/tokens/variables";
import { AuthHeaders } from "types/common";
import { ExecutionTask } from "types/Execution";
import { dropdownIcon } from "./dropdownIcon";
import { LabelRenderer } from "./LabelRenderer";
import { CollapsibleIterationList } from "./CollapsibleIterationList";
import {
  pageStartForIteration,
  IterationPlaceholder,
} from "./iterationHelpers";

const PAGE_SIZE = 50;

/**
 * ExecutionTask augmented with UI-internal fields injected by hook.ts when
 * building synthetic placeholder rows for iterations not yet in the task list.
 */
export interface AugmentedExecutionTask extends ExecutionTask {
  iteration?: number;
  _parentDoWhileRef?: string;
  _summarized?: boolean;
  _totalIterations?: number;
}

export interface InlineTaskIterationsProps {
  retryIterationOptions: (AugmentedExecutionTask | IterationPlaceholder)[];
  selectedTask: AugmentedExecutionTask;
  isIteration: boolean;
  handleSelectTask: (task: ExecutionTask) => void;
  executionId?: string;
  authHeaders?: AuthHeaders;
}

export const InlineTaskIterations = ({
  retryIterationOptions,
  selectedTask,
  isIteration,
  handleSelectTask,
  executionId,
  authHeaders,
}: InlineTaskIterationsProps) => {
  const parentDoWhileRef = useMemo(
    () =>
      retryIterationOptions.find((opt) => opt._parentDoWhileRef)
        ?._parentDoWhileRef,
    [retryIterationOptions],
  );

  const innerTaskRef = selectedTask?.workflowTask?.taskReferenceName;

  const currentIteration = selectedTask.iteration;
  const isCurrentSummarized = selectedTask._summarized === true;

  const {
    data: pagesData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery(
    ["dowhile-iterations", executionId, parentDoWhileRef],
    ({ pageParam = 0 }) =>
      fetchDoWhileIterations({
        authHeaders: authHeaders as any,
        executionId: executionId!,
        taskReferenceName: parentDoWhileRef!,
        start: pageParam,
        count: PAGE_SIZE,
      }),
    {
      enabled: !!executionId && !!parentDoWhileRef && !!innerTaskRef,
      staleTime: Infinity,
      getNextPageParam: (lastPage, allPages) => {
        const fetched = allPages.length * PAGE_SIZE;
        return fetched < lastPage.totalHits ? fetched : undefined;
      },
    },
  );

  const fetchedIterations: DoWhileIterationOutput[] = useMemo(
    () => pagesData?.pages.flatMap((p) => p.results) ?? [],
    [pagesData],
  );

  const totalHits = pagesData?.pages[0]?.totalHits ?? 0;

  // Auto-upgrade: when the data for a summarized placeholder's iteration
  // arrives via lazy loading, replace the placeholder with real data so the
  // normal detail tabs render.
  useEffect(() => {
    if (!isCurrentSummarized) return;
    if (currentIteration == null || fetchedIterations.length === 0) return;

    const item = fetchedIterations.find(
      (i) => i.iteration === currentIteration,
    );
    if (!item || item.summarized) return;

    const matchingTask = retryIterationOptions.find(
      (opt: any) => opt.iteration === currentIteration,
    );
    if (!matchingTask) return;

    const taskRef = innerTaskRef ?? "";
    handleSelectTask({
      ...matchingTask,
      _summarized: false,
      taskId:
        item.taskIds?.[taskRef] ?? (matchingTask as any).taskId ?? undefined,
      inputData:
        (item.inputData?.[taskRef] as Record<string, unknown>) ?? undefined,
      outputData:
        (item.output?.[taskRef] as Record<string, unknown>) ?? undefined,
    } as any);
  }, [fetchedIterations, isCurrentSummarized, currentIteration]);

  const handleScrollEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const queryClient = useQueryClient();
  const queryKey = ["dowhile-iterations", executionId, parentDoWhileRef];

  const fetchPageForIteration = useCallback(
    async (iterationNum: number) => {
      if (!executionId || !parentDoWhileRef || totalHits === 0) return;
      const alreadyLoaded = fetchedIterations.some(
        (i) => i.iteration === iterationNum,
      );
      if (alreadyLoaded) return;
      const pageStart = pageStartForIteration(
        iterationNum,
        totalHits,
        PAGE_SIZE,
      );
      if (pageStart === null) return;
      try {
        const page = await fetchDoWhileIterations({
          authHeaders: authHeaders as any,
          executionId,
          taskReferenceName: parentDoWhileRef,
          start: pageStart,
          count: PAGE_SIZE,
        });
        queryClient.setQueryData<any>(queryKey, (old: any) => {
          if (!old) return { pages: [page], pageParams: [pageStart] };
          const existingStarts = new Set(old.pageParams as number[]);
          if (existingStarts.has(pageStart)) return old;
          return {
            pages: [...old.pages, page],
            pageParams: [...old.pageParams, pageStart],
          };
        });
      } catch {
        // Silently fail
      }
    },
    [
      executionId,
      parentDoWhileRef,
      authHeaders,
      totalHits,
      fetchedIterations,
      queryClient,
      queryKey,
    ],
  );

  const handlePrefetchPage = useCallback(
    (iterationNum: number) => {
      fetchPageForIteration(iterationNum);
    },
    [fetchPageForIteration],
  );

  const handleJumpToIteration = useCallback(
    async (iterationNum: number) => {
      await fetchPageForIteration(iterationNum);

      const loaded = fetchedIterations.find(
        (i) => i.iteration === iterationNum,
      );
      const matchingTask = retryIterationOptions.find(
        (opt: any) => opt.iteration === iterationNum,
      );

      if (loaded && !loaded.summarized) {
        const taskRef = innerTaskRef ?? "";
        handleSelectTask({
          ...(matchingTask ?? selectedTask),
          _summarized: false,
          iteration: iterationNum,
          taskId:
            loaded.taskIds?.[taskRef] ??
            (matchingTask as any)?.taskId ??
            undefined,
          inputData:
            (loaded.inputData?.[taskRef] as Record<string, unknown>) ??
            undefined,
          outputData:
            (loaded.output?.[taskRef] as Record<string, unknown>) ?? undefined,
        } as any);
      } else if (matchingTask) {
        handleSelectTask(matchingTask as ExecutionTask);
      }
    },
    [
      fetchPageForIteration,
      fetchedIterations,
      retryIterationOptions,
      innerTaskRef,
      handleSelectTask,
      selectedTask,
    ],
  );

  const effectiveIteration = selectedTask.iteration;
  const hasKnownIteration =
    isIteration ||
    (typeof effectiveIteration === "number" && effectiveIteration > 0);

  const headerText = hasKnownIteration
    ? `Iteration ${effectiveIteration ?? ""}`
    : `Attempt #${selectedTask.retryCount ?? 0}`;

  const headerLabel = `${dropdownIcon(selectedTask.status)}${headerText}`;

  if (fetchedIterations.length > 0) {
    return (
      <CollapsibleIterationList
        items={fetchedIterations}
        headerLabel={headerLabel}
        headerText={headerText}
        selectedLabel={hasKnownIteration ? headerText : undefined}
        totalItems={totalHits}
        onPrefetch={handlePrefetchPage}
        onJumpTo={handleJumpToIteration}
        onScrollEnd={handleScrollEnd}
        getOptionLabel={(item) => `Iteration ${item.iteration}`}
        getItemValue={(item) => item.iteration}
        onSelect={(item) => {
          const matchingTask = retryIterationOptions.find(
            (opt) => opt.iteration === item.iteration,
          );
          if (!matchingTask) return;

          if (item.summarized) {
            handleSelectTask(matchingTask as ExecutionTask);
          } else {
            const taskRef = innerTaskRef ?? "";
            handleSelectTask({
              ...matchingTask,
              _summarized: false,
              taskId:
                item.taskIds?.[taskRef] ??
                (matchingTask as AugmentedExecutionTask).taskId ??
                undefined,
              inputData:
                (item.inputData?.[taskRef] as Record<string, unknown>) ??
                undefined,
              outputData:
                (item.output?.[taskRef] as Record<string, unknown>) ??
                undefined,
            } as AugmentedExecutionTask);
          }
        }}
        isItemSelected={(item) => selectedTask.iteration === item.iteration}
        renderItem={(item) => (
          <>
            <Box component="span" sx={{ minWidth: 18, lineHeight: 1 }}>
              {dropdownIcon(item.summarized ? "COMPLETED" : "COMPLETED")}
            </Box>
            <Box component="span">Iteration {item.iteration}</Box>
            {item.summarized && (
              <Typography
                component="span"
                sx={{
                  fontSize: "8pt",
                  color: colors.gray04,
                  opacity: 0.7,
                  ml: 1,
                }}
              >
                (summarized)
              </Typography>
            )}
          </>
        )}
      />
    );
  }

  return (
    <CollapsibleIterationList
      items={retryIterationOptions}
      headerLabel={headerLabel}
      onSelect={(option) => handleSelectTask(option as ExecutionTask)}
      isItemSelected={(option) => {
        const taskId = (option as AugmentedExecutionTask).taskId;
        return !!taskId && taskId === selectedTask.taskId;
      }}
      renderItem={(option) => (
        <LabelRenderer isIteration={isIteration} iterationTask={option} />
      )}
    />
  );
};
