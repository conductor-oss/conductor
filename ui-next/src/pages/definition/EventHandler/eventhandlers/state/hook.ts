import { useInterpret, useSelector } from "@xstate/react";
import { MessageContext } from "components/v1/layout/MessageContext";
import _get from "lodash/get";
import { useContext, useMemo } from "react";
import { useLocation, useParams, Location } from "react-router";
import { EVENT_HANDLERS_URL } from "utils/constants/route";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useAuthHeaders } from "utils/query";
import { NEW_EVENT_HANDLER_TEMPLATE } from "../eventHandlerSchema";
import { saveEventHandlerMachine } from "./machine";
import {
  SaveEventHandlerMachineEventTypes,
  SaveEventHandlerStates,
} from "./types";

const isNewEventHandlerDef = (location: Location) =>
  location.pathname === EVENT_HANDLERS_URL.NEW;

export const useEventHandlerDefinition = () => {
  const authHeaders = useAuthHeaders();

  const pushHistory = usePushHistory();
  const { setMessage } = useContext(MessageContext);

  const location = useLocation();
  const params = useParams();
  const isNewEventHandlerUrl = isNewEventHandlerDef(location);
  const eventHandlerName = isNewEventHandlerUrl
    ? ""
    : decodeURIComponent(_get(params, "name") || "");

  const service = useInterpret(saveEventHandlerMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      eventHandlerName,
      originalSource: isNewEventHandlerUrl
        ? JSON.stringify(NEW_EVENT_HANDLER_TEMPLATE, null, 2)
        : "",
      couldNotParseJson: false,
      isNewEventHandler: isNewEventHandlerUrl,
    },
    actions: {
      pushToHistory: ({ eventHandlerName }) => {
        pushHistory(
          `${EVENT_HANDLERS_URL.BASE}/${encodeURIComponent(eventHandlerName)}`,
        );
      },
      goBackToEventHandlersIndex: (_context) => {
        pushHistory(EVENT_HANDLERS_URL.BASE);
      },
      redirectToNew: () => {
        pushHistory(EVENT_HANDLERS_URL.NEW);
      },
      showSaveSuccessMessage: () => {
        setMessage({
          text: "Event handler saved successfully.",
          severity: "success",
        });
      },
    },
  });

  const handleEditChanges = (changes: string) => {
    return service.send({
      type: SaveEventHandlerMachineEventTypes.EDIT_DEBOUNCE_EVT,
      changes,
    });
  };
  const isFormMode = useSelector(service, (state) =>
    state.matches([SaveEventHandlerStates.IDLE, SaveEventHandlerStates.FORM]),
  );

  const isEditorMode = useSelector(service, (state) => {
    return (
      state.matches([
        SaveEventHandlerStates.IDLE,
        SaveEventHandlerStates.EDITOR,
      ]) || state.matches([SaveEventHandlerStates.CONFIRM_SAVE])
    );
  });

  const isFetching = useSelector(service, (state) => {
    return state.matches([
      SaveEventHandlerStates.FETCH_EVENT_HANDLER_DEFINITION,
    ]);
  });

  const couldNotParseJson = useSelector(
    service,
    (state) => state.context.couldNotParseJson,
  );

  const handleSaveRequest = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.SAVE_EVT });

  const handleConfirmSaveRequest = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.CONFIRM_SAVE_EVT });

  const handleCancelRequest = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.CANCEL_SAVE_EVT });

  const handleResetRequest = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.RESET_EVT });

  const handleConfirmReset = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.RESET_CONFIRM_EVT });

  const handleDeleteRequest = () =>
    service.send({ type: SaveEventHandlerMachineEventTypes.DELETE_EVT });

  const handleConfirmDelete = () =>
    service.send({
      type: SaveEventHandlerMachineEventTypes.DELETE_CONFIRM_EVT,
    });

  const handleDefineNewEventHandler = () => {
    service.send({
      type: SaveEventHandlerMachineEventTypes.NEW_EVENT_HANDLER_REQUEST,
    });
  };

  const handleConfirmNewEventHandler = () => {
    service.send({
      type: SaveEventHandlerMachineEventTypes.CONFIRM_NEW_EVENT,
    });
  };

  const handleBackToIdle = () => {
    service.send({
      type: SaveEventHandlerMachineEventTypes.BACK_TO_IDLE,
    });
  };

  const handleClearErrorMessage = () => {
    service.send({
      type: SaveEventHandlerMachineEventTypes.CLEAR_ERROR_MESSAGE,
    });
  };

  const originalSource = useSelector(
    service,
    (state) => state.context.originalSource,
  );

  const editorChanges = useSelector(
    service,
    (state) => state.context.editorChanges,
  );

  const isNewEventHandler = useSelector(
    service,
    (state) => state.context.isNewEventHandler,
  );

  const message = useSelector(service, (state) => state.context.message);
  // const errors = useSelector(service, (state) => state.context.errors);

  const isIdle = useSelector(service, (state) => state.matches("idle"));

  const isConfirmSave = useSelector(service, (state) =>
    state.matches("confirmSave"),
  );

  const isSaving = useSelector(
    service,
    (state) =>
      state.matches("createEventHandler") ||
      state.matches("updateEventHandler"),
  );

  const isConfirmReset = useSelector(service, (state) =>
    ["idle.form.confirmReset", "idle.editor.confirmReset"].some(state.matches),
  );

  const isConfirmDelete = useSelector(service, (state) =>
    ["idle.form.confirmDelete", "idle.editor.confirmDelete"].some(
      state.matches,
    ),
  );

  const isConfirmNew = useSelector(service, (state) =>
    ["idle.form.confirmNew", "idle.editor.confirmNew"].some(state.matches),
  );

  const isUpdatingToNewChanges = useSelector(service, (state) =>
    state.matches("refetchEventHandlerChanges"),
  );

  const madeChanges = useMemo(
    () =>
      editorChanges !== "" &&
      (editorChanges !== originalSource || isNewEventHandler),
    [editorChanges, originalSource, isNewEventHandler],
  );

  const toggleFormMode = () => {
    service.send({
      type: SaveEventHandlerMachineEventTypes.TOGGLE_FORM_EDITOR_EVT,
      isEditorMode: !isEditorMode,
    });
  };

  return [
    {
      handleDeleteRequest,
      handleConfirmDelete,
      handleConfirmReset,
      handleResetRequest,
      handleCancelRequest,
      handleConfirmSaveRequest,
      handleSaveRequest,
      handleEditChanges,
      handleDefineNewEventHandler,
      handleConfirmNewEventHandler,
      handleBackToIdle,
      handleClearErrorMessage,
      toggleFormMode,
      service,
    },
    {
      isNewEventHandler,
      eventHandlerName,
      originalSource,
      editorChanges,
      isConfirmReset,
      isConfirmDelete,
      isConfirmNew,
      // message,
      // errors,
      madeChanges,
      isUpdatingToNewChanges,
      isConfirmSave,
      isSaving,
      isIdle,
      message,
      isFormMode,
      isEditorMode,
      couldNotParseJson,
      isFetching,
    },
  ] as const;
};
