/**
 * Core (OSS) sidebar menu items for Conductor UI.
 *
 * These items are merged with plugin-registered items in UiSidebar.
 * - Executions submenu (Workflow, Queue Monitor)
 * - Run Workflow button
 * - Definitions submenu (Workflow, Task, Event Handler)
 * - Help menu
 * - API Docs
 */

import CodeIcon from "@mui/icons-material/Code";
import PlayIcon from "@mui/icons-material/PlayArrowOutlined";
import PlaylistPlayIcon from "@mui/icons-material/PlaylistPlay";
import SupportIcon from "@mui/icons-material/Support";
import WebhookOutlinedIcon from "@mui/icons-material/WebhookOutlined";
import RunWorkflowButton from "components/providers/sidebar/RunWorkflowButton";
import { MenuItemType } from "components/providers/sidebar/types";
import { FEATURES, featureFlags } from "utils";
import {
  EVENT_HANDLERS_URL,
  NEW_TASK_DEF_URL,
  RUN_WORKFLOW_URL,
  TASK_DEF_URL,
  TASK_QUEUE_URL,
  WORKFLOW_DEFINITION_URL,
  WORKFLOW_EXECUTION_URL,
} from "utils/constants/route";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
const hideFeedbackForm = !featureFlags.isEnabled(FEATURES.SHOW_FEEDBACK_FORM);

/**
 * Core sidebar position constants. Root and submenus both use 100, 200, 300, ...
 * so plugins can inject items in between (e.g. position 150 between 100 and 200).
 * Export for orkes-conductor-ui to reference when registering sidebar items.
 */
const CORE_SIDEBAR_POSITIONS = {
  // Root level (top-level menu items)
  ROOT: {
    executionsSubMenu: 100,
    runWorkflow: 200,
    definitionsSubMenu: 300,
    helpMenu: 400,
    swaggerItem: 500,
  },
  // Executions submenu children
  EXECUTIONS: {
    workflowExeItem: 100,
    queueMonitorItem: 200,
  },
  // Definitions submenu children
  DEFINITIONS: {
    workflowDefItem: 100,
    taskDefItem: 200,
    eventHandlerDefItem: 300,
  },
  // Help submenu children
  HELP: {
    docsItem: 100,
    requestsItem: 200,
    supportItem: 300,
  },
} as const;

/**
 * Returns the core OSS sidebar menu items. Accepts `open` for the Run Workflow
 * button component which depends on sidebar open state.
 * Each item has a numeric position so plugins can inject between (e.g. 150 between 100 and 200).
 */
export function getCoreSidebarItems(open: boolean): MenuItemType[] {
  const R = CORE_SIDEBAR_POSITIONS.ROOT;
  const E = CORE_SIDEBAR_POSITIONS.EXECUTIONS;
  const D = CORE_SIDEBAR_POSITIONS.DEFINITIONS;
  const H = CORE_SIDEBAR_POSITIONS.HELP;

  return [
    // Executions submenu - core items only
    {
      id: "executionsSubMenu",
      title: "Executions",
      icon: <PlaylistPlayIcon />,
      linkTo: "",
      shortcuts: [],
      hotkeys: "",
      hidden: false,
      position: R.executionsSubMenu,
      items: [
        {
          id: "workflowExeItem",
          title: "Workflow",
          icon: null,
          linkTo: "/executions",
          activeRoutes: [WORKFLOW_EXECUTION_URL.WF_ID_TASK_ID],
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: E.workflowExeItem,
        },
        {
          id: "queueMonitorItem",
          title: "Queue Monitor",
          icon: null,
          linkTo: TASK_QUEUE_URL.BASE,
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: E.queueMonitorItem,
        },
      ],
    },
    // Run Workflow button
    {
      id: "runWorkflow",
      title: "Run Workflow",
      icon: <PlayIcon />,
      linkTo: RUN_WORKFLOW_URL,
      shortcuts: [],
      hidden: true,
      position: R.runWorkflow,
      component: <RunWorkflowButton open={open} />,
    },
    // Definitions submenu - core items only
    {
      id: "definitionsSubMenu",
      title: "Definitions",
      icon: <CodeIcon />,
      linkTo: "",
      shortcuts: [],
      hotkeys: "",
      hidden: false,
      position: R.definitionsSubMenu,
      items: [
        {
          id: "workflowDefItem",
          title: "Workflow",
          icon: null,
          linkTo: WORKFLOW_DEFINITION_URL.BASE,
          activeRoutes: [
            WORKFLOW_DEFINITION_URL.NEW,
            WORKFLOW_DEFINITION_URL.NAME_VERSION,
          ],
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: D.workflowDefItem,
        },
        {
          id: "taskDefItem",
          title: "Task",
          icon: null,
          linkTo: TASK_DEF_URL.BASE,
          activeRoutes: [NEW_TASK_DEF_URL, TASK_DEF_URL.NAME],
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: D.taskDefItem,
        },
        {
          id: "eventHandlerDefItem",
          title: "Event Handler",
          icon: null,
          linkTo: EVENT_HANDLERS_URL.BASE,
          activeRoutes: [EVENT_HANDLERS_URL.NEW, EVENT_HANDLERS_URL.NAME],
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: D.eventHandlerDefItem,
        },
      ],
    },
    // Help menu
    {
      id: "helpMenu",
      title: "Help",
      icon: <SupportIcon />,
      linkTo: "",
      shortcuts: [],
      hotkeys: "",
      hidden: false,
      position: R.helpMenu,
      items: [
        {
          id: "docsItem",
          title: "Docs",
          icon: null,
          linkTo: "https://orkes.io/content/",
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: H.docsItem,
          isOpenNewTab: true,
        },
        {
          id: "requestsItem",
          title: "Requests",
          icon: null,
          linkTo:
            "https://orkes.io/orkes-cloud-free-trial?utm_source=playground",
          shortcuts: [],
          hotkeys: "",
          hidden: hideFeedbackForm,
          position: H.requestsItem,
          isOpenNewTab: true,
        },
        {
          id: "supportItem",
          title: "Support",
          icon: null,
          linkTo: isPlayground
            ? "https://community.orkes.io/ "
            : "https://orkeshelp.zendesk.com/hc/en-us/requests/new",
          shortcuts: [],
          hotkeys: "",
          hidden: false,
          position: H.supportItem,
          isOpenNewTab: true,
        },
      ],
    },
    // API Docs
    {
      id: "swaggerItem",
      title: "API Docs",
      icon: <WebhookOutlinedIcon />,
      linkTo: "/api-reference",
      shortcuts: [],
      hotkeys: "",
      hidden: false,
      position: R.swaggerItem,
    },
  ];
}
