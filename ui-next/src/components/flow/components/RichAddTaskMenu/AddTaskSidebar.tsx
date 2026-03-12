import {
  alpha,
  Box,
  Button,
  CircularProgress,
  Grid,
  IconButton,
  InputBase,
  Typography,
} from "@mui/material";
import {
  ArrowRight,
  Cpu,
  Gear,
  GridFour as GridLines,
  MagnifyingGlass,
  Plus,
  Robot,
  Users,
  X,
} from "@phosphor-icons/react";
import { IntegrationIcon } from "components/IntegrationIcon";
import { MessageContext } from "components/v1/layout/MessageContext";
import { WorkflowEditContext } from "pages/definition/state";
import { buildDataForOperation } from "pages/definition/state/taskModifier/taskModifier";
import { DefinitionMachineEventTypes } from "pages/definition/state/types";
import { usePerformOperationOnDefinition } from "pages/definition/state/usePerformOperationOnDefintion";
import { pluginRegistry } from "plugins/registry";
import React, {
  cloneElement,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  BaseIntegration,
  CommonTaskDef,
  IntegrationDef,
  SubWorkflowTaskDef,
} from "types";
import { getSequentiallySuffix } from "utils/strings";
import { getInitials } from "utils/utils";
import { ActorRef } from "xstate";
import { itemFilterMatcher } from "./helpers";
import { iconForTaskTypeMap } from "./iconsForTaskTypes";
import { IntegrationDrillDownContent } from "./IntegrationDrillDownContent";
import { useRichAddTaskMenu } from "./state/hook";
import {
  BaseTaskMenuItem,
  IntegrationMenuItem,
  TaskMenuItem as OriginalTaskMenuItem,
  RichAddMenuTabs,
  RichAddTaskMenuEvents,
  RichAddTaskMenuEventTypes,
} from "./state/types";
import { getALL_TASKS } from "./supportedTasks";
import {
  generateMCPTask,
  generateSimpleTask,
  generateSubWorkflowTask,
  NameGeneratorFn,
  taskGeneratorMap,
} from "./taskGenerator";

// Extend the TaskMenuItem type to include status and onClick
type TaskMenuItem = Omit<OriginalTaskMenuItem, "onClick" | "icon"> & {
  status?: string;
  onClick?: () => void;
  icon?: React.ReactElement;
};
type AddTaskSidebarProps = {
  open: boolean;
  setOpen?: (val: boolean) => void;
  richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>;
};

const noRandomSuffix: NameGeneratorFn = (aPram: string) => ({
  name: `${aPram}`,
  taskReferenceName: `${aPram}_ref`,
});

const SIDEBAR_ITEMS = [
  {
    label: "All",
    tab: RichAddMenuTabs.ALL_TAB,
    icon: GridLines,
  },
  {
    label: "System",
    tab: RichAddMenuTabs.SYSTEMS_TAB,
    icon: Cpu,
  },
  {
    label: "AI",
    tab: RichAddMenuTabs.AI_AGENTS_TAB,
    icon: Robot,
  },
  {
    label: "Worker Tasks",
    tab: RichAddMenuTabs.WORKERS_TAB,
    icon: Users,
  },
  {
    label: "Integrations",
    tab: RichAddMenuTabs.INTEGRATIONS_TAB,
    icon: Gear,
  },
];

