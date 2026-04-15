import { Grid } from "@mui/material";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Dropdown from "components/ui/inputs/Dropdown";
import MuiButton from "components/ui/buttons/MuiButton";
import MuiCheckbox from "components/ui/MuiCheckbox";
import MuiTypography from "components/ui/MuiTypography";
import { RoundedInput } from "components/ui/inputs/RoundedInput";
import { identity as _identity } from "lodash/fp";
import { FunctionComponent, useState } from "react";
import { FEATURES, featureFlags, tryToJson } from "utils";
import { useLocalStorage } from "utils/localstorage";

type FeatureFlagValue = string | boolean | null;

type BaseFeatureFlag = {
  name: string;
  label: string;
  contextValue: string;
};

type StringFeatureFlag = BaseFeatureFlag & {
  type: "string";
  value: string | null;
  setValue: (value: string | null) => void;
};

type BooleanFeatureFlag = BaseFeatureFlag & {
  type: "boolean";
  value: boolean | null;
  setValue: (value: boolean | null) => void;
};

type _FeatureFlagsType = StringFeatureFlag | BooleanFeatureFlag;

const dropdownBorder = (value: FeatureFlagValue): string => {
  if (value === null) {
    return "2px solid default";
  } else if (value) {
    return "2px solid forestgreen";
  } else {
    return "2px solid red";
  }
};

type InputStringTemplateProps = StringFeatureFlag & {
  handleStringChange: (
    value: string | undefined,
    setStateCb: (value: string | null) => void,
  ) => void;
};

const InputStringTemplate = ({
  label,
  value,
  setValue,
  type: _type,
  contextValue,
  handleStringChange,
}: InputStringTemplateProps) => {
  const [newValue, setNewValue] = useState<string>(value || "");
  const handleNewValue = (val: string) => {
    setNewValue(val);
  };
  return (
    <Grid
      container
      spacing={2}
      p={1}
      alignItems="top"
      wrap="nowrap"
      sx={{
        width: "100%",
        borderBottom: "1px solid lightgray",
        padding: "15px 0",
      }}
    >
      <Grid size={4}>
        <Box>{label}</Box>
      </Grid>
      <Grid size={4}>
        <Box>
          <RoundedInput
            value={newValue}
            onChange={(value: string) => handleNewValue(value)}
          />

          <Box
            sx={{
              padding: "5px 0",
              display: "flex",
              flexWrap: "wrap",
              gap: "10px",
            }}
          >
            <MuiButton
              size={"small"}
              onClick={() => handleStringChange(newValue, setValue)}
            >
              Store
            </MuiButton>
            <MuiButton
              size={"small"}
              onClick={() => handleStringChange(undefined, setValue)}
            >
              Remove
            </MuiButton>
          </Box>
        </Box>
      </Grid>
      <Grid size={4}>
        <Box>
          <RoundedInput disabled value={contextValue} />
        </Box>
      </Grid>
    </Grid>
  );
};

type InputCheckboxTemplateProps = BooleanFeatureFlag & {
  handleChangeDropdown: (
    value: unknown,
    setStateCb: (value: boolean | null) => void,
  ) => void;
};

const InputCheckboxTemplate = ({
  label,
  value,
  setValue,
  contextValue,
  handleChangeDropdown,
}: InputCheckboxTemplateProps) => {
  return (
    <Grid
      container
      spacing={2}
      p={1}
      alignItems={"center"}
      wrap={"nowrap"}
      sx={{ borderBottom: "1px solid lightgray" }}
    >
      <Grid size={4}>
        <Box>{label}</Box>
      </Grid>
      <Grid size={4}>
        <Box sx={{ display: "flex", gap: 4 }}>
          <Dropdown
            sx={{
              ".MuiOutlinedInput-notchedOutline": {
                border: dropdownBorder(value),
              },
            }}
            label=""
            value={value === null ? "empty" : value ? "true" : "false"}
            options={["empty", "true", "false"] as const}
            onChange={(__, val) => handleChangeDropdown(val, setValue)}
          />
        </Box>
      </Grid>
      <Grid size={4}>
        <Box sx={{ display: "flex" }}>
          <MuiCheckbox
            disabled
            checked={contextValue ? tryToJson(contextValue) : false}
          />
        </Box>
      </Grid>
    </Grid>
  );
};

