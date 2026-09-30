import { createJsonValidator } from "components/ui/CodeTab";
import { Action, Evaluator } from "./FormComponent/state/types";

const KNOWN_ACTIONS = new Set<string>(Object.values(Action));
const EVALUATOR_TYPES = Object.values(Evaluator) as string[];
// The server splits the event at its first colon into a queue type and a
// queue URI (EventQueues.getQueue), so `type:queue` is the only hard shape.
const EVENT_PATTERN = /^[\w-]+:.+$/;

/** Light EventHandler checks for the Code tab, on top of JSON parsing. */
export const validateEventHandlerJson = createJsonValidator(
  (handler, { report }) => {
    if (!handler.name || !String(handler.name).trim()) {
      report("error", "name", "name is required.");
    }
    if (!handler.event) {
      report("error", "event", "event is required.");
    } else if (!EVENT_PATTERN.test(String(handler.event))) {
      report(
        "warn",
        "event",
        "event should follow type:queue, e.g. sqs:my_queue.",
      );
    }
    if (
      handler.evaluatorType &&
      !EVALUATOR_TYPES.includes(handler.evaluatorType)
    ) {
      report(
        "warn",
        "evaluatorType",
        `evaluatorType should be one of ${EVALUATOR_TYPES.join(", ")}.`,
      );
    }
    if (!Array.isArray(handler.actions) || handler.actions.length === 0) {
      report("error", "actions", "Add at least one action.");
    } else {
      handler.actions.forEach((a: any) => {
        if (!KNOWN_ACTIONS.has(a?.action)) {
          report("warn", "action", `Unknown action "${a?.action}".`);
        }
      });
    }
    if (!handler.description) {
      report(
        "info",
        "description",
        "A description helps others find this handler.",
      );
    }
  },
  { documentName: "Event handler" },
);
