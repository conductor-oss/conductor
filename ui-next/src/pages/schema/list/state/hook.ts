import _ from "lodash";
import { useCallback, useMemo, useState } from "react";

import { SchemaDefinition } from "types/SchemaDefinition";
import { SCHEMAS_URL } from "utils/constants/route";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { useGetSchemas } from "utils/hooks/useGetSchemas";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useToastMessage } from "utils/hooks/useToastMessage";
import { useActionWithPath } from "utils/query";
import { getErrors } from "utils/utils";

/**
 * A row stands for a schema, not for one of its versions: the latest version's
 * body, plus every version that exists under that name so the table can say how
 * much history there is.
 */
export type SchemaListRow = SchemaDefinition & {
  versions: number[];
};

export const useSchemaList = () => {
  const pushHistory = usePushHistory();
  const { toastMessage } = useToastMessage();
  const { data, isFetching, refetch } = useGetSchemas();
  const [{ searchParam }, { setSearchParam }] = useCustomPagination();

  const [cloneDialogData, setCloneDialogData] = useState<SchemaListRow>();
  const [confirmDeleteDialogData, setConfirmDeleteDialogData] =
    useState<SchemaListRow>();

  const reportError = useCallback(
    (operation: string) => async (response: Response) => {
      const errors = await getErrors(response);
      toastMessage({
        text: errors.message || `Error ${operation} schema`,
        severity: "error",
      });
      refetch();
    },
    [refetch, toastMessage],
  );

  const deleteSchemaAction = useActionWithPath({
    onSuccess: () => {
      toastMessage({ text: "Schema deleted", severity: "success" });
      refetch();
    },
    onError: reportError("deleting"),
  });

  const cloneSchemaAction = useActionWithPath({
    onSuccess: () => {
      toastMessage({ text: "Schema cloned", severity: "success" });
      refetch();
    },
    onError: reportError("cloning"),
  });

  const tableData = useMemo(
    (): SchemaListRow[] =>
      _.chain(data)
        .groupBy("name")
        .map((group): SchemaListRow | undefined => {
          const latest = _.maxBy(group, "version");
          if (!latest) {
            return undefined;
          }
          return {
            ...latest,
            versions: _.chain(group).map("version").sortBy().value(),
          };
        })
        .compact()
        .value(),
    [data],
  );

  const schemaNameList: string[] = useMemo(
    () => tableData.map((schema) => schema.name),
    [tableData],
  );

  // Either action leaves the table showing data the server has moved on from.
  const isMutating =
    deleteSchemaAction.isLoading || cloneSchemaAction.isLoading;

  const handleGoToNewSchema = useCallback(() => {
    pushHistory(SCHEMAS_URL.DEF);
  }, [pushHistory]);

  const handleUpdateSearchParam = useCallback(
    (text: string) => setSearchParam(text),
    [setSearchParam],
  );

  const handleResetSearchParam = useCallback(
    () => setSearchParam(""),
    [setSearchParam],
  );

  const handleRefreshTable = useCallback(() => {
    refetch();
  }, [refetch]);

  const handleOpenCloneDialog = useCallback(
    (schema: SchemaListRow) => setCloneDialogData(schema),
    [],
  );

  const handleCloseCloneDialog = useCallback(
    () => setCloneDialogData(undefined),
    [],
  );

  const handleOpenConfirmDeleteDialog = useCallback(
    (schema: SchemaListRow) => setConfirmDeleteDialogData(schema),
    [],
  );

  const handleCloseConfirmDeleteDialog = useCallback(
    () => setConfirmDeleteDialogData(undefined),
    [],
  );

  /**
   * Deletes every version under the name. A row stands for the schema, so the
   * row's delete retires the schema; a single version is deleted from the
   * editor, where the version being viewed is unambiguous.
   */
  const handleDeleteSchema = useCallback(() => {
    if (!confirmDeleteDialogData) {
      return;
    }
    deleteSchemaAction.mutate({
      method: "delete",
      path: `/schema/${encodeURIComponent(confirmDeleteDialogData.name)}`,
    });
    handleCloseConfirmDeleteDialog();
  }, [
    confirmDeleteDialogData,
    deleteSchemaAction,
    handleCloseConfirmDeleteDialog,
  ]);

  /**
   * A clone starts its own history at version 1 rather than inheriting the
   * version it was copied from, and carries none of the original's audit
   * timestamps.
   */
  const handleCloneSchema = useCallback(
    ({ name }: { name: string }) => {
      if (!cloneDialogData) {
        return;
      }
      cloneSchemaAction.mutate({
        method: "post",
        path: `/schema`,
        body: JSON.stringify([
          {
            name,
            version: 1,
            type: cloneDialogData.type,
            data: cloneDialogData.data,
            ...(cloneDialogData.externalRef
              ? { externalRef: cloneDialogData.externalRef }
              : {}),
          },
        ]),
      });
      handleCloseCloneDialog();
    },
    [cloneDialogData, cloneSchemaAction, handleCloseCloneDialog],
  );

  return [
    {
      tableData,
      isFetching,
      isMutating,
      cloneDialogData,
      confirmDeleteDialogData,
      searchParam,
      schemaNameList,
    },
    {
      handleGoToNewSchema,
      handleRefreshTable,
      handleUpdateSearchParam,
      handleResetSearchParam,
      handleOpenCloneDialog,
      handleCloseCloneDialog,
      handleOpenConfirmDeleteDialog,
      handleCloseConfirmDeleteDialog,
      handleCloneSchema,
      handleDeleteSchema,
    },
  ] as const;
};
