import {
  alpha,
  Box,
  Button,
  CircularProgress,
  ClickAwayListener,
  Grid,
  IconButton,
  InputBase,
  Popper,
  Tooltip,
  Typography,
} from "@mui/material";
import { DotsThree, MagnifyingGlass, Plugs, X } from "@phosphor-icons/react";
import { useSelector } from "@xstate/react";
import { WorkflowEditContext } from "pages/definition/state";
import { buildDataForOperation } from "pages/definition/state/taskModifier/taskModifier";
import { usePerformOperationOnDefinition } from "pages/definition/state/usePerformOperationOnDefintion";
import { pluginRegistry } from "plugins/registry";
import {
  cloneElement,
  ReactElement,
  useCallback,
  useContext,
  useMemo,
} from "react";
import { NodeData } from "reaflow";
import { CommonTaskDef, SubWorkflowTaskDef, TaskType } from "types";
import useArrowNavigation from "useArrowNavigation";
import { getSequentiallySuffix } from "utils/strings";
import { getInitials } from "utils/utils";
import { ActorRef } from "xstate";
import { itemFilterMatcher } from "./helpers";
import { iconForTaskTypeMap } from "./iconsForTaskTypes";
import { useRichAddTaskMenu } from "./state/hook";
import {
  BaseTaskMenuItem,
  MainStates,
  RichAddMenuTabs,
  RichAddTaskMenuEventTypes,
} from "./state/types";
import { getALL_TASKS } from "./supportedTasks";
import {
  generateSimpleTask,
  generateSubWorkflowTask,
  taskGeneratorMap,
  uniqueTaskIdGenerator,
} from "./taskGenerator";

// Core OSS task types that always appear in the quick-add grid (in order)
const OSS_QUICK_ADD_TYPES: TaskType[] = [
  // row 1
  TaskType.SIMPLE,
  TaskType.HTTP,
  TaskType.HTTP_POLL,
  TaskType.GRPC,
  TaskType.EVENT,
  // row 2
  TaskType.SWITCH,
  TaskType.FORK_JOIN,
  TaskType.DO_WHILE,
  TaskType.SET_VARIABLE,
  TaskType.WAIT,
  // row 3
  TaskType.SUB_WORKFLOW,
  TaskType.START_WORKFLOW,
  TaskType.TERMINATE,
  TaskType.INLINE,
];

/** Placeholder in core quick-add type list → opens Add Task side panel (Integrations tab). */
const QUICK_ADD_INTEGRATIONS_PANEL = "QUICK_ADD_INTEGRATIONS_PANEL" as const;

// AI/LLM task types for the Agentic Orchestration section
const AI_QUICK_ADD_TYPES: TaskType[] = [
  TaskType.LLM_CHAT_COMPLETE,
  TaskType.LLM_GENERATE_EMBEDDINGS,
  TaskType.LLM_GET_EMBEDDINGS,
  TaskType.LLM_INDEX_DOCUMENT,
  TaskType.LLM_SEARCH_INDEX,
];

const noRandomSuffix = (aPram: string) => ({
  name: `${aPram}`,
  taskReferenceName: `${aPram}_ref`,
});

interface QuickAddMenuProps {
  anchorEl: HTMLElement | null;
  richAddTaskMenuActor: ActorRef<any>;
}

type TaskMenuItem = BaseTaskMenuItem & {
  status?: string;
  onClick?: () => void;
  icon?: ReactElement<any>;
  /** Stable React key when `type` duplicates another quick-add row (e.g. Integrations opener). */
  quickAddRowId?: string;
};