const AddTaskSidebar = ({
  open,
  setOpen,
  richAddTaskMenuActor,
}: AddTaskSidebarProps) => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const { setMessage } = useContext(MessageContext);
  const listRef = useRef<HTMLDivElement>(null);
  const { handlePerformOperation: onPerformOperation } =
    usePerformOperationOnDefinition(workflowDefinitionActor!);

  const [
    {
      supportedIntegrations,
      integrationDefs,
      integrationDrillDownMenu,
      scrollPosition,
      operationContext,
      nodes,
      workerMenuItems,
      subWorkflowMenuItems,
      selectedTab,
      isFetching,
      searchQuery,
    },
    { refetchIntegrations, handleUpdateIntegrationDrillDown, handleTyping },
  ] = useRichAddTaskMenu(richAddTaskMenuActor);

  const send = richAddTaskMenuActor?.send;

  const [basicIntegrationTemplate, _setBasicIntegrationTemplate] = useState<
    BaseIntegration | undefined
  >(undefined);

  const setBasicIntegrationTemplate = useCallback(
    (template?: BaseIntegration) => {
      if (!template) {
        _setBasicIntegrationTemplate(undefined);
        return;
      }
      const templateNameWithoutSpaces = {
        ...template,
        name: template.name.replace(/\s+/g, ""),
      };
      _setBasicIntegrationTemplate(templateNameWithoutSpaces);
    },
    [_setBasicIntegrationTemplate],
  );

  const taskRefNames: string[] = useMemo(
    () => nodes.map((node) => node?.data?.task?.taskReferenceName),
    [nodes],
  );

  const handleEditModelClose = () => {
    setBasicIntegrationTemplate(undefined);
  };

  const handleIntegrationSave = () => {
    setMessage({
      text: "Integration created successfully",
      severity: "success",
    });
    setBasicIntegrationTemplate(undefined);
    refetchIntegrations();
  };

  // Add scroll handler
  const handleScroll = (event: any) => {
    const x = event.currentTarget.scrollTop + event.currentTarget.clientHeight;
    if (
      event.currentTarget.scrollHeight - x <= 1 &&
      selectedTab === RichAddMenuTabs.ALL_TAB
    ) {
      send({
        type: RichAddTaskMenuEventTypes.GOT_TO_END,
        lastScrollTopPosition: event.currentTarget.scrollTop,
      });
    }
  };

  // Add effect to restore scroll position
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = scrollPosition ?? 0;
    }
  }, [scrollPosition]);

  type TaskGeneratorFn = () => CommonTaskDef | CommonTaskDef[];

  const handleAddTaskBelow = useCallback(
    (payloadGenFn: TaskGeneratorFn) => () => {
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

  const taskOptions = getALL_TASKS().map((bt: BaseTaskMenuItem) => {
    const IconComponent = iconForTaskTypeMap[bt.type];
    const generatorForType = taskGeneratorMap[bt.type];

    return {
      category: bt.category,
      name: bt.name,
      description: bt.description,
      onClick: handleAddTaskBelow(() =>
        getSequentialTask({
          handler: generatorForType,
        }),
      ),
      icon: <IconComponent size="24" />,
    };
  });

  const handleChangeTab = (data: RichAddMenuTabs) => {
    handleCloseDrillDown();
    send({
      type: RichAddTaskMenuEventTypes.SET_SELECTED_TAB,
      tab: data,
    });
  };

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

  const options: TaskMenuItem[] = useMemo(
    () =>
      [
        ...taskOptions,
        ...workerOptions,
        ...workflowDefinitionsOptions,
        ...supportedIntegrations,
      ] as TaskMenuItem[],
    [
      taskOptions,
      workerOptions,
      workflowDefinitionsOptions,
      supportedIntegrations,
    ],
  );

  const filteredOptions = useMemo(() => {
    if (options) {
      const filterer = itemFilterMatcher(searchQuery, selectedTab);
      return options.filter(filterer);
    } else return [];
  }, [selectedTab, searchQuery, options]);

  // Add a function to generate unique task ID
  const getTaskUniqueId = (task: TaskMenuItem) =>
    `${task.name}_${task.category}_${task.description}`;

  const handleTaskClick = (task: TaskMenuItem) => {
    if (
      task.category === RichAddMenuTabs.INTEGRATIONS_TAB &&
      task.status === "active"
    ) {
      handleTyping("");
      handleUpdateIntegrationDrillDown({
        isOpen: true,
        selectedRootIntegration: task as IntegrationMenuItem,
        level: "integrations",
        selectedIntegration: null,
      });
      // handleFetchIntegrationTools(task as IntegrationMenuItem);
    } else if (
      task.category === RichAddMenuTabs.INTEGRATIONS_TAB &&
      task.status !== "active"
    ) {
      const template = integrationDefs.find(
        (integration: IntegrationDef) => integration.name === task?.name,
      );
      setBasicIntegrationTemplate({
        name: template?.name,
        description: "",
        type: template?.type,
        category: template?.category,
        enabled: template?.enabled,
      });
    } else if (task.onClick) {
      task.onClick();
    }
  };

  const handleCloseMenu = useCallback(() => {
    send({ type: RichAddTaskMenuEventTypes.CLOSE_MENU });
  }, [send]);

  const handleClose = useCallback(() => {
    if (setOpen) {
      setOpen(false);
    }
    if (workflowDefinitionActor) {
      workflowDefinitionActor.send({
        type: DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED,
        onSelectNode: false,
      });
    }
    handleCloseMenu();
  }, [handleCloseMenu, setOpen, workflowDefinitionActor]);

  const handleAddToolTask = (tool: any) => {
    return handleAddTaskBelow(() =>
      getSequentialTask({
        overrides: {
          name: tool?.api,
          taskReferenceName: `${tool?.api}_ref`,
          description: tool?.description,
          inputParameters: {
            integrationName:
              integrationDrillDownMenu?.selectedIntegration?.name,
            method: tool?.api,
            integrationType:
              integrationDrillDownMenu?.selectedIntegration?.integrationType,
            // ...generateObjectFromSchema(tool?.inputSchema?.data),
          },
        },
        handler: generateMCPTask,
      }),
    )();
  };

  const handleCloseDrillDown = () => {
    handleUpdateIntegrationDrillDown({
      isOpen: false,
      selectedIntegration: null,
      selectedRootIntegration: null,
      level: "integrations",
    });
  };

  return open ? (
    <Box
      sx={{
        width: 450,
        flexShrink: 0,
        height: "100%",
        boxShadow: [
          "-5px 0px 20px rgba(0, 0, 0, .8)",
          "0px 0px 6px rgba(0, 0, 0, 0.18)",
        ],
        background: "#FFFFFF",
        position: "relative",
        zIndex: 1,
      }}
    >
      <Box
        sx={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          background: "#FFFFFF",
          border: "1px solid #F0F0F0",
        }}
      >
        {/* Header */}
        <Box
          sx={{
            p: 2.5,
            borderBottom: "1px solid #F0F0F0",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            background: "#FFFFFF",
          }}
        >
          <Typography
            variant="h6"
            sx={{
              fontSize: "1.125rem",
              fontWeight: 600,
              color: "#111827",
            }}
          >
            Add Task
          </Typography>
          <Box
            component={X}
            sx={{
              cursor: "pointer",
              color: "#6B7280",
              p: 1,
              borderRadius: 1,
              transition: "all 0.2s ease",
              "&:hover": {
                color: "#111827",
                backgroundColor: "#F3F4F6",
              },
            }}
            onClick={handleClose}
            size={20}
          />
        </Box>

        {/* Search */}
        <Box sx={{ p: 2.5, borderBottom: "1px solid #F0F0F0" }}>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              background: "#F9FAFB",
              border: "1px solid #F0F0F0",
              borderRadius: 1.5,
              px: 2,
              py: 1.25,
              "&:hover": {
                background: "#F3F4F6",
                borderColor: "#E5E7EB",
              },
              "&:focus-within": {
                background: "#FFFFFF",
                borderColor: "#2563EB",
                boxShadow: "0 0 0 2px rgba(37, 99, 235, 0.1)",
              },
            }}
          >
            <Box
              component="span"
              sx={{
                display: "flex",
                alignItems: "center",
                color: "#6B7280",
                mr: 1.5,
              }}
            >
              🔍
            </Box>
            <InputBase
              placeholder="Search tasks..."
              value={searchQuery}
              onChange={(e) => handleTyping(e.target.value)}
              sx={{
                flex: 1,
                fontSize: "0.875rem",
                "& input": {
                  padding: 0,
                },
              }}
              endAdornment={
                searchQuery ? (
                  <IconButton
                    size="small"
                    onClick={() => handleTyping("")}
                    sx={{
                      color: "#6B7280",
                      p: 0.5,
                      "&:hover": {
                        color: "#4B5563",
                        backgroundColor: "transparent",
                      },
                    }}
                  >
                    <X size={16} weight="bold" />
                  </IconButton>
                ) : null
              }
            />
            {!integrationDrillDownMenu.isOpen && (
              <Typography
                sx={{
                  fontSize: "0.75rem",
                  color: "#6B7280",
                  ml: 1.5,
                  userSelect: "none",
                }}
              >
                {filteredOptions.length} results
              </Typography>
            )}
          </Box>
        </Box>

        {/* Categories */}
        <Box sx={{ px: 2.5, py: 1.5, borderBottom: "1px solid #F0F0F0" }}>
          <Grid container sx={{ width: "100%" }} spacing={0.75}>
            {SIDEBAR_ITEMS.map((item) => (
              <Grid key={item.label} size={2.4}>
                <Box
                  onClick={() => handleChangeTab(item.tab)}
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: 0.75,
                    py: 1,
                    px: 0.5,
                    cursor: "pointer",
                    borderRadius: "6px",
                    backgroundColor:
                      selectedTab === item.tab ? "#F3F4F6" : "transparent",
                    transition: "all 0.2s ease",
                    border: "1px solid",
                    borderColor:
                      selectedTab === item.tab ? "#E5E7EB" : "transparent",
                    "&:hover": {
                      backgroundColor:
                        selectedTab === item.tab ? "#F3F4F6" : "#F9FAFB",
                      borderColor: "#E5E7EB",
                    },
                  }}
                >
                  <Box
                    component={item.icon}
                    sx={{
                      width: 18,
                      height: 18,
                      color: selectedTab === item.tab ? "#111827" : "#6B7280",
                      transition: "color 0.2s ease",
                    }}
                  />
                  <Typography
                    sx={{
                      fontSize: "0.6875rem",
                      fontWeight: selectedTab === item.tab ? 600 : 500,
                      color: selectedTab === item.tab ? "#111827" : "#6B7280",
                      textAlign: "center",
                      transition: "all 0.2s ease",
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      width: "100%",
                    }}
                  >
                    {item.label}
                  </Typography>
                </Box>
              </Grid>
            ))}
          </Grid>
        </Box>

        {/* Task List */}
        <Box
          sx={{ flex: 1, overflow: "auto", p: 2.5 }}
          onScroll={handleScroll}
          ref={listRef}
        >
          {isFetching ? (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                height: "100%",
              }}
            >
              <CircularProgress
                size={24}
                sx={{
                  color: "#2563EB",
                }}
              />
            </Box>
          ) : !integrationDrillDownMenu.isOpen &&
            filteredOptions.length === 0 ? (
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                height: "100%",
                gap: 1.5,
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
              <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
                {!integrationDrillDownMenu.isOpen &&
                  filteredOptions.map(
                    (task: TaskMenuItem | IntegrationMenuItem, idx: number) => (
                      <Box key={getTaskUniqueId(task) + idx}>
                        <Box
                          onClick={() => handleTaskClick(task)}
                          sx={{
                            background: "#FFFFFF",
                            border: "1px solid #F0F0F0",
                            borderRadius: 2,
                            p: 1.5,
                            cursor: "pointer",
                            transition: "all 0.2s ease",
                            boxShadow: "0 1px 2px rgba(0, 0, 0, 0.05)",
                            "&:hover": {
                              backgroundColor: "#F9FAFB",
                              borderColor: "#E5E7EB",
                              transform: "translateY(-1px)",
                              boxShadow:
                                "0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)",
                              "& .task-icon-box": {
                                backgroundColor: alpha("#3B82F6", 0.08),
                                color: "#3B82F6",
                                "& .task-icon-typography": {
                                  color: "#3B82F6",
                                },
                              },
                              "& .task-description": {
                                color: "#1E293B",
                              },
                            },
                          }}
                        >
                          <Box
                            sx={{
                              display: "flex",
                              alignItems:
                                task.category !==
                                RichAddMenuTabs.INTEGRATIONS_TAB
                                  ? "flex-start"
                                  : "center",
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
                                background: "#F9FAFB",
                                borderRadius: 1.5,
                                mr: 2,
                                color: "#6B7280",
                                transition: "all 0.2s ease",
                                flexShrink: 0,
                              }}
                            >
                              {cloneElement(
                                "iconName" in task &&
                                  (task.iconName || task?.integrationType) ? (
                                  <div
                                    style={{
                                      width: "20px",
                                      height: "20px",
                                      display: "flex",
                                      alignItems: "center",
                                      justifyContent: "center",
                                    }}
                                  >
                                    <IntegrationIcon
                                      integrationName={
                                        task.iconName ?? task?.integrationType
                                      }
                                    />
                                  </div>
                                ) : "icon" in task && task.icon ? (
                                  task.icon
                                ) : (
                                  <Typography
                                    className="task-icon-typography"
                                    sx={{
                                      fontSize: "0.875rem",
                                      fontWeight: 500,
                                      color: "#64748B",
                                    }}
                                  >
                                    {getInitials(task.name)}
                                  </Typography>
                                ),
                                {
                                  size: 20,
                                },
                              )}
                            </Box>
                            <Box sx={{ flex: 1, minWidth: 0 }}>
                              <Typography
                                sx={{
                                  fontWeight: 600,
                                  fontSize: "0.8125rem",
                                  color: "#111827",
                                  overflowWrap: "break-word",
                                }}
                              >
                                {task.name}
                              </Typography>
                              {task.category !==
                                RichAddMenuTabs.INTEGRATIONS_TAB && (
                                <Typography
                                  className="task-description"
                                  sx={{
                                    color: "#6B7280",
                                    fontSize: "0.75rem",

                                    overflowWrap: "break-word",
                                  }}
                                >
                                  {task.description}
                                </Typography>
                              )}
                            </Box>
                            {task.category ===
                              RichAddMenuTabs.INTEGRATIONS_TAB &&
                              ((task as any)?.status === "active" ? (
                                <ArrowRight size={14} color="#6B7280" />
                              ) : (
                                <Button
                                  variant="outlined"
                                  size="small"
                                  sx={{
                                    fontWeight: "normal",
                                    color: "#D97706",
                                    borderColor: "#D97706",
                                    "&:hover": {
                                      borderColor: "#B45309",
                                      backgroundColor:
                                        "rgba(217, 119, 6, 0.04)",
                                    },
                                  }}
                                  onClick={() => {}}
                                  startIcon={<Plus size={14} weight="bold" />}
                                >
                                  Add New
                                </Button>
                              ))}
                          </Box>
                        </Box>
                      </Box>
                    ),
                  )}
              </Box>
              {integrationDrillDownMenu.isOpen &&
                integrationDrillDownMenu.selectedRootIntegration && (
                  <IntegrationDrillDownContent
                    richAddTaskMenuActor={richAddTaskMenuActor}
                    onAddToolTask={handleAddToolTask}
                    onAddNewIntegration={(template) => {
                      setBasicIntegrationTemplate({
                        name: template?.name,
                        description: "",
                        type: template?.type,
                        category: template?.category,
                        enabled: template?.enabled,
                      });
                    }}
                  />
                )}
            </>
          )}
        </Box>
      </Box>
      {basicIntegrationTemplate &&
        (() => {
          const IntegrationEditModal = pluginRegistry.getNewIntegrationModal();
          return IntegrationEditModal ? (
            <IntegrationEditModal
              integrationDefList={integrationDefs ?? []}
              integrationToEdit={basicIntegrationTemplate}
              onClose={handleEditModelClose}
              onAfterSave={handleIntegrationSave}
              nameEditable={true}
              isNewIntegration={true}
            />
          ) : null;
        })()}
    </Box>
  ) : null;
};

export default AddTaskSidebar;
