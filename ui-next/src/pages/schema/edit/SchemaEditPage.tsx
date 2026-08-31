import { yupResolver } from "@hookform/resolvers/yup";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Stack,
} from "@mui/material";
import { TrashIcon as DeleteIcon } from "@phosphor-icons/react";
import fastDeepEqual from "fast-deep-equal";
import { useCallback, useEffect, useMemo } from "react";
import { Helmet } from "react-helmet";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { useParams } from "react-router";
import * as yup from "yup";

import BlockNavigationWithConfirmation from "components/BlockNavigationWithConfirmation";
import { ProgressHeading } from "components";
import DownloadIcon from "components/icons/DownloadIcon";
import ResetIcon from "components/icons/ResetIcon";
import SaveIcon from "components/icons/SaveIcon";
import { ConductorSectionHeader } from "components/layout/section/ConductorSectionHeader";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import ReactHookFormEditor from "components/ui/react-hook-form/ReactHookFormEditor";
import SectionContainer from "components/ui/layout/SectionContainer";
import { defaultEditorOptions } from "shared/editor";
import { SCHEMAS_URL } from "utils/constants/route";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useReplaceHistory } from "utils/hooks/useReplaceHistory";
import { NEW_SCHEMA_URL_PARAM, schemaEditPath } from "../paths";
import { useSchemaEdit } from "./state/hook";
import { SchemaEditorFormValues } from "./state/types";

const SchemaFormSchema: yup.ObjectSchema<SchemaEditorFormValues> = yup
  .object()
  .shape({
    editor: yup.string().required(),
  });

