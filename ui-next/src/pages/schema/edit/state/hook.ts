import _ from "lodash";
import { useCallback, useMemo, useState } from "react";
import { UseFormReturn } from "react-hook-form";

import { SchemaDefinition } from "types/SchemaDefinition";
import { exportObjToFile } from "utils/helpers";
import { useEntityAvailableVersions } from "utils/hooks/useEntityAvailableVersions";
import { useToastMessage } from "utils/hooks/useToastMessage";
import { useActionWithPath, useFetch } from "utils/query";
import { randomChars } from "utils/strings";
import { getErrors, tryToJson } from "utils/utils";
import { SchemaEditorFormValues } from "./types";

/**
 * The only type this editor writes. The registry stores AVRO and PROTOBUF
 * schemas too, and this server does not validate them, so the editor shows them
 * read-only rather than pretending to enforce them.
 */
const EDITABLE_TYPE = "JSON";

/**
 * What the editor holds: a stored schema, or the template for one that does not
 * exist yet and therefore has no version.
 */
type EditableSchema = Omit<SchemaDefinition, "version"> & { version?: number };

export interface SchemaEditHookInput {
  schemaNameURLParam: string;
  versionURLParam?: string;
  isNewSchema: boolean;
  onGoToSchema: (schemaName: string, schemaVersion?: number) => void;
  onGoToList: () => void;
  onSuccess?: (schemaName: string, schemaVersion?: number) => void;
  formMethods: UseFormReturn<SchemaEditorFormValues>;
}

