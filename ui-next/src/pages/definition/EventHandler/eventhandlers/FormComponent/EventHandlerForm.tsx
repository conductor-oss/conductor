import {
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import {
  Box,
  FormControlLabel,
  Switch,
  Theme,
  createFilterOptions,
} from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import HelperText from "components/ui/inputs/HelperText";
import { ComponentType, useRef, useState } from "react";
import { EventHandlerAction } from "types/Events";
import { useEventNameSuggestions } from "utils/hooks/useEventNameSuggestions";
import { ActorRef } from "xstate";
import { tabColumnStyle, tabSurfaceStyle } from "../tabLayout";
import ActionCard from "./ActionCard";
import { CompleteTask } from "./ActionForms/CompleteTask";
import { FailTask } from "./ActionForms/FailTask";
import { StartAgentActionForm } from "./ActionForms/StartAgentTask";
import { StartWorkflowActionForm } from "./ActionForms/StartWorkflowTask";
import { TerminateWorkflowForm } from "./ActionForms/TerminateWorkflowTask";
import { UpdateWorkflowForm } from "./ActionForms/UpdateWorkflowTask";
import { Props } from "./ActionForms/common";
import AddActionMenu from "./AddActionMenu";
import FormSection from "./FormSection";
import { templateFor } from "./actionMeta";
import { useEventHandlerFormActor } from "./state/hook";
import { Action, FormHandlerEvents } from "./state/types";

/**
 * No ground of its own — the tab container paints the surface for both tabs,
 * and the cards are separated by their borders rather than by contrast.
 */
const pageStyle = {
  ...tabSurfaceStyle,
  minHeight: "100%",
};

// NB: this theme's spacing unit is 4px, not MUI's default 8px.
/**
 * Two columns once there is room for both at a usable width: what the handler
 * is and when it fires on the left, what it does on the right. Narrower than
 * that, the grid collapses to one column in DOM order.
 */
const columnStyle = {
  ...tabColumnStyle,
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))",
  gap: 4,
  alignItems: "start",
};

const stackStyle = {
  display: "flex",
  flexDirection: "column",
  gap: 4,
  minWidth: 0,
};

const emptyStateStyle = {
  border: (theme: Theme) => `1px dashed ${theme.palette.divider}`,
  borderRadius: 1,
  px: 5,
  py: 8,
  textAlign: "center",
  color: "text.secondary",
};

const filter = createFilterOptions<string>();

// The server splits the event at its first colon (EventQueues.getQueue).
const EVENT_PATTERN = /^[\w-]+:.+$/;

const eventError = (event?: string) => {
  if (!event) return "Event is required.";
  if (!EVENT_PATTERN.test(event))
    return "Use type:queue, e.g. conductor:my_workflow:my_task_ref.";
  return undefined;
};

const actionForms: Record<Action, ComponentType<Props>> = {
  [Action.COMPLETE_TASK]: CompleteTask,
  [Action.FAIL_TASK]: FailTask,
  [Action.TERMINATE_WORKFLOW]: TerminateWorkflowForm,
  [Action.UPDATE_WORKFLOW_VARIABLES]: UpdateWorkflowForm,
  [Action.START_WORKFLOW]: StartWorkflowActionForm,
  [Action.START_AGENT]: StartAgentActionForm,
};

/**
 * Actions carry no id of their own, but sortable items and React keys need one
 * that follows an action when it moves. Keep a parallel list of client-side
 * ids and update it alongside every add/remove/move made from this form. When
 * the list changes from elsewhere (Code tab, reset) the length usually
 * changes too, and the ids are regenerated.
 */
const useActionIds = (count: number) => {
  const nextId = useRef(0);
  const makeId = () => `action-${nextId.current++}`;
  const [ids, setIds] = useState<string[]>(() =>
    Array.from({ length: count }, makeId),
  );

  let current = ids;
  if (ids.length !== count) {
    current = Array.from({ length: count }, makeId);
    setIds(current);
  }

  return {
    ids: current,
    prepend: () => setIds((prev) => [makeId(), ...prev]),
    remove: (index: number) =>
      setIds((prev) => prev.filter((_, i) => i !== index)),
    move: (from: number, to: number) =>
      setIds((prev) => {
        const next = [...prev];
        next.splice(to, 0, ...next.splice(from, 1));
        return next;
      }),
  };
};

