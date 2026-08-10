import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import {
  WorkflowMetadataEvents,
  WorkflowMetadataMachineEventTypes,
} from "pages/definition/WorkflowMetadata/state";
import { SchemaFormValue } from "../../TaskFormTab/forms/SchemaForm";

export const useWorkflowMetadataEditorActor = (
  metadataEditorActor: ActorRef<WorkflowMetadataEvents>,
) => {
  const [
    inputParametersActor,
    outputParametersActors,
    restartableActors,
    timeoutSecondsActors,
    timeoutPolicyActors,
    failureWorkflowActors,
    nameFieldActor,
    descriptionFieldActor,
    inputSchemaFieldActor,
    outputSchemaFieldActor,
    enforceSchemaFieldActor,
    workflowStatusListenerEnabledActor,
    workflowStatusListenerSinkActor,
    rateLimitConfigActor,
  ] = useSelector(
    metadataEditorActor,
    (state) => state.context.editableFieldActors,
  );

  const isReady = useSelector(metadataEditorActor, (state) =>
    state.hasTag("editingEnabled"),
  );

  return [
    {
      inputParametersActor,
      outputParametersActors,
      restartableActors,
      timeoutSecondsActors,
      timeoutPolicyActors,
      failureWorkflowActors,
      isReady,
      nameFieldActor,
      descriptionFieldActor,
      inputSchemaFieldActor,
      outputSchemaFieldActor,
      enforceSchemaFieldActor,
      workflowStatusListenerEnabledActor,
      workflowStatusListenerSinkActor,
      rateLimitConfigActor,
    },
  ];
};

export const useWorkflowMetadata = (
  metadataEditorActor: ActorRef<WorkflowMetadataEvents>,
) => {
  const wUpdateTime = useSelector(
    metadataEditorActor,
    (state) => state.context?.metadataChanges?.updateTime,
  );
  const ownerEmail = useSelector(
    metadataEditorActor,
    (state) => state.context?.metadataChanges?.ownerEmail,
  );
  const currentWorkflowName = useSelector(
    metadataEditorActor,
    (state) => state.context.metadata.name,
  );

  const workflowStatusListenerEnabled = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges.workflowStatusListenerEnabled,
  );

  const fastAppCreation = useSelector(metadataEditorActor, (state) =>
    state.hasTag("fastAppCreation"),
  );

  const installScriptMetadata = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges?.metadata?.installScript,
  );
  const readmeMetadata = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges?.metadata?.readme,
  );

  const inputSchema = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges?.inputSchema,
  );
  const outputSchema = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges?.outputSchema,
  );
  const enforceSchema = useSelector(
    metadataEditorActor,
    (state) => state.context.metadataChanges?.enforceSchema,
  );

  const removeMetadataAttribs = () => {
    metadataEditorActor.send({
      type: WorkflowMetadataMachineEventTypes.UPDATE_METADATA,
      metadataChanges: {
        metadata: {},
      },
    });
  };
  const updateSchemaForm = (
    inputSchema?: SchemaFormValue,
    outputSchema?: SchemaFormValue,
    enforceSchema?: boolean,
  ) => {
    metadataEditorActor.send({
      type: WorkflowMetadataMachineEventTypes.UPDATE_METADATA,
      metadataChanges: {
        inputSchema: inputSchema as unknown as Record<string, unknown>,
        outputSchema: outputSchema as unknown as Record<string, unknown>,
        enforceSchema: enforceSchema as unknown as boolean,
      },
    });
  };

  return [
    {
      wUpdateTime,
      ownerEmail,
      currentWorkflowName,
      workflowStatusListenerEnabled,
      fastAppCreation,
      installScriptMetadata,
      readmeMetadata,
      inputSchema,
      outputSchema,
      enforceSchema,
    },
    {
      removeMetadataAttribs,
      updateSchemaForm,
    },
  ] as const;
};
