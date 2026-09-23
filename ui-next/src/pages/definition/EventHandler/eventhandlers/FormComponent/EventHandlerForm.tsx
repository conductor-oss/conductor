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
import { ComponentType, useRef, useState } from "react";
import { ActorRef } from "xstate";

import { EventHandlerAction } from "types/Events";
import { Props } from "./ActionForms/common";

import MuiTypography from "components/ui/MuiTypography";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import HelperText from "components/ui/inputs/HelperText";
import { useEventNameSuggestions } from "utils/hooks/useEventNameSuggestions";
import ActionCard from "./ActionCard";
import { CompleteTask } from "./ActionForms/CompleteTask";
import { FailTask } from "./ActionForms/FailTask";
import { StartAgentActionForm } from "./ActionForms/StartAgentTask";
import { StartWorkflowActionForm } from "./ActionForms/StartWorkflowTask";
import { TerminateWorkflowForm } from "./ActionForms/TerminateWorkflowTask";
import { UpdateWorkflowForm } from "./ActionForms/UpdateWorkflowTask";
import AddActionMenu from "./AddActionMenu";
import FormSection from "./FormSection";
import { templateFor } from "./actionMeta";
import { tabColumnStyle, tabSurfaceStyle } from "../tabLayout";
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
const columnStyle = {
  ...tabColumnStyle,
  display: "flex",
  flexDirection: "column",
  gap: 4,
};

/**
 * Name is the only short field in Details, so at md it takes the Active toggle
 * as a partner rather than leaving a long empty row beside it, with Description
 * spanning underneath. Stacked, the cells fall back to DOM order — Name,
 * Description, Active — which keeps the toggle out from between the two inputs.
 */
const detailsBodyStyle = {
  display: "grid",
  gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1fr) auto" },
  columnGap: 6,
  alignItems: "center",
};

const nameCellStyle = { gridColumn: { md: "1" }, gridRow: { md: 1 } };
const descriptionCellStyle = {
  gridColumn: { md: "1 / -1" },
  gridRow: { md: 2 },
};
const activeCellStyle = { gridColumn: { md: "2" }, gridRow: { md: 1 } };

const emptyStateStyle = {
  border: (theme: Theme) => `1px dashed ${theme.palette.divider}`,
  borderRadius: 1,
  px: 5,
  py: 8,
  textAlign: "center",
  color: "text.secondary",
};

const filter = createFilterOptions<string>();

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
    <Box sx={pageStyle}>
      <Box sx={columnStyle}>
        <FormSection title="Details" bodySx={detailsBodyStyle}>
          <Box sx={nameCellStyle}>
            <ConductorInput
              label="Name"
              fullWidth
              required
              placeholder="Event Handler Name"
              id="event-name-input"
              name="name"
              value={name}
              onTextInputChange={(val) => handleChange("name", val)}
            />
          </Box>
          <Box sx={descriptionCellStyle}>
            <ConductorInput
              id="event-description-field"
              label="Description"
              name="description"
              multiline
              minRows={2}
              fullWidth
              onTextInputChange={(value) => handleChange("description", value)}
              value={description}
              placeholder="What this handler is for"
            />
          </Box>
          <Box sx={activeCellStyle}>
            <FormControlLabel
              control={
                <Switch
                  color="primary"
                  checked={active}
                  name="activateEvent"
                  onChange={(val) => handleChange("active", val.target.checked)}
                />
              }
              label="Active"
            />
          </Box>
        </FormSection>

        <FormSection title="Event">
          <ConductorAutoComplete
            label="Event"
            fullWidth
            required
            placeholder="Event String"
            id="event-string-input"
            options={suggestions}
            value={event}
            onChange={(_, val) => handleEventChange(val ?? "")}
            onInputChange={(_, val) => handleEventChange(val)}
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
          <HelperText>
            The queue this handler listens on, as <code>source:queue</code> —
            for example <code>kafka:payments.settled</code>.
          </HelperText>
        </FormSection>

        <FormSection title="Condition">
          <ConductorCodeBlockInput
            label="Condition (Trigger if evaluated to true)"
            language="javascript"
            value={condition}
            onChange={(val) => handleChange("condition", val)}
          />
          <HelperText>
            Runs on every matching event when empty. Actions fire only if this
            evaluates to true.
          </HelperText>
        </FormSection>

        <FormSection
          title="Actions"
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
