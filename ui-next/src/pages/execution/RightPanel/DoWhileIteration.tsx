import { Box, Typography } from "@mui/material";
import { DoWhileIterationOutput, fetchDoWhileIterations } from "commonServices";
import ConductorTooltip from "components/ui/ConductorTooltip";
import _nth from "lodash/nth";
import { useCallback, useMemo } from "react";
import { useInfiniteQuery, useQueryClient } from "react-query";
import { colors } from "theme/tokens/variables";
import { AuthHeaders } from "types/common";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { CollapsibleIterationList } from "./CollapsibleIterationList";
import {
  deriveFallbackIterationStatus,
  getOrderedIterationKeys,
} from "./doWhileIterationHelpers";
import { pageStartForIteration } from "./iterationHelpers";
import { IterationStatusIcon } from "./IterationStatusIcon";

const PAGE_SIZE = 50;

export interface DoWhileIterationProps {
  selectedTask: ExecutionTask;
  handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
  doWhileSelection?: DoWhileSelection[];
  executionId?: string;
  authHeaders?: AuthHeaders;
}

export const DoWhileIteration = ({
  selectedTask,
  handleSelectDoWhileIteration,
  doWhileSelection,
  executionId,
  authHeaders,
}: DoWhileIterationProps) => {
  const isTaskProcessing = [
    TaskStatus.PENDING,
    TaskStatus.SCHEDULED,
    TaskStatus.IN_PROGRESS,
  ].includes(selectedTask.status);

  const iterationOptions = getOrderedIterationKeys(
    selectedTask?.outputData ?? {},
    selectedTask,
  );

  const taskReferenceName = selectedTask?.referenceTaskName;

  const {
    data: pagesData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery(
    ["dowhile-iterations", executionId, taskReferenceName],
    ({ pageParam = 0 }) =>
      fetchDoWhileIterations({
        authHeaders: authHeaders as any,
        executionId: executionId!,
        taskReferenceName: taskReferenceName!,
        start: pageParam,
        count: PAGE_SIZE,
      }),
    {
      enabled: !!executionId && !!taskReferenceName,
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

  const currentIteration = _nth(
    doWhileSelection?.filter(
      (item) =>
        item.doWhileTaskReferenceName === selectedTask?.referenceTaskName,
    ),
    0,
  )?.selectedIteration;

  const outputData = selectedTask?.outputData ?? {};

  const isIterationSummarized = (option: number): boolean => {
    if (!Object.prototype.hasOwnProperty.call(outputData, option.toString())) {
      return !isTaskProcessing;
    }
    const val = outputData[option.toString()];
    return (
      val !== null &&
      typeof val === "object" &&
      (val as Record<string, unknown>)["_summarized"] === true
    );
  };

  const handleSelect = useCallback(
    (option: number) => {
      handleSelectDoWhileIteration({
        doWhileTaskReferenceName: selectedTask?.referenceTaskName,
        selectedIteration: option,
      });
    },
    [handleSelectDoWhileIteration, selectedTask?.referenceTaskName],
  );

  const handleSelectFetched = useCallback(
    (item: DoWhileIterationOutput) => {
      handleSelectDoWhileIteration({
        doWhileTaskReferenceName: selectedTask?.referenceTaskName,
        selectedIteration: item.iteration,
      });
    },
    [handleSelectDoWhileIteration, selectedTask?.referenceTaskName],
  );

  const handleScrollEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const queryClient = useQueryClient();
  const queryKey = ["dowhile-iterations", executionId, taskReferenceName];

  const fetchPageForIteration = useCallback(
    async (iterationNum: number) => {
      if (!executionId || !taskReferenceName || totalHits === 0) return;
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
          taskReferenceName,
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
      taskReferenceName,
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
      handleSelectDoWhileIteration({
        doWhileTaskReferenceName: selectedTask?.referenceTaskName,
        selectedIteration: iterationNum,
      });
      await fetchPageForIteration(iterationNum);
    },
    [
      handleSelectDoWhileIteration,
      selectedTask?.referenceTaskName,
      fetchPageForIteration,
    ],
  );

  const headerText =
    currentIteration != null
      ? `Iteration ${currentIteration}`
      : `Iterations (${iterationOptions.length})`;

  const headerLabel = (
    <Box
      component="span"
      sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}
    >
      <IterationStatusIcon status={selectedTask.status} size={13} />
      <span>{headerText}</span>
    </Box>
  );

  const keepLastNTrailing =
    selectedTask?.inputData?.keepLastN != null ? (
      <ConductorTooltip
        title=""
        content={`keepLastN is set to ${selectedTask?.inputData?.keepLastN}`}
        placement="top"
      >
        <img
          alt="info"
          src="/icons/info-icon.svg"
          style={{ paddingLeft: "3px" }}
        />
      </ConductorTooltip>
    ) : null;

  if (fetchedIterations.length > 0) {
    return (
      <CollapsibleIterationList
        items={fetchedIterations}
        headerLabel={headerLabel}
        headerText={headerText}
        selectedLabel={
          currentIteration != null ? `Iteration ${currentIteration}` : undefined
        }
        trailing={keepLastNTrailing}
        totalItems={totalHits}
        onPrefetch={handlePrefetchPage}
        onJumpTo={handleJumpToIteration}
        onScrollEnd={handleScrollEnd}
        onSelect={(item) => handleSelectFetched(item)}
        isItemSelected={(item) => currentIteration === item.iteration}
        getOptionLabel={(item) => `Iteration ${item.iteration}`}
        getItemValue={(item) => item.iteration}
        renderItem={(item) => (
          <>
            <Box
              component="span"
              sx={{ minWidth: 18, display: "flex", alignItems: "center" }}
            >
              <IterationStatusIcon
                status={item.status ?? TaskStatus.COMPLETED}
                size={13}
              />
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
      items={iterationOptions}
      headerLabel={headerLabel}
      trailing={keepLastNTrailing}
      totalItems={iterationOptions.length}
      getOptionLabel={(option) => `Iteration ${option}`}
      getItemValue={(option) => option}
      onJumpTo={handleJumpToIteration}
      onSelect={(option) => handleSelect(option)}
      isItemSelected={(option) => currentIteration === option}
      renderItem={(option) => (
        <>
          <Box
            component="span"
            sx={{ minWidth: 18, display: "flex", alignItems: "center" }}
          >
            <IterationStatusIcon
              status={deriveFallbackIterationStatus(
                option,
                outputData,
                selectedTask.status,
              )}
              size={13}
            />
          </Box>
          <Box component="span">Iteration {option}</Box>
          {isIterationSummarized(option) && (
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
};