const EventHandlerForm = ({
  actor,
}: {
  actor: ActorRef<FormHandlerEvents>;
}) => {
  const [
    { name, condition, actions, event, active, description },
    {
      handleChangeAction,
      handleChange,
      handleAction,
      removeAction,
      moveAction,
      handleEventChange,
    },
  ] = useEventHandlerFormActor(actor);

  const suggestions = useEventNameSuggestions();

  // Required-field errors wait until the field has been edited, so a new
  // handler doesn't open covered in red.
  const [touched, setTouched] = useState({ name: false, event: false });
  const nameErrorText =
    touched.name && !name?.trim() ? "Name is required." : undefined;
  const eventErrorText = touched.event ? eventError(event) : undefined;

  // Bumped on every add so the new card can announce itself. persistNewAction
  // prepends, so the action just added is always the one at index 0.
  const [addToken, setAddToken] = useState(0);

  const actionIds = useActionIds(actions?.length ?? 0);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const addAction = (type: Action) => {
    handleAction(type);
    actionIds.prepend();
    setAddToken((token) => token + 1);
  };

  const deleteAction = (index: number) => {
    removeAction(index);
    actionIds.remove(index);
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const from = actionIds.ids.indexOf(String(active.id));
    const to = actionIds.ids.indexOf(String(over.id));
    if (from < 0 || to < 0) return;
    moveAction(from, to);
    actionIds.move(from, to);
  };

  return (
    <Box sx={pageStyle} id="event-handler-form-wrapper">
      <Box sx={columnStyle}>
        <Box sx={stackStyle}>
          <FormSection
            title="Details"
            id="event-handler-section-details"
            action={
              <FormControlLabel
                sx={{ mr: 0 }}
                control={
                  <Switch
                    size="small"
                    color="primary"
                    checked={active}
                    name="activateEvent"
                    onChange={(val) =>
                      handleChange("active", val.target.checked)
                    }
                  />
                }
                label="Active"
              />
            }
          >
            <ConductorInput
              label="Name"
              fullWidth
              required
              placeholder="e.g. payments-settled-complete-task"
              id="event-name-input"
              name="name"
              value={name}
              error={Boolean(nameErrorText)}
              helperText={nameErrorText}
              onTextInputChange={(val) => {
                setTouched((t) => ({ ...t, name: true }));
                handleChange("name", val);
              }}
            />
            <ConductorInput
              id="event-description-field"
              label="Description"
              name="description"
              multiline
              minRows={2}
              fullWidth
              onTextInputChange={(value) => handleChange("description", value)}
              value={description}
              placeholder="Enter description"
            />
          </FormSection>

          <FormSection title="Event" id="event-handler-section-event">
            <ConductorAutoComplete
              label="Event"
              fullWidth
              required
              placeholder="conductor:my_workflow:my_task_ref"
              id="event-string-input"
              options={suggestions}
              value={event}
              error={Boolean(eventErrorText)}
              helperText={eventErrorText}
              onChange={(_, val) => handleEventChange(val ?? "")}
              onInputChange={(_, val, reason) => {
                // MUI also fires this with reason "reset" when it syncs the
                // stored value in; only a real edit should surface errors.
                if (reason === "input") {
                  setTouched((t) => ({ ...t, event: true }));
                }
                handleEventChange(val);
              }}
              freeSolo
              selectOnFocus
              filterOptions={(options, params) => {
                const filtered = filter(options, params);

                const { inputValue } = params;
                // Suggest the creation of a new value
                const isExisting = options.some(
                  (option) => inputValue === option,
                );

                if (inputValue !== "" && !isExisting) {
                  filtered.push(`${inputValue}`);
                }

                return filtered;
              }}
            />
            <HelperText sx={{ pt: 0 }}>
              Queue to listen on, e.g.{" "}
              <code>conductor:my_workflow:my_task_ref</code>.
            </HelperText>
          </FormSection>

          <FormSection title="Condition" id="event-handler-section-condition">
            <ConductorCodeBlockInput
              label="Condition (Trigger if evaluated to true)"
              language="javascript"
              value={condition}
              onChange={(val) => handleChange("condition", val)}
            />
            <HelperText sx={{ pt: 0 }}>
              Runs on every matching event when empty. Actions fire only if this
              evaluates to true.
            </HelperText>
          </FormSection>
        </Box>

        <FormSection
          title="Actions"
          id="event-handler-section-actions"
          count={actions?.length ?? 0}
          action={<AddActionMenu onAdd={addAction} />}
        >
          {!actions?.length && (
            <MuiTypography variant="body2" sx={emptyStateStyle}>
              No actions yet. Use <strong>Add action</strong> to choose what
              happens when the event fires.
            </MuiTypography>
          )}
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={actionIds.ids}
              strategy={verticalListSortingStrategy}
            >
              {actions?.map((action: EventHandlerAction, index: number) => {
                const Component = actionForms[action.action as Action];
                if (!Component) return null;
                const id = actionIds.ids[index];

                return (
                  <ActionCard
                    key={id}
                    id={id}
                    index={index}
                    payload={action}
                    highlightToken={
                      index === 0 && addToken > 0 ? addToken : null
                    }
                    onRemove={() => deleteAction(index)}
                    onChangeType={(next) =>
                      handleChangeAction(index, templateFor(next))
                    }
                  >
                    <Component
                      onRemove={() => deleteAction(index)}
                      handleChangeAction={handleChangeAction}
                      payload={action}
                      index={index}
                    />
                  </ActionCard>
                );
              })}
            </SortableContext>
          </DndContext>
        </FormSection>
      </Box>
    </Box>
  );
};

export default EventHandlerForm;
