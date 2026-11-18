import {
  ControlElement,
  createAjv,
  isVisible,
  INIT,
  UPDATE_CORE,
  Middleware,
  Layout,
  toDataPathSegments,
  UISchemaElement,
} from "@jsonforms/core";
import has from "lodash/has";
import unset from "lodash/unset";

export const formAjv = createAjv({ useDefaults: true });

// AJV fills defaults by mutation. Keep external task inputs intact so the
// workflow editor can detect and save the values emitted by JSON Forms.
export const protectTaskInputs: Middleware = (state, action, reduce) => {
  if (
    action.type === INIT ||
    (action.type === UPDATE_CORE &&
      (action.data !== state.data || action.schema !== state.schema))
  ) {
    return reduce(state, { ...action, data: structuredClone(action.data) });
  }
  return reduce(state, action);
};

// Submit only values relevant to the library-provided visibility rules.
export function retainVisibleInputs(
  uiSchema: UISchemaElement | undefined,
  data: unknown,
) {
  if (!uiSchema || !data || typeof data !== "object" || Array.isArray(data))
    return data;
  let result = data;
  function visit(element: UISchemaElement, parentHidden = false) {
    const hidden =
      parentHidden || !isVisible(element, data, "", formAjv, undefined);
    if (hidden && element.type === "Control") {
      const path = toDataPathSegments((element as ControlElement).scope);
      if (has(result, path)) {
        if (result === data) result = structuredClone(data);
        unset(result, path);
      }
    }
    for (const child of (element as Layout).elements ?? [])
      visit(child, hidden);
  }
  visit(uiSchema);
  return result;
}