export const CreatorFlags: FunctionComponent = () => {
  const [withTaskStats, setTaskStats] = useLocalStorage(
    FEATURES.DISABLE_TASK_STATS,
    null,
  );

  const [javascriptOption, setJavascriptOption] = useLocalStorage(
    FEATURES.HIDE_JAVASCRIPT_OPTION,
    null,
  );

  //boolean

  const [enableTaskDefinitionForm, setEnableTaskDefinitionForm] =
    useLocalStorage(FEATURES.ENABLE_TASK_DEFINITION_FORM, null);

  const [disableExpandWorkflow, setDisableExpandWorkflow] = useLocalStorage(
    FEATURES.DISABLE_EXPAND_WORKFLOW,
    null,
  );

  const [accessManagement, setAccessManagement] = useLocalStorage(
    FEATURES.ACCESS_MANAGEMENT,
    null,
  );
  const [copyToken, setCopyToken] = useLocalStorage(FEATURES.COPY_TOKEN, null);
  const [playground, setPlayground] = useLocalStorage(
    FEATURES.PLAYGROUND,
    null,
  );
  const [scheduler, setScheduler] = useLocalStorage(FEATURES.SCHEDULER, null);
  const [creatorEnableCreator, setCreatorEnableCreator] = useLocalStorage(
    FEATURES.CREATOR_ENABLE_CREATOR,
    null,
  );
  const [creatorEnableReaflowDiagram, setCreatorEnableReaflowDiagram] =
    useLocalStorage(FEATURES.CREATOR_ENABLE_REAFLOW_DIAGRAM, null);

  const [enableDarkmodeToggle, setEnableDarkmodeToggle] = useLocalStorage(
    FEATURES.ENABLE_DARK_MODE_TOGGLE,
    null,
  );

  const [navbarElementsVariant, setNavbarElementsVariant] = useLocalStorage(
    FEATURES.NAVBAR_ELEMENTS_VARIANT,
    null,
  );

  const [showStartTitle, setShowStartTitle] = useLocalStorage(
    FEATURES.SHOW_START_TITLE,
    null,
  );

  const [showCloudLink, setShowCloudLink] = useLocalStorage(
    FEATURES.SHOW_CLOUD_LINK,
    null,
  );

  const [showFeedbackForm, setShowFeedbackForm] = useLocalStorage(
    FEATURES.SHOW_FEEDBACK_FORM,
    null,
  );

  const [showSupportForm, setShowSupportForm] = useLocalStorage(
    FEATURES.SHOW_SUPPORT_FORM,
    null,
  );

  const [showDocumentation, setShowDocumentation] = useLocalStorage(
    FEATURES.SHOW_DOCUMENTATION,
    null,
  );

  const [showJoinSlackCommunity, setShowJoinSlackCommunity] = useLocalStorage(
    FEATURES.SHOW_JOIN_SLACK_COMMUNITY,
    null,
  );

  const [betaKeyboardFlow, setBetaKeyboardFlow] = useLocalStorage(
    FEATURES.BETA_KEYBOARD_FLOW,
    null,
  );

  const [enableMetricsDashboard, setEnableMetricsDashboard] = useLocalStorage(
    FEATURES.ENABLE_METRICS_DASHBOARD,
    null,
  );

  const [humanTask, setHumanTask] = useLocalStorage(FEATURES.HUMAN_TASK, null);

  const [integrations, setIntegrations] = useLocalStorage(
    FEATURES.INTEGRATIONS,
    null,
  );

  const [showNewsIcon, setShowNewsIcon] = useLocalStorage(
    FEATURES.SHOW_NEWS_ICON,
    null,
  );

  const [showOnBoardingQuiz, setShowOnBoardingQuiz] = useLocalStorage(
    FEATURES.SHOW_ONBOARDING_QUIZ,
    null,
  );

  const [envIsProduction, setEnvIsProduction] = useLocalStorage(
    FEATURES.ENV_IS_PRODUCTION,
    null,
  );

  const [taskIndexing, setTaskIndexing] = useLocalStorage(
    FEATURES.TASK_INDEXING,
    null,
  );

  const [remoteServices, setRemoteServices] = useLocalStorage(
    FEATURES.REMOTE_SERVICES,
    null,
  );

  const [sendgridTaskEnabled, setSendgridTaskEnabled] = useLocalStorage(
    FEATURES.SENDGRID_TASK,
    null,
  );

  const [aiPromptsVersioning, setAiPromptsVersioning] = useLocalStorage(
    FEATURES.AI_PROMPTS_VERSIONING,
    null,
  );

  const [
    advancedErrorInspectorValidations,
    setAdvancedErrorInspectorValidations,
  ] = useLocalStorage(FEATURES.ADVANCED_ERROR_INSPECTOR_VALIDATIONS, null);

  const [enableWhiteBackgroundForm, setEnableWhiteBackgroundForm] =
    useLocalStorage(FEATURES.ENABLE_WHITE_BACKGROUND_FORM, null);

  //string
  const [taskVisibility, setTaskVisibility] = useLocalStorage(
    FEATURES.TASK_VISIBILITY,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [metricsOriginUrl, setMetricsOriginUrl] = useLocalStorage(
    FEATURES.METRICS_ORIGIN_URL,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [loginRedirectType, setLoginRedirectType] = useLocalStorage(
    FEATURES.LOGIN_REDIRECT_TYPE,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [dragdropThreshold, setDragdropThreshold] = useLocalStorage(
    FEATURES.DRAG_DROP_TASK_INCREMENT_THRESHOLD,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [announcementExpiryDate, setAnnouncementExpiryDate] = useLocalStorage(
    FEATURES.ANNOUNCEMENT_EXPIRY_DATE,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [logRocketKey, setLogRocketKey] = useLocalStorage(
    FEATURES.LOG_ROCKET_KEY,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [customLogoUrl, setCustomLogoUrl] = useLocalStorage(
    FEATURES.CUSTOM_LOGO_URL,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [growthbookClientKey, setGrowthbookClientKey] = useLocalStorage(
    FEATURES.GROWTHBOOK_CLIENT_KEY,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [multiTenancyType, setMultiTenancyType] = useLocalStorage(
    FEATURES.MULTITENANCY_TYPE,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [defaultRoles, setDefaultRoles] = useLocalStorage(
    FEATURES.DEFAULT_ROLES,
    _identity(),
    {
      code: _identity,
      parse: _identity,
    },
  );

  const [showEndTimeInDatepicker, setShowEndTimeInDatePicker] = useLocalStorage(
    FEATURES.SHOW_END_TIME_IN_DATEPICKER,
    null,
  );

  const [showEventMonitor, setShowEventMonitor] = useLocalStorage(
    FEATURES.SHOW_EVENT_MONITOR,
    null,
  );
  const [showAiStudioBannerFlag, setShowAiStudioBannerFlag] = useLocalStorage(
    FEATURES.SHOW_AI_STUDIO_BANNER_FLAG,
    null,
  );
  const [showGetStartedPage, setShowGetStartedPage] = useLocalStorage(
    FEATURES.SHOW_GET_STARTED_PAGE,
    null,
  );
  const [gatewayEnabled, setGatewayEnabled] = useLocalStorage(
    FEATURES.GATEWAY_ENABLED,
    null,
  );
  const [
    enableRerunFromForkAndDowhileTasks,
    setEnableRerunFromForkAndDowhileTasks,
  ] = useLocalStorage(FEATURES.ENABLE_RERUN_FROM_FORK_AND_DOWHILE_TASKS, null);
  const [getStartedVideoUrl, setGetStartedVideoUrl] = useLocalStorage(
    FEATURES.GET_STARTED_VIDEO_URL,
    null,
  );
  const [hideImportBpmn, setHideImportBpmn] = useLocalStorage(
    FEATURES.HIDE_IMPORT_BPMN,
    null,
  );
  const [cloudTemplatesSource, setCloudTemplatesSource] = useLocalStorage(
    FEATURES.CLOUD_TEMPLATES_SOURCE,
    null,
  );

  const [showRolesMenuItem, setShowRolesMenuItem] = useLocalStorage(
    FEATURES.SHOW_ROLES_MENU_ITEM,
    null,
  );

  const [notifyHumanTask, setNotifyHumanTask] = useLocalStorage(
    FEATURES.NOTIFY_HUMAN_TASK,
    null,
  );
  const [workflowIntrospection, setWorkflowIntrospection] = useLocalStorage(
    FEATURES.WORKFLOW_INTROSPECTION,
    null,
  );
  const [enableConfetti, setEnableConfetti] = useLocalStorage(
    FEATURES.ENABLE_CONFETTI,
    null,
  );
  const [showAgent, setShowAgent] = useLocalStorage(FEATURES.SHOW_AGENT, null);
  const [enableAgentAudioInput, setEnableAgentAudioInput] = useLocalStorage(
    FEATURES.ENABLE_AGENT_AUDIO_INPUT,
    null,
  );
  const [aiCoderCloudWorker, setAiCoderCloudWorker] = useLocalStorage(
    FEATURES.AI_CODER_CLOUD_WORKER,
    null,
  );

  const handleStringChange = (
    value: string | undefined,
    setStateCb: (value: string | null) => void,
  ) => {
    setStateCb(value || null);
    window.location.reload();
  };

  const handleChangeDropdown = (
    value: unknown,
    setStateCb: (value: boolean | null) => void,
  ) => {
    if (!value) {
      setStateCb(null);
      window.location.reload();
      return;
    }
    const stringValue = Array.isArray(value) ? String(value[0]) : String(value);
    const variable: boolean | null =
      stringValue === "true" ? true : stringValue === "false" ? false : null;
    setStateCb(variable);
    window.location.reload();
  };

  const featureFlagsArray: (StringFeatureFlag | BooleanFeatureFlag)[] = [
    {
      name: "enable_task_stats",
      label: "Enable task stats",
      value: withTaskStats,
      contextValue: featureFlags.getContextValue(FEATURES.DISABLE_TASK_STATS),
      setValue: setTaskStats,
      type: "boolean",
    },

    {
      name: "hide_javascript_option",
      label: "Hide Javascript Option",
      value: javascriptOption,
      setValue: setJavascriptOption,
      contextValue: featureFlags.getContextValue(
        FEATURES.HIDE_JAVASCRIPT_OPTION,
      ),
      type: "boolean",
    },
    {
      name: "access_management",
      label: "Access Management",
      value: accessManagement,
      setValue: setAccessManagement,
      contextValue: featureFlags.getContextValue(FEATURES.ACCESS_MANAGEMENT),
      type: "boolean",
    },
    {
      name: "copy_token",
      label: "Copy Token",
      value: copyToken,
      setValue: setCopyToken,
      contextValue: featureFlags.getContextValue(FEATURES.COPY_TOKEN),
      type: "boolean",
    },
    {
      name: "playground",
      label: "Playground",
      value: playground,
      setValue: setPlayground,
      contextValue: featureFlags.getContextValue(FEATURES.PLAYGROUND),
      type: "boolean",
    },
    {
      name: "scheduler",
      label: "Scheduler",
      value: scheduler,
      setValue: setScheduler,
      contextValue: featureFlags.getContextValue(FEATURES.SCHEDULER),
      type: "boolean",
    },
    {
      name: "creator_enable_creator",
      label: "Creator Enable Creator",
      value: creatorEnableCreator,
      setValue: setCreatorEnableCreator,
      contextValue: featureFlags.getContextValue(
        FEATURES.CREATOR_ENABLE_CREATOR,
      ),
      type: "boolean",
    },
    {
      name: "creator_enable_reaflow_diagram",
      label: "Creator Enable Reaflow Diagram",
      value: creatorEnableReaflowDiagram,
      setValue: setCreatorEnableReaflowDiagram,
      contextValue: featureFlags.getContextValue(
        FEATURES.CREATOR_ENABLE_REAFLOW_DIAGRAM,
      ),
      type: "boolean",
    },
    {
      name: "enable_dark_mode_toggle",
      label: "Enable Dark Mode Toggle",
      value: enableDarkmodeToggle,
      setValue: setEnableDarkmodeToggle,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_DARK_MODE_TOGGLE,
      ),
      type: "boolean",
    },
    {
      name: "navbar_elements_variant",
      label: "Navbar Elements Variant",
      value: navbarElementsVariant,
      setValue: setNavbarElementsVariant,
      contextValue: featureFlags.getContextValue(
        FEATURES.NAVBAR_ELEMENTS_VARIANT,
      ),
      type: "boolean",
    },
    {
      name: "show_start_title",
      label: "Show Start Title",
      value: showStartTitle,
      setValue: setShowStartTitle,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_START_TITLE),
      type: "boolean",
    },
    {
      name: "show_cloud_link",
      label: "Show Cloud Link",
      value: showCloudLink,
      setValue: setShowCloudLink,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_CLOUD_LINK),
      type: "boolean",
    },
    {
      name: "show_feedback_form",
      label: "Show Feedback Form",
      value: showFeedbackForm,
      setValue: setShowFeedbackForm,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_FEEDBACK_FORM),
      type: "boolean",
    },
    {
      name: "show_support_form",
      label: "Show Support Form",
      value: showSupportForm,
      setValue: setShowSupportForm,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_SUPPORT_FORM),
      type: "boolean",
    },
    {
      name: "show_documentation",
      label: "Show Documentation",
      value: showDocumentation,
      setValue: setShowDocumentation,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_DOCUMENTATION),
      type: "boolean",
    },
    {
      name: "show_join_slack_community",
      label: "Show Join Slack Community",
      value: showJoinSlackCommunity,
      setValue: setShowJoinSlackCommunity,
      contextValue: featureFlags.getContextValue(
        FEATURES.SHOW_JOIN_SLACK_COMMUNITY,
      ),
      type: "boolean",
    },
    {
      name: "beta_keyboard_flow",
      label: "Beta Keyboard Flow",
      value: betaKeyboardFlow,
      setValue: setBetaKeyboardFlow,
      contextValue: featureFlags.getContextValue(FEATURES.BETA_KEYBOARD_FLOW),
      type: "boolean",
    },
    {
      name: "enable_metrics_dashboard",
      label: "Enable Metrics Dashboard",
      value: enableMetricsDashboard,
      setValue: setEnableMetricsDashboard,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_METRICS_DASHBOARD,
      ),
      type: "boolean",
    },
    {
      name: "human_task",
      label: "Human Task",
      value: humanTask,
      setValue: setHumanTask,
      contextValue: featureFlags.getContextValue(FEATURES.HUMAN_TASK),
      type: "boolean",
    },
    {
      name: "intgrations",
      label: "Integrations",
      value: integrations,
      setValue: setIntegrations,
      contextValue: featureFlags.getContextValue(FEATURES.INTEGRATIONS),
      type: "boolean",
    },
    {
      name: "show_news_icon",
      label: "Show News Icon",
      value: showNewsIcon,
      setValue: setShowNewsIcon,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_NEWS_ICON),
      type: "boolean",
    },
    {
      name: "enable_task_definition_form",
      label: "Enable Task Definition Form",
      value: enableTaskDefinitionForm,
      setValue: setEnableTaskDefinitionForm,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_TASK_DEFINITION_FORM,
      ),
      type: "boolean",
    },
    {
      name: "disable_expand_workflow",
      label: "Disable Expand Workflow",
      value: disableExpandWorkflow,
      setValue: setDisableExpandWorkflow,
      contextValue: featureFlags.getContextValue(
        FEATURES.DISABLE_EXPAND_WORKFLOW,
      ),
      type: "boolean",
    },
    {
      name: "show_onboarding_quiz",
      label: "Show Onboarding Quiz",
      value: showOnBoardingQuiz,
      setValue: setShowOnBoardingQuiz,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_ONBOARDING_QUIZ),
      type: "boolean",
    },
    {
      name: "task_indexing",
      label: "Task Indexing",
      value: taskIndexing,
      contextValue: featureFlags.getContextValue(FEATURES.TASK_INDEXING),
      setValue: setTaskIndexing,
      type: "boolean",
    },
    {
      name: "enable_white_background_form",
      label: "Enable white background form",
      value: enableWhiteBackgroundForm,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_WHITE_BACKGROUND_FORM,
      ),
      setValue: setEnableWhiteBackgroundForm,
      type: "boolean",
    },
    {
      name: "env_is_production",
      label: "Env is Production",
      value: envIsProduction,
      contextValue: featureFlags.getContextValue(FEATURES.ENV_IS_PRODUCTION),
      setValue: setEnvIsProduction,
      type: "boolean",
    },
    {
      name: "advanced_error_inspector_validations",
      label: "Advanced Error Inspector Validations",
      value: advancedErrorInspectorValidations,
      contextValue: featureFlags.getContextValue(
        FEATURES.ADVANCED_ERROR_INSPECTOR_VALIDATIONS,
      ),
      setValue: setAdvancedErrorInspectorValidations,
      type: "boolean",
    },
    {
      name: "remote_services",
      label: "Remote Services",
      value: remoteServices,
      contextValue: featureFlags.getContextValue(FEATURES.REMOTE_SERVICES),
      setValue: setRemoteServices,
      type: "boolean",
    },
    {
      name: "enable_rerun_from_fork_and_dowhile_tasks",
      label: "Enable Rerun From Fork And Dowhile Tasks",
      value: enableRerunFromForkAndDowhileTasks,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_RERUN_FROM_FORK_AND_DOWHILE_TASKS,
      ),
      setValue: setEnableRerunFromForkAndDowhileTasks,
      type: "boolean",
    },
    {
      name: "task_visibility",
      label: "Task Visibility",
      value: taskVisibility,
      setValue: setTaskVisibility,
      contextValue: featureFlags.getContextValue(FEATURES.TASK_VISIBILITY),
      type: "string",
    },
    {
      name: "metrics_origin_url",
      label: "Metrics Origin URL",
      value: metricsOriginUrl,
      setValue: setMetricsOriginUrl,
      contextValue: featureFlags.getContextValue(FEATURES.METRICS_ORIGIN_URL),
      type: "string",
    },
    {
      name: "login_redirect_type",
      label: "Login Redirect Type",
      value: loginRedirectType,
      setValue: setLoginRedirectType,
      contextValue: featureFlags.getContextValue(FEATURES.LOGIN_REDIRECT_TYPE),
      type: "string",
    },
    {
      name: "drag_drop_task_increment_threshold",
      label: "Drag Drop Task Increment Threshold",
      value: dragdropThreshold,
      setValue: setDragdropThreshold,
      contextValue: featureFlags.getContextValue(
        FEATURES.DRAG_DROP_TASK_INCREMENT_THRESHOLD,
      ),
      type: "string",
    },
    {
      name: "announcement_expiry_date",
      label: "Announcement Expiry Date",
      value: announcementExpiryDate,
      setValue: setAnnouncementExpiryDate,
      contextValue: featureFlags.getContextValue(
        FEATURES.ANNOUNCEMENT_EXPIRY_DATE,
      ),
      type: "string",
    },
    {
      name: "log_rocket_key",
      label: "Log Rocket Key",
      value: logRocketKey,
      setValue: setLogRocketKey,
      contextValue: featureFlags.getContextValue(FEATURES.LOG_ROCKET_KEY),
      type: "string",
    },
    {
      name: "growthbook_client_key",
      label: "Growthbook Client Key",
      value: growthbookClientKey,
      setValue: setGrowthbookClientKey,
      contextValue: featureFlags.getContextValue(
        FEATURES.GROWTHBOOK_CLIENT_KEY,
      ),
      type: "string",
    },
    {
      name: "custom_logo_url",
      label: "Custom logo URL",
      value: customLogoUrl,
      setValue: setCustomLogoUrl,
      contextValue: featureFlags.getContextValue(FEATURES.CUSTOM_LOGO_URL),
      type: "string",
    },
    {
      name: "multi_tenancy_type",
      label: "Multi-tenancy Type",
      value: multiTenancyType,
      setValue: setMultiTenancyType,
      contextValue: featureFlags.getContextValue(FEATURES.MULTITENANCY_TYPE),
      type: "string",
    },
    {
      name: "default_roles",
      label: "Default Roles",
      value: defaultRoles,
      setValue: setDefaultRoles,
      contextValue: featureFlags.getContextValue(FEATURES.DEFAULT_ROLES),
      type: "string",
    },
    {
      name: "show_end_time_in_date_picker",
      label: "Show End Time In Date Picker",
      value: showEndTimeInDatepicker,
      setValue: setShowEndTimeInDatePicker,
      contextValue: featureFlags.getContextValue(
        FEATURES.SHOW_END_TIME_IN_DATEPICKER,
      ),
      type: "boolean",
    },
    {
      name: "show_event_monitor",
      label: "Show Event Monitor",
      value: showEventMonitor,
      setValue: setShowEventMonitor,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_EVENT_MONITOR),
      type: "boolean",
    },
    {
      name: "show_ai_studio_banner",
      label: "Show AI Studio Banner",
      value: showAiStudioBannerFlag,
      setValue: setShowAiStudioBannerFlag,
      contextValue: featureFlags.getContextValue(
        FEATURES.SHOW_AI_STUDIO_BANNER_FLAG,
      ),
      type: "boolean",
    },
    {
      name: "show_get_started_page",
      label: "Show Get Started Page",
      value: showGetStartedPage,
      setValue: setShowGetStartedPage,
      contextValue: featureFlags.getContextValue(
        FEATURES.SHOW_GET_STARTED_PAGE,
      ),
      type: "boolean",
    },
    {
      name: "gateway_enabled",
      label: "Gateway Enabled",
      value: gatewayEnabled,
      setValue: setGatewayEnabled,
      contextValue: featureFlags.getContextValue(FEATURES.GATEWAY_ENABLED),
      type: "boolean",
    },
    {
      name: "get_started_video_url",
      label: "Get Started Page Video URL",
      value: getStartedVideoUrl,
      setValue: setGetStartedVideoUrl,
      contextValue: featureFlags.getContextValue(
        FEATURES.GET_STARTED_VIDEO_URL,
      ),
      type: "string",
    },
    {
      name: "sendgrid_task",
      label: "Sendgrid Task",
      value: sendgridTaskEnabled,
      contextValue: featureFlags.getContextValue(FEATURES.SENDGRID_TASK),
      setValue: setSendgridTaskEnabled,
      type: "boolean",
    },
    {
      name: "ai_prompts_versioning",
      label: "AI Prompts Versioning",
      value: aiPromptsVersioning,
      setValue: setAiPromptsVersioning,
      contextValue: featureFlags.getContextValue(
        FEATURES.AI_PROMPTS_VERSIONING,
      ),
      type: "boolean",
    },
    {
      name: "hide_import_bpmn",
      label: "Hide Import BPMN",
      value: hideImportBpmn,
      setValue: setHideImportBpmn,
      contextValue: featureFlags.getContextValue(FEATURES.HIDE_IMPORT_BPMN),
      type: "boolean",
    },
    {
      name: "cloud_templates_source",
      label: "Cloud Templates Source",
      value: cloudTemplatesSource,
      setValue: setCloudTemplatesSource,
      contextValue: featureFlags.getContextValue(
        FEATURES.CLOUD_TEMPLATES_SOURCE,
      ),
      type: "string",
    },
    {
      name: "show_roles_menu_item",
      label: "Show Roles Menu Item",
      value: showRolesMenuItem,
      setValue: setShowRolesMenuItem,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_ROLES_MENU_ITEM),
      type: "boolean",
    },
    {
      name: "notify_human_task",
      label: "Notify Human Task",
      value: notifyHumanTask,
      setValue: setNotifyHumanTask,
      contextValue: featureFlags.getContextValue(FEATURES.NOTIFY_HUMAN_TASK),
      type: "boolean",
    },
    {
      name: "workflow_introspection",
      label: "Workflow Introspection",
      value: workflowIntrospection,
      setValue: setWorkflowIntrospection,
      contextValue: featureFlags.getContextValue(
        FEATURES.WORKFLOW_INTROSPECTION,
      ),
      type: "boolean",
    },
    {
      name: "enable_confetti",
      label: "Enable Confetti",
      value: enableConfetti,
      setValue: setEnableConfetti,
      contextValue: featureFlags.getContextValue(FEATURES.ENABLE_CONFETTI),
      type: "boolean",
    },
    {
      name: "workflow_introspection",
      label: "Workflow Introspection",
      value: workflowIntrospection,
      setValue: setWorkflowIntrospection,
      contextValue: featureFlags.getContextValue(
        FEATURES.WORKFLOW_INTROSPECTION,
      ),
      type: "boolean",
    },
    {
      name: "show_agent",
      label: "Show Agent",
      value: showAgent,
      setValue: setShowAgent,
      contextValue: featureFlags.getContextValue(FEATURES.SHOW_AGENT),
      type: "boolean",
    },
    {
      name: "enable_agent_audio_input",
      label: "Enable Agent Audio Input",
      value: enableAgentAudioInput,
      setValue: setEnableAgentAudioInput,
      contextValue: featureFlags.getContextValue(
        FEATURES.ENABLE_AGENT_AUDIO_INPUT,
      ),
      type: "boolean",
    },
    // AI_CODER_CLOUD_WORKER
    {
      name: "ai_coder_cloud_worker",
      label: "AI Coder Cloud Worker",
      value: aiCoderCloudWorker,
      setValue: setAiCoderCloudWorker,
      contextValue: featureFlags.getContextValue(
        FEATURES.AI_CODER_CLOUD_WORKER,
      ),
      type: "boolean",
    },
  ];

  const headerStyle = {
    fontSize: "14px",
    fontWeight: 600,
  };

  const renderFlagFields = (
    items: (StringFeatureFlag | BooleanFeatureFlag)[],
  ) =>
    items.map((item) => (
      <Box key={item.name}>
        {item.type === "boolean" ? (
          <InputCheckboxTemplate
            {...item}
            handleChangeDropdown={handleChangeDropdown}
          />
        ) : (
          <InputStringTemplate
            {...item}
            handleStringChange={handleStringChange}
          />
        )}
      </Box>
    ));

  return (
    <div
      style={{
        width: "60%",
        margin: "auto",
        marginTop: 50,
      }}
    >
      <Paper elevation={2} sx={{ padding: 6 }}>
        <MuiTypography variant="h5">Beta Feature flags</MuiTypography>
        <span style={{ fontSize: "12px", color: "gray" }}>
          *LocalStorage take precedence over flags defined in window.conductor
          or process.env
        </span>
        <Box>
          <Grid
            container
            spacing={2}
            wrap="nowrap"
            sx={{
              width: "100%",
              borderBottom: "1px solid lightgray",
              padding: "10px 0",
            }}
          >
            <Grid size={4}>
              <Box sx={headerStyle}>Flag</Box>
            </Grid>
            <Grid size={4}>
              <Box sx={headerStyle}>Local Storage</Box>
            </Grid>
            <Grid size={4}>
              <Box sx={headerStyle}>Context</Box>
            </Grid>
          </Grid>
        </Box>
        <Box>{renderFlagFields(featureFlagsArray)}</Box>
      </Paper>
    </div>
  );
};