function QuickAddGridItem({ item }: { item: TaskMenuItem }) {
  return (
    <Grid size={12 / 5}>
      <Tooltip
        title={item.name}
        arrow
        placement="top"
        PopperProps={{
          modifiers: [
            {
              name: "preventOverflow",
              enabled: true,
              options: {
                altAxis: true,
                altBoundary: true,
                rootBoundary: "document",
                padding: 8,
              },
            },
          ],
        }}
      >
        <Box
          onClick={item.onClick}
          sx={{
            cursor: "pointer",
            transition: "all 0.2s ease",
          }}
        >
          <Box
            className="quick-add-icon"
            sx={{
              width: "100%",
              aspectRatio: "1",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: 2,
              backgroundColor: alpha("#F1F5F9", 0.6),
              border: "2px solid",
              borderColor: "transparent",
              color: "#64748B",
              transition: "all 0.2s ease",
              "&:hover": {
                backgroundColor: alpha("#3B82F6", 0.08),
                color: "#3B82F6",
                borderColor: alpha("#3B82F6", 0.2),
                transform: "translateY(-2px)",
              },
            }}
          >
            {item.icon}
            <Typography
              sx={{
                fontSize: "0.675rem",
                textAlign: "center",
                width: "100%",
                overflow: "hidden",
                textOverflow: "ellipsis",
                display: "-webkit-box",
                WebkitLineClamp: 2,
                WebkitBoxOrient: "vertical",
                lineHeight: 1.2,
                mt: 0.5,
              }}
            >
              {item.name}
            </Typography>
          </Box>
        </Box>
      </Tooltip>
    </Grid>
  );
}

const popperStyle = {
  width: "360px",
  boxShadow: "0px 8px 24px rgba(0, 0, 0, 0.12)",
  borderRadius: "20px",
  backgroundColor: "#FFFFFF",
  overflow: "hidden",
};

