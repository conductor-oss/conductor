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
import { TaskDef } from "types";
import { ActorRef } from "xstate";
import { LLMFormFieldsEvents } from "./LLMFormFields/state";
export interface LLMInstructionsWithPromptPickerProps {
    task: Partial<TaskDef>;
    onChange: (task: Partial<TaskDef>) => void;
    actor: ActorRef<LLMFormFieldsEvents>;
}
export declare const LLMInstructionsWithPromptPicker: ({ task, onChange, actor, }: LLMInstructionsWithPromptPickerProps) => import("react").JSX.Element;
