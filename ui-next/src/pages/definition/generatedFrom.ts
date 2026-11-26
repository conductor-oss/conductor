import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./state/types";

/**
 * The code a native workflow's definition is generated from, or undefined for a definition made
 * here. `metadata.native.source` is set by every SDK in its own language's notation (Java
 * `com.acme.Orders#ship`, Python `orders.flows:ship`); `class` and `method` are what older Java
 * SDKs set. The SDK registers such a definition again from its code, so the editor shows it read
 * only.
 */
export const generatedFrom = (workflow?: any): string | undefined => {
  const native = workflow?.metadata?.native;
  if (!native) {
    return undefined;
  }
  if (native.source) {
    return String(native.source);
  }
  return native.class && native.method
    ? `${native.class}.${native.method}`
    : (workflow?.name ?? "code");
};

/** Where the definition being edited is generated from, if it is generated from code. */
export const useGeneratedFrom = (
  definitionActor?: ActorRef<WorkflowDefinitionEvents>,
): string | undefined =>
  generatedFrom(
    useSelector(definitionActor!, (state: any) => state?.context?.currentWf),
  );