export const useSchemaEdit = ({
  schemaNameURLParam,
  versionURLParam,
  isNewSchema,
  onGoToSchema,
  onGoToList,
  onSuccess,
  formMethods,
}: SchemaEditHookInput) => {
  const { toastMessage } = useToastMessage();

  const reportError = useCallback(
    (fallback: string) => async (response: Response) => {
      const errors = await getErrors(response);
      toastMessage({ text: errors.message || fallback, severity: "error" });
    },
    [toastMessage],
  );

  const [showSaveSchemaConfirmationModal, setShowSaveSchemaConfirmationModal] =
    useState<{ open: boolean; saveAsNewVersion?: boolean }>({ open: false });
  const [confirmDeleteVersion, setConfirmDeleteVersion] = useState(false);
  const [successfulSave, setSuccessfulSave] = useState<boolean | undefined>(
    undefined,
  );

  const {
    availableVersions,
    refetchAvailableVersions,
    isFetchingAvailableVersions,
  } = useEntityAvailableVersions({
    url: "/schema",
    name: schemaNameURLParam,
  });

  const currentVersion = useMemo(() => {
    if (versionURLParam) {
      return Number(versionURLParam);
    }
    return _.last(availableVersions);
  }, [availableVersions, versionURLParam]);

  const newSchemaTemplate: EditableSchema = useMemo(
    () => ({
      name: `schema-${randomChars(6)}`,
      type: EDITABLE_TYPE,
      data: {
        $schema: "http://json-schema.org/draft-07/schema",
        type: "object",
        properties: {
          firstName: { type: "string" },
          amount: { type: "integer" },
        },
        required: ["firstName", "amount"],
      },
    }),
    [],
  );

  const {
    data,
    isFetching,
    refetch,
    isError: isFetchError,
  } = useFetch<SchemaDefinition>(
    `/schema/${encodeURIComponent(schemaNameURLParam)}/${currentVersion}`,
    {
      enabled: !isNewSchema && !!currentVersion,
      onSuccess: (value: SchemaDefinition) => {
        formMethods.reset({ editor: JSON.stringify(value, null, 2) });
      },
      onError: reportError("Error fetching schema"),
    },
  );

  /**
   * The template belongs to a new schema only. Handing it back for an existing one that
   * failed to load would let Reset overwrite the editor with an unrelated random schema,
   * and would have the dirty check diff a real schema against a template.
   */
  const schema: EditableSchema | undefined = useMemo(() => {
    if (isNewSchema) {
      return newSchemaTemplate;
    }
    return data;
  }, [data, isNewSchema, newSchemaTemplate]);

  /**
   * A name with no versions registered under it. The body fetch is keyed on a version, so
   * without one it never runs and neither isFetching nor isFetchError would say anything.
   */
  const isNotFound =
    !isNewSchema && !isFetchingAvailableVersions && !currentVersion;

  /**
   * A stored schema of a type this server cannot validate is shown as it is and
   * cannot be edited here, so nobody is left believing an AVRO or protobuf
   * schema is being enforced.
   */
  const readOnlyReason = useMemo(() => {
    if (isNewSchema || !data) {
      return undefined;
    }
    if (data.type && data.type !== EDITABLE_TYPE) {
      return `${data.type} schemas are stored but not validated by this server, and cannot be edited here. Use the API to replace this schema.`;
    }
    return undefined;
  }, [data, isNewSchema]);

  const isReadOnly = !!readOnlyReason;

  const isSameNameAndVersion = useCallback(
    (name?: string, version?: number) =>
      name === schemaNameURLParam && version === currentVersion,
    [currentVersion, schemaNameURLParam],
  );

  const saveSchemaAction = useActionWithPath({
    onSuccess: (_result: unknown, request: { body: string; path: string }) => {
      toastMessage({
        text: isNewSchema
          ? "Schema created successfully"
          : "Schema saved successfully",
        severity: "success",
      });
      const [saved] = JSON.parse(request.body);

      // The editor now matches what the server holds, so it is no longer dirty and the
      // unsaved-changes guard re-arms on the next keystroke. Done here rather than left to
      // the refetch below, which does not run when the name or version was edited.
      formMethods.reset({ editor: JSON.stringify(saved, null, 2) });

      refetchAvailableVersions();

      // POST returns no body and the server allocates the next version itself, so on a
      // new-version save the version in the request body is the one we came from, not the
      // one just written. Navigating to the name with no version segment means
      // currentVersion follows availableVersions to the latest once the refetch lands —
      // no client-side version arithmetic, and no race to await.
      const savedAsNewVersion = request.path.includes("newVersion=true");
      if (
        !savedAsNewVersion &&
        isSameNameAndVersion(saved.name, saved.version)
      ) {
        refetch();
      }
      onSuccess?.(saved.name, savedAsNewVersion ? undefined : saved.version);
      setSuccessfulSave(true);
    },
    onError: async (response: Response) => {
      await reportError("Error saving schema")(response);
      setSuccessfulSave(false);
    },
  });

  const deleteVersionAction = useActionWithPath({
    onSuccess: () => {
      toastMessage({ text: "Schema version deleted", severity: "success" });
      onGoToList();
    },
    onError: reportError("Error deleting schema version"),
  });

  const isSaving = saveSchemaAction.isLoading;

  /**
   * The body is a list, which is what the endpoint declares. A new version is
   * allocated by the server rather than computed here: two people saving at
   * once would otherwise pick the same number and one save would be lost.
   */
  const handleSaveSchema = useCallback(
    (saveAsNewVersion?: boolean) => {
      formMethods.handleSubmit(
        (fieldValues) => {
          const edited = {
            ...(schema ?? {}),
            ...JSON.parse(fieldValues.editor),
          };
          saveSchemaAction.mutate({
            method: "post",
            path: saveAsNewVersion ? `/schema?newVersion=true` : `/schema`,
            body: JSON.stringify([edited]),
          });
        },
        (errors) => {
          if (!!errors && Object.keys(errors).length > 0) {
            toastMessage({
              text: _.chain(errors).map("message").find().value(),
              severity: "error",
            });
            setSuccessfulSave(false);
          }
        },
      )();
    },
    [formMethods, saveSchemaAction, schema, toastMessage],
  );

  const handleResetSchema = useCallback(() => {
    if (!schema) {
      return;
    }
    formMethods.reset({ editor: JSON.stringify(schema, null, 2) });
  }, [formMethods, schema]);

  const handleChangeVersion = useCallback(
    (version: number) => {
      if (version === -1) {
        return onGoToSchema(schemaNameURLParam);
      }
      onGoToSchema(schemaNameURLParam, version);
    },
    [onGoToSchema, schemaNameURLParam],
  );

  const handleDownloadFile = useCallback(async () => {
    try {
      exportObjToFile({
        data: JSON.parse(formMethods.getValues("editor")),
        fileName: `${schemaNameURLParam || "new"}.json`,
        type: `application/json`,
      });
    } catch (error: any) {
      toastMessage({ text: error.message, severity: "error" });
    }
  }, [formMethods, schemaNameURLParam, toastMessage]);

  const handleHideSaveSchemaConfirmationModal = useCallback(
    () => setShowSaveSchemaConfirmationModal({ open: false }),
    [],
  );

  /**
   * Saving in place over the version being viewed changes the contract every
   * definition pinned to it already depends on, so that is the one save worth
   * confirming. Saving as a new version needs no confirmation.
   */
  const handleSaveButtonClicked = useCallback(
    (saveAsNewVersion?: boolean) => {
      const values = tryToJson<Partial<SchemaDefinition>>(
        formMethods.getValues("editor"),
      );
      if (!values) {
        toastMessage({ text: "Invalid JSON", severity: "error" });
        return;
      }
      if (values.type && values.type !== EDITABLE_TYPE) {
        toastMessage({
          text: `This editor only saves ${EDITABLE_TYPE} schemas. Use the API to register a ${values.type} schema.`,
          severity: "error",
        });
        return;
      }
      if (
        !saveAsNewVersion &&
        !isNewSchema &&
        isSameNameAndVersion(values.name, values.version)
      ) {
        setShowSaveSchemaConfirmationModal({ open: true, saveAsNewVersion });
        return;
      }
      handleSaveSchema(saveAsNewVersion);
    },
    [
      formMethods,
      handleSaveSchema,
      isNewSchema,
      isSameNameAndVersion,
      toastMessage,
    ],
  );

  const handleOpenConfirmDeleteVersion = useCallback(
    () => setConfirmDeleteVersion(true),
    [],
  );

  const handleCloseConfirmDeleteVersion = useCallback(
    () => setConfirmDeleteVersion(false),
    [],
  );

  const handleDeleteVersion = useCallback(() => {
    setConfirmDeleteVersion(false);
    deleteVersionAction.mutate({
      method: "delete",
      path: `/schema/${encodeURIComponent(schemaNameURLParam)}/${currentVersion}`,
    });
  }, [currentVersion, deleteVersionAction, schemaNameURLParam]);

  return [
    {
      showSaveSchemaConfirmationModal,
      confirmDeleteVersion,
      availableVersions,
      currentVersion,
      schema,
      isFetching: isFetching || isFetchingAvailableVersions,
      isFetchError,
      isNotFound,
      isSaving,
      isReadOnly,
      readOnlyReason,
      successfulSave,
    },
    {
      handleSaveSchema,
      handleSaveButtonClicked,
      handleHideSaveSchemaConfirmationModal,
      handleResetSchema,
      handleChangeVersion,
      handleDownloadFile,
      handleOpenConfirmDeleteVersion,
      handleCloseConfirmDeleteVersion,
      handleDeleteVersion,
    },
  ] as const;
};
