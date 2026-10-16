/**
 * Version pin for a saved AI Prompt on LLM_CHAT_COMPLETE / LLM_TEXT_COMPLETE.
 *
 * The task stores only the prompt's name, so at run time the server resolves it to
 * whatever is latest unless `inputParameters.promptVersion` says otherwise. "Latest"
 * is therefore the absence of that key, which is exactly what every workflow written
 * before this field already looks like.
 *
 * Each version declares its own variables, so changing the pin re-populates
 * inputParameters.promptVariables from the chosen version — otherwise the task keeps
 * the latest version's variables while running an older prompt.
 *
 * The field sits beside the prompt picker for as long as AI_PROMPTS_VERSIONING is on, so
 * the row never reflows while a prompt is being chosen. Until the referenced name turns
 * out to be a saved prompt it simply has nothing to show, and stays empty. With the flag
 * off it does not render at all and the picker takes the whole row; an existing pin is
 * left untouched, so a definition authored where versioning is on survives a round trip
 * through a deployment where it is not.
 */
import { Grid } from "@mui/material";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { useMemo } from "react";
import { TaskDef } from "types";
import { useFetch } from "utils/query";

import {
  isPromptVersioningEnabled,
  VERSION_FIELD_COLUMNS,
} from "./promptVersioning";

/**
 * The select's value for "no pin". A non-empty sentinel, because MUI reads "" as
 * "nothing selected" and would render the field blank instead of showing "Latest".
 * It is never written to the task — an unpinned task simply has no promptVersion.
 */
const LATEST = "latest";

/** MUI's own "nothing selected", used while there is no prompt to offer versions for. */
const NOTHING = "";

export interface PromptVersionSelectProps {
  task: Partial<TaskDef>;
  onChange: (task: Partial<TaskDef>) => void;
  /** The prompt the task references; raw prompt text is fine, it simply finds no versions. */
  promptName: string;
}

/** One entry of GET /prompts/{name}/versions. */
type PromptTemplateVersion = { version: number; variables?: string[] };

export const PromptVersionSelect = ({
  task,
  onChange,
  promptName,
}: PromptVersionSelectProps) => {
  const isVersioningEnabled = isPromptVersioningEnabled();

  // Raw prompt text simply 404s here, which is how a raw prompt is told apart from a
  // saved one; there is nothing to retry.
  const { data, isPreviousData } = useFetch<PromptTemplateVersion[]>(
    `/prompts/${promptName}/versions`,
    { when: isVersioningEnabled && !!promptName, retry: false },
  );

  // useFetch keeps the previous prompt's data while the next one loads, and holds on to
  // it when the query is switched off entirely — so clearing the prompt would otherwise
  // leave the cleared prompt's versions on offer.
  const templates = useMemo(
    () =>
      isPreviousData
        ? []
        : [...(data ?? [])].sort((a, b) => b.version - a.version),
    [data, isPreviousData],
  );

  const pinned = task.inputParameters?.promptVersion;

  // A pin already on the task stays selectable while the lookup is in flight, so the
  // field never blanks out and misreports which version will run.
  const selectable = useMemo(
    () =>
      templates.length || typeof pinned !== "number"
        ? templates.map(({ version }) => version)
        : [pinned],
    [templates, pinned],
  );

  const items = useMemo(
    () =>
      selectable.length
        ? [
            { label: "Latest", value: LATEST },
            ...selectable.map((version) => ({
              label: String(version),
              value: version,
            })),
          ]
        : [],
    [selectable],
  );

  if (!isVersioningEnabled) {
    return null;
  }

  /**
   * Latest means the highest version and no stored pin, so both resolve to the same
   * template. Values already typed against a variable the version still declares are
   * kept; anything it drops goes with it.
   */
  const applyVersion = (value: string) => {
    if (value === NOTHING) {
      return;
    }
    const version = value === LATEST ? undefined : Number(value);
    const chosen =
      version === undefined
        ? templates[0]
        : templates.find((template) => template.version === version);

    const inputParameters: Record<string, unknown> = {
      ...(task.inputParameters ?? {}),
    };
    if (version === undefined) {
      delete inputParameters.promptVersion;
    } else {
      inputParameters.promptVersion = version;
    }
    if (chosen?.variables) {
      const current = (inputParameters.promptVariables ?? {}) as Record<
        string,
        unknown
      >;
      inputParameters.promptVariables = Object.fromEntries(
        chosen.variables.map((name) => [name, current[name] ?? ""]),
      );
    }
    onChange({ ...task, inputParameters } as Partial<TaskDef>);
  };

  return (
    <Grid size={{ xs: 12, md: VERSION_FIELD_COLUMNS }}>
      <ConductorSelect
        id="prompt-version-field"
        fullWidth
        label="Version"
        value={selectable.length ? (pinned ?? LATEST) : NOTHING}
        items={items}
        onChange={(event) => applyVersion(String(event.target.value))}
      />
    </Grid>
  );
};

export default PromptVersionSelect;
