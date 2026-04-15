import { useInterpret, useSelector } from "@xstate/react";
import { MessageContext } from "components/providers/messageContext";
import { useSetAtom } from "jotai";
import _get from "lodash/get";
import {
  DefinitionMachineEventTypes,
  RedirectToExecutionPageEvent,
  WorkflowDefinitionEvents,
} from "pages/definition/state/types";
import { usePanelChanges } from "pages/definition/state/usePanelChanges";
import { removeDeletedWorkflow } from "pages/runWorkflow/runWorkflowUtils";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useContext, useEffect, useMemo } from "react";
import { useQueryClient } from "react-query";
import { Location, useLocation, useNavigate, useParams } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { setDefinitionServiceAtom } from "components/features/agent/agentAtomsStore";
import { AuthContext } from "components/features/auth/context";
import { PersistableSidebarEventTypes } from "shared/PersistableSidebar/state/types";
import { AuthProviderMachineContext } from "shared/state";
import { User } from "types/User";
import { getErrors } from "utils";
import { WORKFLOW_METADATA_BASE_URL } from "utils/constants/api";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { useActionWithPath, useAuthHeaders } from "utils/query";
import { ActorRef, State } from "xstate";
import { workflowDefinitionMachine } from "./machine";

const WORKFLOW_FETCH_FAILED = "Failed to fetch workflow";
const WORKFLOW_FETCH_FORBIDDEN =
  "You don't seem to have access to view this workflow";

const isNewWorkflowFn = (location: Location) =>
  location.pathname === WORKFLOW_DEFINITION_URL.NEW;

