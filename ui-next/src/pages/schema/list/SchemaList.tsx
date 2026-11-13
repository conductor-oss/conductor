import { Box, Button, IconButton, Paper, Tooltip } from "@mui/material";
import {
  ArrowClockwiseIcon as RefreshIcon,
  CopySimpleIcon as CopyIcon,
  TrashIcon as DeleteIcon,
} from "@phosphor-icons/react";
import { Helmet } from "react-helmet";

import { DataTable, ProgressHeading } from "components";
import AddIcon from "components/icons/AddIcon";
import SectionHeader from "components/layout/SectionHeader";
import { ColumnCustomType } from "components/ui/DataTable/types";
import NavLink from "components/ui/NavLink";
import NoDataComponent from "components/ui/NoDataComponent";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import CloneDialog from "pages/definitions/dialog/CloneDialog";
import { colors } from "theme/tokens/variables";
import { getSequentiallySuffix } from "utils/strings";
import { schemaEditPath } from "../paths";
import { SchemaListRow, useSchemaList } from "./state/hook";

const INTRO_CONTENT = `A **schema** describes the shape of the data a workflow or task expects. Register one here and reference it from a workflow definition or a task definition by name and version.

Schemas are versioned, so a contract can evolve without breaking the definitions pinned to an older version.
`;

export const SchemaList = () => {
  const [
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
  ] = useSchemaList();

  const columns = [
    {
      id: "name",
      name: "name",
      label: "Schema Name",
      renderer: (name: string) => (
        <NavLink path={schemaEditPath(name)}>{name}</NavLink>
      ),
    },
    {
      id: "type",
      name: "type",
      label: "Type",
    },
    {
      id: "version",
      name: "version",
      label: "Latest Version",
    },
    {
      id: "versions",
      name: "versions",
      label: "Versions",
      searchable: false,
      renderer: (versions: number[]) => versions.length,
    },
    {
      id: "createTime",
      name: "createTime",
      label: "Created Time",
      type: ColumnCustomType.DATE,
    },
    {
      id: "actions",
      name: "name",
      label: "Actions",
      sortable: false,
      searchable: false,
      right: true,
      renderer: (_name: string, row: SchemaListRow) => (
        <Box sx={{ display: "flex", justifyContent: "space-evenly" }}>
          <Tooltip title={"Clone schema"}>
            <IconButton
              id={`clone-${row.name}-btn`}
              onClick={() => handleOpenCloneDialog(row)}
              size="small"
              sx={{ whiteSpace: "nowrap" }}
            >
              <CopyIcon size={20} />
            </IconButton>
          </Tooltip>
          <Tooltip title={"Delete schema and all its versions"}>
            <IconButton
              id={`delete-${row.name}-btn`}
              onClick={() => handleOpenConfirmDeleteDialog(row)}
              size="small"
              sx={{ whiteSpace: "nowrap" }}
            >
              <DeleteIcon size={20} />
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
  ];

  return (
    <Box id="schema-list">
      <Helmet>
        <title>Schemas</title>
      </Helmet>

      {!!cloneDialogData && (
        <CloneDialog
          name={
            getSequentiallySuffix({
              name: cloneDialogData.name,
              refNames: schemaNameList,
            }).name
          }
          namesList={schemaNameList}
          onClose={handleCloseCloneDialog}
          onSuccess={handleCloneSchema}
          title="Clone Schema Confirmation"
          id="schema-name-field"
          label="Schema name"
        />
      )}

      {!!confirmDeleteDialogData && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(selectedChoice) => {
            if (selectedChoice) {
              handleDeleteSchema();
            } else {
              handleCloseConfirmDeleteDialog();
            }
          }}
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>
                {confirmDeleteDialogData.name}
              </strong>
              ? All {confirmDeleteDialogData.versions.length} version
              {confirmDeleteDialogData.versions.length === 1 ? "" : "s"} will be
              removed. This cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{confirmDeleteDialogData.name}</strong> to
                confirm.
              </div>
            </>
          }
          header={"Deletion confirmation"}
          isInputConfirmation
          valueToBeDeleted={confirmDeleteDialogData.name}
        />
      )}

      <SectionHeader
        title="Schemas"
        _deprecate_marginTop={0}
        actions={
          <SectionHeaderActions
            buttons={[
              {
                id: "new-schema-btn",
                label: "New schema",
                onClick: handleGoToNewSchema,
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />

      <SectionContainer>
        <ProgressHeading loading={isFetching || isMutating} />
        <Paper variant="outlined">
          <DataTable
            localStorageKey="schemasTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search schemas"
            searchTerm={searchParam}
            onSearchTermChange={handleUpdateSearchParam}
            defaultShowColumns={[
              "name",
              "type",
              "version",
              "versions",
              "createTime",
              "actions",
            ]}
            keyField="name"
            data={tableData}
            columns={columns}
            defaultSortFieldId="createTime"
            defaultSortAsc={false}
            customActions={[
              <Tooltip title="Refresh schemas" key="refresh-schemas">
                <Button
                  variant="text"
                  color="inherit"
                  size="small"
                  startIcon={<RefreshIcon />}
                  key="refresh"
                  onClick={handleRefreshTable}
                >
                  Refresh
                </Button>
              </Tooltip>,
            ]}
            noDataComponent={
              searchParam === "" ? (
                <NoDataComponent
                  title="Schema"
                  description={INTRO_CONTENT}
                  buttonText="Define a schema"
                  buttonHandler={handleGoToNewSchema}
                />
              ) : (
                <NoDataComponent
                  title="Empty"
                  titleBg={colors.warningTag}
                  description="I'm sorry that search didn't find any matches. Please try different filters."
                  buttonText="Clear search"
                  buttonHandler={handleResetSearchParam}
                />
              )
            }
          />
        </Paper>
      </SectionContainer>
    </Box>
  );
};