export const SchemaEditPage = () => {
  const { schemaName: schemaNameURLParam, version: versionURLParam } =
    useParams<{ schemaName: string; version?: string }>();

  const pushHistory = usePushHistory();
  const replaceHistory = useReplaceHistory();

  const isNewSchema = schemaNameURLParam === NEW_SCHEMA_URL_PARAM;

  const schemaDisplayName = isNewSchema
    ? "New Schema"
    : schemaNameURLParam || "";

  const handleGoToSchema = useCallback(
    (schemaName: string, schemaVersion?: number) => {
      pushHistory(schemaEditPath(schemaName, schemaVersion));
    },
    [pushHistory],
  );

  const handleGoToList = useCallback(
    () => pushHistory(SCHEMAS_URL.BASE),
    [pushHistory],
  );

  const handleSaved = useCallback(
    (schemaName: string, schemaVersion?: number) => {
      replaceHistory(schemaEditPath(schemaName, schemaVersion));
    },
    [replaceHistory],
  );

  const formMethods = useForm<SchemaEditorFormValues>({
    mode: "onChange",
    defaultValues: { editor: "" },
    resolver: yupResolver(SchemaFormSchema),
  });

  const [
    {
      showSaveSchemaConfirmationModal,
      confirmDeleteVersion,
      availableVersions,
      currentVersion,
      schema,
      isFetching,
      isFetchError,
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
  ] = useSchemaEdit({
    schemaNameURLParam: schemaNameURLParam || "",
    versionURLParam,
    isNewSchema,
    onGoToSchema: handleGoToSchema,
    onGoToList: handleGoToList,
    onSuccess: handleSaved,
    formMethods,
  });

  // A new schema has nothing to fetch, so seed the editor with its template.
  useEffect(() => {
    if (isNewSchema && !formMethods.getValues("editor")) {
      formMethods.reset({ editor: JSON.stringify(schema, null, 2) });
    }
  }, [isNewSchema, schema, formMethods]);

  const currentEditorValue = useWatch({
    control: formMethods.control,
    name: "editor",
    defaultValue: "",
  });

  const noFormChanges = useMemo(() => {
    if (successfulSave === true) {
      return true;
    }
    if (!formMethods.formState.isDirty) {
      return true;
    }
    try {
      return fastDeepEqual(JSON.parse(currentEditorValue || ""), schema);
    } catch {
      // Unparseable text is a change: it is not what was loaded.
      return false;
    }
  }, [
    currentEditorValue,
    schema,
    formMethods.formState.isDirty,
    successfulSave,
  ]);

  const saveDisabled =
    isReadOnly ||
    isFetchError ||
    (!formMethods.formState.isDirty && !isNewSchema);

  return (
    <FormProvider {...formMethods}>
      <Box id="schema-editor">
        <Helmet>
          <title>Schema - {schemaDisplayName}</title>
        </Helmet>

        <BlockNavigationWithConfirmation
          nonBlockPaths={[`${SCHEMAS_URL.BASE}/.*`]}
          promptMessage={
            <>
              Your recent changes are not saved to the server. To use the new
              schema, you have to save your progress.
            </>
          }
          title={"Unsaved schema confirmation"}
          block={!noFormChanges && !isSaving}
          onSave={() => handleSaveButtonClicked()}
          successfulSave={successfulSave}
        />

        <SectionContainer
          header={
            <ConductorSectionHeader
              id="schema-editor-header-section"
              title={schemaDisplayName}
              breadcrumbItems={[
                { label: "Schemas", to: SCHEMAS_URL.BASE },
                { label: schemaDisplayName, to: "" },
              ]}
              versionSelector={
                isNewSchema
                  ? undefined
                  : {
                      current: !versionURLParam ? -1 : currentVersion || 0,
                      available: availableVersions,
                      onChange: handleChangeVersion,
                    }
              }
              buttonsComponent={
                <Stack
                  display="flex"
                  gap={2}
                  flexDirection="row"
                  marginRight={3.5}
                >
                  {!isNewSchema && (
                    <Button
                      id="delete-schema-version-btn"
                      onClick={handleOpenConfirmDeleteVersion}
                      disabled={isFetchError || !currentVersion}
                      variant="text"
                      color="inherit"
                      startIcon={<DeleteIcon size={20} />}
                    >
                      Delete version
                    </Button>
                  )}

                  <Button
                    disabled={!formMethods.formState.isDirty || isFetchError}
                    onClick={handleResetSchema}
                    variant="text"
                    startIcon={<ResetIcon />}
                  >
                    Reset
                  </Button>

                  <Button
                    variant="text"
                    onClick={handleDownloadFile}
                    startIcon={<DownloadIcon />}
                  >
                    Download
                  </Button>

                  <SplitButton
                    id="schema-save-btn"
                    disabled={saveDisabled}
                    options={[
                      {
                        label: "Save as new version",
                        onClick: () => handleSaveButtonClicked(true),
                      },
                    ]}
                    primaryOnClick={() => handleSaveButtonClicked()}
                    startIcon={<SaveIcon />}
                    tooltip="Save this version"
                  >
                    Save
                  </SplitButton>
                </Stack>
              }
            />
          }
        >
          <Paper sx={{ height: "calc(100vh - 80px)" }}>
            <ProgressHeading loading={isSaving} />
            {readOnlyReason && (
              <Alert severity="info" id="schema-read-only-notice">
                {readOnlyReason}
              </Alert>
            )}
            {isFetching ? (
              <Box
                sx={{
                  height: "100%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <CircularProgress size={20} />
              </Box>
            ) : isFetchError ? null : (
              <Box
                sx={{
                  maxWidth: "820px",
                  flex: "0 0 auto",
                  position: "relative",
                  width: "100%",
                  height: "100%",
                  border: "1px solid #aaaaaa",
                  borderTop: "1px solid rgba(0,0,0,.2)",
                }}
              >
                <Box
                  sx={{
                    display: "flex",
                    flexFlow: "column",
                    height: "100%",
                    overflowX: "auto",
                    minWidth: 590,
                  }}
                >
                  <ReactHookFormEditor
                    control={formMethods.control}
                    name="editor"
                    height="100%"
                    width="100%"
                    language="json"
                    options={{
                      ...defaultEditorOptions,
                      readOnly: isReadOnly,
                      selectOnLineNumbers: true,
                      minimap: { enabled: false },
                    }}
                  />
                </Box>
              </Box>
            )}
          </Paper>
        </SectionContainer>

        {showSaveSchemaConfirmationModal.open && (
          <ConfirmChoiceDialog
            handleConfirmationValue={(confirmed) => {
              if (confirmed) {
                handleSaveSchema(
                  showSaveSchemaConfirmationModal.saveAsNewVersion,
                );
              }
              handleHideSaveSchemaConfirmationModal();
            }}
            message="You are overwriting a version that workflows or tasks may already reference. To avoid changing a contract they depend on, consider saving this as a new version instead."
          />
        )}

        {confirmDeleteVersion && (
          <ConfirmChoiceDialog
            header="Deletion confirmation"
            handleConfirmationValue={(confirmed) => {
              if (confirmed) {
                handleDeleteVersion();
              } else {
                handleCloseConfirmDeleteVersion();
              }
            }}
            message={
              <>
                Are you sure you want to delete version{" "}
                <strong style={{ color: "red" }}>{currentVersion}</strong> of{" "}
                <strong>{schemaDisplayName}</strong>? Its other versions are
                kept. This cannot be undone.
              </>
            }
          />
        )}
      </Box>
    </FormProvider>
  );
};
