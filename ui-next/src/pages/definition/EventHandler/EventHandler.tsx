import { Box, CircularProgress, Paper, Tab, Tabs } from "@mui/material";
import { DocLink } from "components/DocLink";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import { SnackbarMessage } from "components/SnackbarMessage";
import { ConductorSectionHeader } from "components/v1/layout/section/ConductorSectionHeader";
import EventHandlerButton from "pages/definition/EventHandler/eventhandlers/EventHandlerButton";
import EventHandlerEditor from "pages/definition/EventHandler/eventhandlers/EventHandlerEditor";
import EventHandlerForm from "pages/definition/EventHandler/eventhandlers/FormComponent/EventHandlerForm";
import { useEventHandlerDefinition } from "pages/definition/EventHandler/eventhandlers/state/hook";
import { Helmet } from "react-helmet";
import SectionContainer from "shared/SectionContainer";
import { colors } from "theme/tokens/variables";
import { DOC_LINK_URL } from "utils/constants/docLink";
import { EVENT_HANDLERS_URL } from "utils/constants/route";
import { ActorRef } from "xstate";
import { FormHandlerEvents } from "./eventhandlers/FormComponent/state/types";
import { SaveProtectionPrompt } from "./SaveProtectionPrompt";

export default function EventHandlerDefinition() {
  const [
    {
      handleSaveRequest,
      handleCancelRequest,
      handleResetRequest,
      handleEditChanges,
      handleConfirmSaveRequest,
      handleConfirmReset,
      handleDeleteRequest,
      handleConfirmDelete,
      handleBackToIdle,
      handleClearErrorMessage,
      toggleFormMode,
      service,
    },
    {
      isNewEventHandler,
      editorChanges,
      isConfirmSave,
      isConfirmReset,
      isSaving,
      originalSource,
      isConfirmDelete,
      madeChanges,
      message,
      eventHandlerName,
      isFormMode,
      couldNotParseJson,
      isEditorMode,
      isFetching,
    },
  ] = useEventHandlerDefinition();

  return (
    <Box id="event-handler-container">
      {isConfirmReset && (
        <ConfirmChoiceDialog
          header="Reset Changes"
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleConfirmReset?.();
            } else {
              handleBackToIdle?.();
            }
          }}
          message={
            "You will lose all changes made in the editor. Please confirm resetting this Event Handler definition to its original state."
          }
        />
      )}

      {isConfirmDelete && (
        <ConfirmChoiceDialog
          header="Delete Event Handler"
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleConfirmDelete?.();
            } else {
              handleBackToIdle?.();
            }
          }}
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>{eventHandlerName}</strong> Event
              Handler definition? This change cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{eventHandlerName}</strong> to confirm
              </div>
            </>
          }
          valueToBeDeleted={eventHandlerName}
          isInputConfirmation
        />
      )}
      <Helmet>
        <title>
          Event Handler Definition -&nbsp;
          {eventHandlerName ? eventHandlerName : "NEW"}
        </title>
      </Helmet>
      <SaveProtectionPrompt service={service} />
      <SectionContainer
        header={
          <ConductorSectionHeader
            breadcrumbItems={[
              { label: "Event Definitions", to: EVENT_HANDLERS_URL.BASE },
              {
                label: eventHandlerName
                  ? eventHandlerName
                  : "New Event Handler",
                to: "",
              },
            ]}
            title={eventHandlerName ? eventHandlerName : "New Event Handler"}
            buttonsComponent={
              <EventHandlerButton
                {...{
                  isConfirmSave,
                  isSaving,
                  handleConfirmSaveRequest,
                  handleCancelRequest,
                  handleSaveRequest,
                  handleResetRequest,
                  madeChanges,
                  handleDeleteRequest,
                  isNewEventHandler,
                  service,
                  disableDeleteBtn: isNewEventHandler,
                }}
              />
            }
          />
        }
      >
        <Paper
          variant="outlined"
          sx={{
            overflow: "auto",
            padding: 5,
            borderRadius: 0,
          }}
        >
          {message && (
            <SnackbarMessage
              message={message}
              severity="error"
              onDismiss={() => handleClearErrorMessage()}
            />
          )}

          <Box sx={{ position: "relative" }}>
            <Tabs
              value={isEditorMode ? 0 : 1}
              sx={{
                marginBottom: 0,
                borderBottom: "1px solid rgba(0,0,0,0.2)",
              }}
              onChange={toggleFormMode}
            >
              <Tab
                label="Event"
                value={1}
                disabled={couldNotParseJson}
                id="event-handler-form-tab"
              />
              <Tab
                label="Code"
                value={0}
                disabled={isFetching}
                id="event-handler-code-tab"
              />
            </Tabs>

            <DocLink
              label="Event Handler docs"
              url={DOC_LINK_URL.EVENT_HANDLER}
            />
          </Box>
          {isFetching ? (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                height: "calc(100vh - 250px)",
              }}
            >
              <CircularProgress />
            </Box>
          ) : (
            <Box
              sx={{
                height: "calc(100vh - 180px)",
                overflow: "scroll",
                color: (theme) =>
                  theme.palette?.mode === "dark" ? colors.gray14 : undefined,
                backgroundColor: (theme) => theme.palette.customBackground.form,
              }}
            >
              {isFormMode ? (
                <EventHandlerForm
                  actor={
                    service.children.get(
                      "eventFormMachine",
                    ) as ActorRef<FormHandlerEvents>
                  }
                />
              ) : (
                <EventHandlerEditor
                  {...{
                    handleEditChanges,
                    editorChanges,
                    isConfirmSave,
                    originalSource,
                  }}
                />
              )}
            </Box>
          )}
        </Paper>
      </SectionContainer>
    </Box>
  );
}