export const useWorkflowDefinition = (currentUser: User) => {
  const queryClient = useQueryClient();
  const fetchContext = useFetchContext(); // Maintain compatibility
  const { setMessage } = useContext(MessageContext);
  const [timeInParameter, setTimeInParameter] = useQueryState<string>(
    "showImportSuccess",
    "",
  );
  const [blogUrl] = useQueryState<string>("blogUrl", "");
  const [taskReferenceName, handleTaskReferenceName] = useQueryState<string>(
    "taskReferenceName",
    "",
  );
  const authHeaders = useAuthHeaders();

  const setDefinitionService = useSetAtom(setDefinitionServiceAtom);

  // Needed stuff for compatibility mode
  const navigate = useNavigate();

  const { authService } = useContext(AuthContext);

  // No-op actor used as a fallback when authService is not available (OSS mode).
  const dummyActor = useMemo(
    (): ActorRef<any> => ({
      id: "noop",
      send: () => {},
      subscribe: () => ({ unsubscribe: () => {} }),
      getSnapshot: () => ({ children: {} }),
      [Symbol.observable]() {
        return { subscribe: () => ({ unsubscribe: () => {} }) };
      },
    }),
    [],
  );

  const sidebarActor = useSelector(
    authService ?? dummyActor,
    (state: State<AuthProviderMachineContext>) =>
      state?.children?.["sidebarMachine"],
  );

  const { mutateAsync: deleteWorkflowMutator } = useActionWithPath();

  const location = useLocation();
  const params = useParams();
  const isNewWorkflowUrl = isNewWorkflowFn(location);
  const templateIdMaybe = _get(params, "templateId");
  const version = _get(params, "version");
  const workflowNameParam = _get(params, "name");
  const workflowName = useMemo<string>(() => {
    if (isNewWorkflowUrl) {
      return "NEW";
    }

    if (workflowNameParam) {
      try {
        return decodeURIComponent(workflowNameParam);
      } catch {
        setMessage({
          severity: "error",
          text: "Name has invalid chars and cant be opened.",
        });
        return "";
      }
    }

    return "";
  }, [isNewWorkflowUrl, workflowNameParam, setMessage]);
  // End needed stuff

  const fetchWorkflowAndRelatedData = async (
    workflowName: string,
    currentVersion: string | undefined,
  ) => {
    const maybeVersion = currentVersion ? `?version=${currentVersion}` : "";
    const path = `${WORKFLOW_METADATA_BASE_URL}/${encodeURIComponent(
      workflowName,
    )}${maybeVersion}`;

    try {
      // First fetch the workflow metadata
      const workflowData = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers: authHeaders }),
      );

      return {
        workflow: workflowData,
      };
    } catch (error: any) {
      const errorMessage = (await getErrors(error))?.message;

      if (errorMessage) {
        return Promise.reject({ message: errorMessage });
      }

      const status = error.status;

      if (status === 403) {
        return Promise.reject({ message: WORKFLOW_FETCH_FORBIDDEN });
      }

      if (status === 404) {
        return Promise.reject({ message: "Workflow was not found" });
      }

      return Promise.reject({ message: WORKFLOW_FETCH_FAILED });
    }
  };

  const service = useInterpret(workflowDefinitionMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      currentUserInfo: currentUser,
      initialSelectedTaskReferenceName: taskReferenceName,
      successfullyImportedWorkflowId: timeInParameter,
    },
    services: {
      fetchWorkflow: async (data) => {
        const { workflowName, currentVersion } = data;

        if (workflowName) {
          const result = fetchWorkflowAndRelatedData(
            workflowName,
            currentVersion,
          );
          return result;
        }
      },
      deleteWorkflowVersion: async ({ currentWf }, __) => {
        try {
          if (currentWf?.name) {
            // @ts-ignore
            await deleteWorkflowMutator({
              method: "delete",
              path: `${WORKFLOW_METADATA_BASE_URL}/${encodeURIComponent(
                currentWf.name,
              )}/${currentWf?.version}`,
            }).then(() => {
              removeDeletedWorkflow(currentWf?.name, currentWf?.version);
            });
          } else {
            return Promise.reject({ message: "Workflow's name is undefined" });
          }
        } catch (error) {
          return Promise.reject(error);
        }
      },
    },
    actions: {
      closeLeftSidebar: () => {
        sidebarActor?.send({
          type: PersistableSidebarEventTypes.COLLAPSE_SIDEBAR_EVENT,
        });
      },
      openLeftSidebar: () => {
        sidebarActor?.send({
          type: PersistableSidebarEventTypes.EXPAND_SIDEBAR_EVENT,
        });
      },
      pushToHistory: (
        { workflowName, currentVersion },
        { isContinueCreate }: any,
      ) => {
        if (isContinueCreate) {
          // Clear localStorage for new workflow when saving and creating new
          localStorage.removeItem("newWorkflowDef");
          // Clear workflow history and selected workflow to ensure clean state
          localStorage.removeItem("workflowHistory");
          localStorage.removeItem("selectedWorkflow");

          if (isNewWorkflowUrl) {
            window.location.reload();
          } else {
            navigate(WORKFLOW_DEFINITION_URL.NEW);
          }
        } else if (workflowName) {
          navigate(
            `${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(
              workflowName,
            )}${currentVersion == null ? "" : "/" + currentVersion}`,
          );
        }
      },
      goBackToDefinitionSelection: (_context) => {
        navigate(WORKFLOW_DEFINITION_URL.BASE);
      },
      redirectToExecutionPage: (
        _context,
        data: RedirectToExecutionPageEvent,
      ) => {
        navigate(`/execution/${data.executionId}`);
      },
      setQueryParam: (_context, data: any) => {
        handleTaskReferenceName(data?.node?.id);
      },
      removeQueryParam: (_context) => {
        handleTaskReferenceName("");
      },
      dismissImportSuccessfullParam: () => {
        setTimeInParameter("");
      },
      showSuccessMassage: () => {
        setMessage({
          text: "Workflow saved successfully.",
          severity: "success",
        });
      },
    },
  });

  const workflowVersions = useSelector(
    service,
    (state) => state.context.workflowVersions,
  );

  useEffect(() => {
    if (isNewWorkflowUrl || templateIdMaybe || workflowName || version) {
      service.send({
        type: DefinitionMachineEventTypes.UPDATE_ATTRIBS_EVT,
        workflowName,
        isNewWorkflow: isNewWorkflowUrl,

        currentVersion: version,
        workflowTemplateId: templateIdMaybe,
      });
    }
  }, [workflowName, isNewWorkflowUrl, templateIdMaybe, version, service]);

  const handleSetMessage = (messageSeverity: any) =>
    service.send({
      type: DefinitionMachineEventTypes.ASSIGN_MESSAGE_EVT,
      ...messageSeverity,
    });

  const handleResetMessage = () => {
    service.send({ type: DefinitionMachineEventTypes.MESSAGE_RESET_EVT });
  };

  const isNewWorkflow = useSelector(
    service,
    (state) => state.context.isNewWorkflow,
  );

  const message = useSelector(service, (state) => state.context.message);
  const { leftPanelExpanded, setLeftPanelExpanded } = usePanelChanges(service);

  // FIXME: Temporary hack to pin the service to the Agent atom store.
  useEffect(() => {
    setDefinitionService(service as ActorRef<WorkflowDefinitionEvents>);
  }, [service, setDefinitionService]);

  return [
    {
      handleSetMessage,
      handleResetMessage,
      setLeftPanelExpanded,
    },
    {
      isNewWorkflow,
      workflowName,
      workflowVersions,
      message,
      definitionActor: service,
      leftPanelExpanded,
      blogUrl,
    },
  ] as const;
};