const QuickAddMenu = ({
  anchorEl,
  richAddTaskMenuActor,
}: QuickAddMenuProps) => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const { handlePerformOperation: onPerformOperation } =
    usePerformOperationOnDefinition(workflowDefinitionActor!);

  const searchQuery = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.searchQuery,
  );

  const handleSearchChange = useCallback(
    (value: string) => {
      richAddTaskMenuActor.send({
        type: RichAddTaskMenuEventTypes.TYPING,
        text: value,
      });
    },
    [richAddTaskMenuActor],
  );

  const handleClose = useCallback(() => {
    richAddTaskMenuActor.send({ type: RichAddTaskMenuEventTypes.CLOSE_MENU });
  }, [richAddTaskMenuActor]);

  const operationContext = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.operationContext,
  );

  const nodes = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.nodes,
  ) as NodeData[];

  const taskRefNames: string[] = useMemo(
    () => nodes.map((node) => node?.data?.task?.taskReferenceName),
    [nodes],
  );

  const hoveredItem = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.hoveredItem,
  );

  const setHoveredItem = (data: string) => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.SET_HOVERED_ITEM,
      data,
    });
  };

  const getSequentialTask = useCallback(
    ({
      handler,
      overrides,
    }: {
      handler: any;
      overrides?: Partial<CommonTaskDef | SubWorkflowTaskDef>;
    }) => {
      let newTask = handler({
        overrides,
        nameGenerator: noRandomSuffix,
      });

      if (Array.isArray(newTask)) {
        newTask = newTask.map((task) => {
          const sequentialName = getSequentiallySuffix({
            name: task.taskReferenceName,
            refNames: taskRefNames,
          });

          return {
            ...task,
            name: overrides?.name ? task.name : sequentialName.name,
            taskReferenceName: sequentialName.taskReferenceName,
          };
        });

        return newTask;
      }

      const sequentialName = getSequentiallySuffix({
        name: newTask.taskReferenceName,
        refNames: taskRefNames,
      });

      return {
        ...newTask,
        name: overrides?.name ? newTask?.name : sequentialName.name,
        taskReferenceName: sequentialName.taskReferenceName,
      };
    },
    [taskRefNames],
  );

  const handleAddTaskBelow = useCallback(
    (payloadGenFn: () => CommonTaskDef | CommonTaskDef[]) => () => {
      const dataForOperation = buildDataForOperation(
        operationContext?.port,
        operationContext?.node,
      );

      onPerformOperation({
        ...dataForOperation,
        operation: {
          payload: payloadGenFn(),
        },
      });
    },
    [onPerformOperation, operationContext],
  );

  const openIntegrationsAddTaskPanel = useCallback(() => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.SWITCH_TO_INTEGRATIONS,
    });
  }, [richAddTaskMenuActor]);

  const taskOptions: TaskMenuItem[] = useMemo(
    () =>
      getALL_TASKS().map((bt: BaseTaskMenuItem) => {
        const IconComponent = iconForTaskTypeMap[bt.type];
        const generatorForType = taskGeneratorMap[bt.type];

        const taskRenameMap = (name: string) => {
          switch (name) {
            case "Event Task":
              return "Publish Event";
            case "Inline Task":
              return "Javascript";
            case "LLM Chat Complete":
              return "Chat Complete";
            case "LLM Index Document":
              return "Index Document";
            case "LLM Search Index":
              return "Search Document";
            case "LLM Generate Embeddings":
              return "Generate Embeddings";
            case "LLM Get Embeddings":
              return "Search Embeddings";

            default:
              return name;
          }
        };
        return {
          category: bt.category,
          name: taskRenameMap(bt.name),
          description: bt.description,
          onClick: handleAddTaskBelow(() =>
            getSequentialTask({
              handler: generatorForType,
            }),
          ),
          type: bt.type,
          icon: <IconComponent size="24" />,
        };
      }),
    [handleAddTaskBelow, getSequentialTask],
  );

  const workerMenuItems = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.workerMenuItems ?? [],
  );

  const subWorkflowMenuItems = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.workflowMenuItems ?? [],
  );

  const workerOptions = useMemo(
    () =>
      workerMenuItems.map((baseItem: BaseTaskMenuItem) => ({
        ...baseItem,
        onClick: handleAddTaskBelow(() =>
          getSequentialTask({
            overrides: {
              name: baseItem.name,
              taskReferenceName: `${baseItem.name}_ref`,
            },
            handler: generateSimpleTask,
          }),
        ),
      })),
    [getSequentialTask, handleAddTaskBelow, workerMenuItems],
  );

  const workflowDefinitionsOptions = useMemo(
    () =>
      subWorkflowMenuItems.map((baseItem: BaseTaskMenuItem) => ({
        ...baseItem,
        onClick: handleAddTaskBelow(() =>
          getSequentialTask({
            overrides: {
              name: baseItem.name,
              taskReferenceName: `${baseItem.name}_ref`,
              subWorkflowParam: {
                name: baseItem.name,
                version: baseItem.version,
              },
            },
            handler: generateSubWorkflowTask,
          }),
        ),
      })),
    [subWorkflowMenuItems, handleAddTaskBelow, getSequentialTask],
  );

  const options = useMemo(() => {
    const showIntegrationsPanelShortcut =
      pluginRegistry.getNewIntegrationModal() != null;
    const integrationsPanelItem = showIntegrationsPanelShortcut
      ? [
          {
            name: "Connected Apps",
            description:
              "Browse integration-backed tasks in the Add Task panel.",
            category: RichAddMenuTabs.INTEGRATIONS_TAB,
            type: QUICK_ADD_INTEGRATIONS_PANEL as unknown as TaskType,
            quickAddRowId: "quick-add-integrations-panel",
            onClick: openIntegrationsAddTaskPanel,
            icon: <Plugs size={24} />,
          },
        ]
      : [];
    return [
      ...integrationsPanelItem,
      ...taskOptions,
      ...workerOptions,
      ...workflowDefinitionsOptions,
    ] as TaskMenuItem[];
  }, [
    taskOptions,
    workerOptions,
    workflowDefinitionsOptions,
    openIntegrationsAddTaskPanel,
  ]);

  const { coreQuickAddTasks, agenticQuickAddTasks } = useMemo(() => {
    const showIntegrationsPanelShortcut =
      pluginRegistry.getNewIntegrationModal() != null;

    const pluginQuickAddTypes = pluginRegistry
      .getTaskMenuItems()
      .filter((item) => item.quickAdd)
      .map((item) => item.type as TaskType)
      .filter(
        (type) => !(showIntegrationsPanelShortcut && type === TaskType.MCP),
      );

    const aiTaskTypesSet = new Set(AI_QUICK_ADD_TYPES as string[]);
    const coreTaskTypes = [
      ...OSS_QUICK_ADD_TYPES,
      ...(showIntegrationsPanelShortcut ? [QUICK_ADD_INTEGRATIONS_PANEL] : []),
      ...pluginQuickAddTypes,
    ].filter((type) =>
      type === QUICK_ADD_INTEGRATIONS_PANEL
        ? true
        : !aiTaskTypesSet.has(type as TaskType),
    );

    const coreTasks = coreTaskTypes
      .map((taskType) => {
        if (taskType === QUICK_ADD_INTEGRATIONS_PANEL) {
          const item: TaskMenuItem = {
            name: "Connected Apps",
            description:
              "Browse integration-backed tasks in the Add Task panel.",
            category: RichAddMenuTabs.INTEGRATIONS_TAB,
            type: TaskType.SIMPLE,
            quickAddRowId: "quick-add-integrations-panel",
            onClick: openIntegrationsAddTaskPanel,
            icon: <Plugs size={24} />,
          };
          return item;
        }
        return taskOptions.find((task) => task.type === taskType);
      })
      .filter((task): task is NonNullable<typeof task> => task !== undefined);

    const aiTasks = AI_QUICK_ADD_TYPES.map((taskType) =>
      taskOptions.find((task) => task.type === taskType),
    )?.filter((task): task is NonNullable<typeof task> => task !== undefined);

    return {
      coreQuickAddTasks: coreTasks.slice(0, 15),
      agenticQuickAddTasks: aiTasks,
    };
  }, [taskOptions, openIntegrationsAddTaskPanel]);

  const filteredOptions = useMemo(() => {
    if (options) {
      const filterer = itemFilterMatcher(searchQuery, RichAddMenuTabs.ALL_TAB);
      return options.filter(filterer);
    } else return [];
  }, [searchQuery, options]);

  const isFetching = useSelector(
    richAddTaskMenuActor,
    (state) =>
      state.matches(`init.main.${MainStates.FETCH_FOR_TASK_DEFINITIONS}`) ||
      state.matches(`init.main.${MainStates.FETCH_FOR_WORKFLOW_DEFINITIONS}`),
  );

  const [{ menuType }, { handleChangeMenuType }] =
    useRichAddTaskMenu(richAddTaskMenuActor);

  const handleTaskClick = (task: TaskMenuItem) => {
    if (task.category === RichAddMenuTabs.INTEGRATIONS_TAB) {
      richAddTaskMenuActor.send({
        type: RichAddTaskMenuEventTypes.SWITCH_TO_INTEGRATIONS,
      });
    } else if (task.onClick) {
      task.onClick();
    }
  };

  const { inputProps, optionPropsForItem } = useArrowNavigation({
    onSelect: (elem) => {
      if (elem?.onClick) {
        elem?.onClick();
      }
    },
    options: filteredOptions.slice(0, 3) || [],
    optionsIdGen: uniqueTaskIdGenerator,
    scrollToCenter: true,
    hoveredItem,
    setHoveredItem,
  });

  return (
    <Popper
      open={operationContext !== null && menuType === "quick"}
      placement="bottom-start"
      anchorEl={anchorEl}
      sx={popperStyle}
      {...inputProps}
      modifiers={[
        {
          name: "preventOverflow",
          options: {
            altAxis: true,
            padding: 16,
          },
        },
        {
          name: "offset",
          options: {
            offset: [0, 12],
          },
        },
      ]}
    >
      <ClickAwayListener onClickAway={handleClose}>
        <Box sx={{ backdropFilter: "blur(8px)" }}>
          {/* Search Header */}
          <Box
            sx={{
              p: 2.5,
              borderBottom: "1px solid rgba(0, 0, 0, 0.06)",
              background: "rgba(255, 255, 255, 0.95)",
            }}
          >
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                background: alpha("#F8FAFC", 0.8),
                border: "2px solid",
                borderColor: "transparent",
                borderRadius: 2.5,
                px: 2,
                py: 1.5,
                transition: "all 0.2s ease",
                "&:hover": {
                  background: alpha("#F1F5F9", 0.8),
                  borderColor: alpha("#94A3B8", 0.2),
                },
                "&:focus-within": {
                  background: "#FFFFFF",
                  borderColor: "#3B82F6",
                  boxShadow: `0 0 0 4px ${alpha("#3B82F6", 0.1)}`,
                },
              }}
            >
              <MagnifyingGlass
                size={20}
                weight="bold"
                style={{
                  color: "#64748B",
                  marginRight: "12px",
                }}
              />
              <InputBase
                placeholder="Search tasks..."
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                autoFocus
                sx={{
                  flex: 1,
                  fontSize: "0.9375rem",
                  color: "#1E293B",
                  "& input": {
                    padding: 0,
                    "&::placeholder": {
                      color: "#94A3B8",
                      opacity: 1,
                    },
                  },
                }}
                endAdornment={
                  searchQuery ? (
                    <IconButton
                      size="small"
                      onClick={() => handleSearchChange("")}
                      sx={{
                        color: "#94A3B8",
                        p: 0.5,
                        "&:hover": {
                          color: "#64748B",
                          backgroundColor: "transparent",
                        },
                      }}
                    >
                      <X size={16} weight="bold" />
                    </IconButton>
                  ) : null
                }
              />
            </Box>
          </Box>

          {/* Quick Add Section */}
          {!searchQuery ? (
            <Box sx={{ p: 2.5, background: "#FFFFFF" }}>
              <Box
                sx={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  mb: 2,
                }}
              >
                <Typography
                  sx={{
                    fontSize: "0.8125rem",
                    fontWeight: 600,
                    color: "#1E293B",
                    letterSpacing: "0.025em",
                  }}
                >
                  QUICK ADD
                </Typography>
                <Button
                  variant="text"
                  size="small"
                  onClick={() => handleChangeMenuType("advanced")}
                  sx={{
                    color: "#3B82F6",
                    fontSize: "0.8125rem",
                    gap: 0.75,
                    fontWeight: 500,
                    "&:hover": {
                      backgroundColor: alpha("#3B82F6", 0.04),
                    },
                  }}
                  startIcon={<DotsThree size={18} weight="bold" />}
                >
                  More tasks
                </Button>
              </Box>

              {/* Core quick add: 5 columns × 3 rows = 15 cells max */}
              <Grid container spacing={1.5} sx={{ width: "100%" }}>
                {coreQuickAddTasks.map((item) => (
                  <QuickAddGridItem
                    key={item.quickAddRowId ?? String(item.type)}
                    item={item}
                  />
                ))}
              </Grid>

              {agenticQuickAddTasks.length > 0 ? (
                <Box sx={{ mt: 2.5 }}>
                  <Box
                    sx={{
                      position: "relative",
                      width: "100%",
                      textAlign: "center",
                      borderBottom: "2px dotted",
                      borderColor: "rgba(226, 232, 240, 0.8)",
                      height: 10,
                      mb: 2,
                    }}
                  >
                    <Typography
                      sx={{
                        position: "absolute",
                        top: "50%",
                        left: "50%",
                        transform: "translate(-50%, -50%)",
                        background: "#FFFFFF",
                        fontSize: "0.6875rem",
                        fontWeight: 600,
                        letterSpacing: "0.05em",
                        color: "#64748B",
                        textTransform: "uppercase",
                        whiteSpace: "nowrap",
                        px: 1,
                      }}
                    >
                      Agentic Orchestration
                    </Typography>
                  </Box>
                  <Grid container spacing={1.5} sx={{ width: "100%" }}>
                    {agenticQuickAddTasks.map((item) => (
                      <QuickAddGridItem key={item.type} item={item} />
                    ))}
                  </Grid>
                </Box>
              ) : null}
            </Box>
          ) : (
            // Search Results Section
            <Box
              sx={{
                p: 2.5,
                borderTop: "1px solid rgba(0, 0, 0, 0.06)",
                background: "#FFFFFF",
              }}
            >
              <Box
                sx={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  mb: 2,
                  minHeight: 25,
                }}
              >
                <Typography
                  sx={{
                    fontSize: "0.75rem",
                    fontWeight: 600,
                    color: "#64748B",
                    letterSpacing: "0.05em",
                  }}
                >
                  SEARCH RESULTS
                </Typography>

                {filteredOptions.length > 3 && (
                  <Box
                    onClick={() => handleChangeMenuType("advanced")}
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      gap: 0.75,
                      px: 1.5,
                      py: 0.75,
                      borderRadius: 1.5,
                      backgroundColor: alpha("#F1F5F9", 0.6),
                      color: "#64748B",
                      fontSize: "0.75rem",
                      fontWeight: 500,
                      cursor: "pointer",
                      transition: "all 0.2s ease",
                      "&:hover": {
                        backgroundColor: alpha("#3B82F6", 0.08),
                        color: "#3B82F6",
                      },
                    }}
                  >
                    <Typography
                      sx={{ fontSize: "inherit", fontWeight: "inherit" }}
                    >
                      {isFetching ? (
                        <CircularProgress size={13} sx={{ color: "#3B82F6" }} />
                      ) : (
                        `+${filteredOptions.length - 3} more`
                      )}
                    </Typography>
                  </Box>
                )}
              </Box>
              <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
                {isFetching && filteredOptions.length === 0 ? (
                  <Box
                    sx={{
                      display: "flex",
                      justifyContent: "center",
                      alignItems: "center",
                      py: 4,
                    }}
                  >
                    <CircularProgress size={24} sx={{ color: "#3B82F6" }} />
                  </Box>
                ) : filteredOptions.length === 0 ? (
                  <Box
                    sx={{
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      gap: 1.5,
                      py: 4,
                      color: "#64748B",
                    }}
                  >
                    <MagnifyingGlass size={24} />
                    <Typography sx={{ fontSize: "0.855rem" }}>
                      No tasks found
                    </Typography>
                  </Box>
                ) : (
                  <>
                    {filteredOptions.slice(0, 3).map((item, index) => (
                      <Box
                        key={`option-${index}-${item.name}-${item.category}${
                          item.version ? item.version : ""
                        }`}
                        {...optionPropsForItem(item)}
                        onClick={() => handleTaskClick(item)}
                        sx={{
                          p: 2,
                          borderRadius: 2,
                          cursor: "pointer",
                          backgroundColor:
                            uniqueTaskIdGenerator(item) === hoveredItem
                              ? alpha("#F1F5F9", 0.6)
                              : "#FFFFFF",
                          border: "2px solid",
                          borderColor:
                            uniqueTaskIdGenerator(item) === hoveredItem
                              ? alpha("#3B82F6", 0.2)
                              : "transparent",
                          transition: "all 0.2s ease",
                          "&:hover": {
                            backgroundColor: alpha("#F1F5F9", 0.6),
                            borderColor: alpha("#3B82F6", 0.2),
                            transform: "translateY(-1px)",
                            "& .task-icon-box": {
                              backgroundColor: alpha("#3B82F6", 0.08),
                            },
                          },
                        }}
                      >
                        <Box
                          sx={{
                            display: "flex",
                            alignItems: "flex-start",
                            gap: 2,
                          }}
                        >
                          <Box
                            className="task-icon-box"
                            sx={{
                              width: 40,
                              height: 40,
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "center",
                              borderRadius: 1.5,
                              backgroundColor:
                                uniqueTaskIdGenerator(item) === hoveredItem
                                  ? alpha("#3B82F6", 0.08)
                                  : alpha("#F1F5F9", 0.6),
                              color: "#64748B",
                              flexShrink: 0,
                              transition: "all 0.2s ease",
                            }}
                          >
                            {item?.icon ? (
                              cloneElement(item.icon, {
                                color:
                                  uniqueTaskIdGenerator(item) === hoveredItem
                                    ? "#3B82F6"
                                    : "#64748B",
                              })
                            ) : (
                              <Typography
                                sx={{
                                  fontSize: "0.875rem",
                                  fontWeight: 500,
                                  color:
                                    uniqueTaskIdGenerator(item) === hoveredItem
                                      ? "#3B82F6"
                                      : "#64748B",
                                }}
                              >
                                {getInitials(item.name)}
                              </Typography>
                            )}
                          </Box>
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography
                              sx={{
                                fontSize: "0.8125rem",
                                fontWeight: 500,
                                color: "#1E293B",
                                overflowWrap: "break-word",
                              }}
                            >
                              {item.name}
                            </Typography>
                            <Typography
                              sx={{
                                fontSize: "0.75rem",
                                color:
                                  uniqueTaskIdGenerator(item) === hoveredItem
                                    ? "#1E293B"
                                    : "#64748B",
                                lineHeight: 1.4,
                                overflowWrap: "break-word",
                              }}
                            >
                              {item.description}
                            </Typography>
                          </Box>
                        </Box>
                      </Box>
                    ))}
                    {isFetching && filteredOptions.length < 3 && (
                      <Box
                        sx={{
                          display: "flex",
                          justifyContent: "center",
                          alignItems: "center",
                          py: 4,
                        }}
                      >
                        <CircularProgress size={24} sx={{ color: "#3B82F6" }} />
                      </Box>
                    )}
                  </>
                )}
              </Box>
            </Box>
          )}
        </Box>
      </ClickAwayListener>
    </Popper>
  );
};

export default QuickAddMenu;
