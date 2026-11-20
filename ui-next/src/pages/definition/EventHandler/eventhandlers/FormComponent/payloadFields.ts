import { createContext, useContext, useState } from "react";

/**
 * `${...}` references the user can pick in action fields, derived from the
 * sample payload in the Event section. The server resolves each one as a
 * JSONPath against the incoming event message (ParametersUtils.replace), so a
 * key `orderId` at the top of the message is `${orderId}` and a nested one is
 * `${customer.id}`.
 */
export const PayloadFieldsContext = createContext<string[]>([]);

export const usePayloadFields = () => useContext(PayloadFieldsContext);

/**
 * Always suggested in Workflow ID fields, sample or not. A Conductor EVENT
 * task adds it to every message it publishes (OSS Event.start); other senders
 * may not, so it is a starting point rather than a guarantee. Other fields get
 * only what the sample shows, since what they need is the message's own data.
 */
export const WORKFLOW_ID_FIELD = "${workflowInstanceId}";

/** Spread onto a ConductorFlatMapFormBase so its values suggest the fields. */
export const usePayloadMapProps = () => {
  const fields = usePayloadFields();
  return {
    enableAutocomplete: fields.length > 0,
    otherOptions: fields,
    includeWorkflowOptions: false,
  };
};

// Keys that need bracket notation are skipped rather than guessed at.
const IDENTIFIER = /^[A-Za-z_$][\w$]*$/;
const MAX_FIELDS = 200;
const MAX_DEPTH = 6;

export const payloadFieldPaths = (sample: unknown): string[] => {
  const paths: string[] = [];

  const walk = (value: unknown, path: string, depth: number) => {
    if (paths.length >= MAX_FIELDS || depth > MAX_DEPTH) return;
    if (Array.isArray(value)) {
      // One element is enough to show the shape of the items.
      if (value.length > 0) walk(value[0], `${path}[0]`, depth + 1);
      return;
    }
    if (value !== null && typeof value === "object") {
      for (const [key, child] of Object.entries(value)) {
        if (!IDENTIFIER.test(key)) continue;
        const childPath = path ? `${path}.${key}` : key;
        paths.push(childPath);
        walk(child, childPath, depth + 1);
      }
    }
  };

  walk(sample, "", 0);
  return paths.slice(0, MAX_FIELDS).map((path) => `\${${path}}`);
};

export const parseSamplePayload = (
  text: string,
): { fields: string[]; error?: string } => {
  if (!text.trim()) return { fields: [] };
  try {
    const parsed = JSON.parse(text);
    if (
      parsed === null ||
      typeof parsed !== "object" ||
      Array.isArray(parsed)
    ) {
      return { fields: [], error: "The sample payload must be a JSON object." };
    }
    return { fields: payloadFieldPaths(parsed) };
  } catch {
    return { fields: [], error: "Not valid JSON yet." };
  }
};

const storageKey = (event: string) =>
  `conductor.eventHandler.samplePayload:${event}`;

const readSample = (event?: string): string | null => {
  if (!event) return null;
  try {
    return localStorage.getItem(storageKey(event));
  } catch {
    return null;
  }
};

const writeSample = (event: string | undefined, text: string) => {
  if (!event) return;
  try {
    if (text.trim()) localStorage.setItem(storageKey(event), text);
    else localStorage.removeItem(storageKey(event));
  } catch {
    // Storage unavailable (private window, blocked): the sample just won't
    // survive a reload.
  }
};

/**
 * The sample payload is an editing aid, not part of the handler, so it is kept
 * in this browser only, keyed by event: handlers on the same event share a
 * message shape. Typing a new event keeps the current sample unless one was
 * already stored for that event.
 */
export const useSamplePayload = (event?: string) => {
  const [state, setState] = useState(() => ({
    event,
    text: readSample(event) ?? "",
  }));

  let text = state.text;
  if (state.event !== event) {
    text = readSample(event) ?? state.text;
    setState({ event, text });
  }

  const setText = (next: string) => {
    setState({ event, text: next });
    writeSample(event, next);
  };

  return [text, setText] as const;
};
