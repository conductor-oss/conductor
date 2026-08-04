/**
 * Prompt-first Instructions section for LLM_CHAT_COMPLETE (enterprise).
 *
 * Primary action: select a saved AI Prompt from the prompt registry.
 * Secondary action: write custom system instructions (collapsed by default).
 *
 * The two modes are mutually exclusive:
 *   - Selecting a saved prompt clears custom text, sets allowRawPrompts=false,
 *     and auto-populates promptVariables / temperature / topP / stopWords.
 *   - Typing custom instructions clears the prompt selection and sets
 *     allowRawPrompts=true.
 *
 * When the prompt registry is empty (e.g. OSS fallback), the picker shows
 * no options and the custom instructions section auto-expands.
 */

import { Box, Collapse, Divider, Grid, Link, Typography } from "@mui/material";
import { CaretDown, CaretRight } from "@phosphor-icons/react";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import MuiTypography from "components/ui/MuiTypography";
import PromptVariables from "components/PromptVariables";
import { path as _path } from "lodash/fp";
import { useCallback, useEffect, useMemo, useState } from "react";
import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { updateField } from "utils/fieldHelpers";
import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import {
  LLMFormFieldsEvents,
  LLMFormFieldsMachineEventTypes,
} from "./LLMFormFields/state";

export interface LLMInstructionsWithPromptPickerProps {
  task: Partial<TaskDef>;
  onChange: (task: Partial<TaskDef>) => void;
  actor: ActorRef<LLMFormFieldsEvents>;
}

export const LLMInstructionsWithPromptPicker = ({
  task,
  onChange,
  actor,
}: LLMInstructionsWithPromptPickerProps) => {
  const promptNames = useSelector(
    actor,
    (state) => state.context.promptNameOptions,
  );
  const promptOptions = useMemo(
    () => promptNames.map(({ name }: { name: string }) => name),
    [promptNames],
  );

  const instructions =
    (_path("inputParameters.instructions", task) as string) || "";
  const allowRawPrompts = _path(
    "inputParameters.allowRawPrompts",
    task,
  ) as boolean;
  const currentVariables = task.inputParameters?.promptVariables || {};

  const isUsingPrompt =
    !allowRawPrompts && promptOptions.includes(instructions);
  const [customExpanded, setCustomExpanded] = useState(false);

  // Auto-expand custom instructions when no prompt registry or using raw text
  useEffect(() => {
    if (promptOptions.length === 0 || (instructions && !isUsingPrompt)) {
      setCustomExpanded(true);
    }
  }, [promptOptions.length, instructions, isUsingPrompt]);

  const handleSelectPrompt = useCallback(
    (value: unknown) => {
      setCustomExpanded(false);
      actor.send({
        type: LLMFormFieldsMachineEventTypes.SELECT_INSTRUCTIONS,
        task: updateField(
          `inputParameters.${UiIntegrationsFieldType.INSTRUCTIONS}`,
          value,
          task,
        ),
      });
    },
    [actor, task],
  );

  const handleCustomInstructions = useCallback(
    (value: string) => {
      let updated = updateField("inputParameters.instructions", value, task);
      updated = updateField("inputParameters.allowRawPrompts", true, updated);
      updated = updateField("inputParameters.promptVariables", {}, updated);
      onChange(updated);
    },
    [onChange, task],
  );

  const toggleCustom = useCallback(() => {
    setCustomExpanded((prev) => !prev);
  }, []);

  return (
    <Grid container spacing={3} sx={{ width: "100%" }}>
      {/* Primary: AI Prompt picker */}
      <Grid size={12}>
        <MuiTypography sx={{ opacity: 0.5, mb: 2 }}>
          Select a saved AI Prompt or{" "}
          <Link
            sx={{ fontWeight: 400 }}
            target="_blank"
            href="/ai_prompts/new_ai_prompt_model"
            rel="noreferrer"
          >
            create a new one.
          </Link>
        </MuiTypography>
        <ConductorAutocompleteVariables
          openOnFocus
          onChange={handleSelectPrompt}
          value={isUsingPrompt ? instructions : ""}
          otherOptions={promptOptions}
          label="AI Prompt"
          placeholder="Select a saved AI Prompt..."
          onFocus={() =>
            actor.send({
              type: LLMFormFieldsMachineEventTypes.FOCUS_PROMPT_NAMES,
              task,
            })
          }
        />
      </Grid>

      {/* Prompt variables (visible when a saved prompt is selected) */}
      {isUsingPrompt &&
        typeof currentVariables === "object" &&
        Object.keys(currentVariables).length > 0 && (
          <Grid size={12}>
            <MuiTypography sx={{ opacity: 0.5, mb: 1 }}>
              Prompt variables
            </MuiTypography>
            <PromptVariables
              currentVariables={currentVariables}
              onChange={onChange}
              updateField={updateField}
              task={task}
            />
          </Grid>
        )}

      {/* Divider */}
      <Grid size={12}>
        <Divider sx={{ my: 1 }}>
          <Typography variant="caption" sx={{ opacity: 0.5 }}>
            or
          </Typography>
        </Divider>
      </Grid>

      {/* Secondary: custom instructions (collapsible) */}
      <Grid size={12}>
        <Box
          onClick={toggleCustom}
          sx={{
            display: "flex",
            alignItems: "center",
            cursor: "pointer",
            userSelect: "none",
            mb: customExpanded ? 2 : 0,
          }}
        >
          {customExpanded ? (
            <CaretDown size={16} weight="bold" />
          ) : (
            <CaretRight size={16} weight="bold" />
          )}
          <MuiTypography sx={{ ml: 1, fontWeight: 500 }}>
            Write custom instructions
          </MuiTypography>
        </Box>
        <Collapse in={customExpanded}>
          <ConductorInput
            label="Instructions"
            name="custom-instructions"
            value={isUsingPrompt ? "" : instructions}
            onTextInputChange={handleCustomInstructions}
            multiline
            rows={6}
            fullWidth
            placeholder="You are a helpful assistant. Be concise..."
            helperText="System prompt sent as a system message before the Structured Messages below."
          />
        </Collapse>
      </Grid>
    </Grid>
  );
};
