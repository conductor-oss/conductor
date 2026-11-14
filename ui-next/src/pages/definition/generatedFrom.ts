import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./state/types";

/**
 * The code a native workflow's definition is generated from (`Class.method`), or undefined. The
 * SDK registers such a definition again from its code, so the editor shows it read only.
 */
export const generatedFrom = (workflow?: any): string | undefined => {
  const native = workflow?.metadata?.native;
  if (!native) {
    return undefined;
  }
  return native.class && native.method
    ? `${native.class}.${native.method}`
    : (native.codeType ?? workflow?.name ?? "code");
};

/** Where the definition being edited is generated from, if it is generated from code. */
export const useGeneratedFrom = (
  definitionActor?: ActorRef<WorkflowDefinitionEvents>,
): string | undefined =>
  generatedFrom(
    useSelector(definitionActor!, (state: any) => state?.context?.currentWf),
  );
