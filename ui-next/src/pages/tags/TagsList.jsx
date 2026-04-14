import { Box, Tooltip } from "@mui/material";
import {
  Trash as DeleteIcon,
  ArrowClockwise as RefreshIcon,
} from "@phosphor-icons/react";
import { DataTable, NavLink, Paper } from "components";
import ConfirmChoiceDialog from "components/enterprise/ConfirmChoiceDialog";
import Button from "components/ui/buttons/MuiButton";
import IconButton from "components/ui/buttons/MuiIconButton";
import sharedStyles from "pages/styles";
import { useState } from "react";
import { Helmet } from "react-helmet";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import { featureFlags, FEATURES } from "utils/flags";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useActionWithPath, useFetch } from "utils/query";

export default function TaskDefinitions() {
  const columns = [
    {
      id: "key",
      name: "key",
      label: "Tag Key",
      renderer: (key) => <NavLink path={`/tags/${key}`}>{key}</NavLink>,
    },
    { id: "value", name: "value", label: "Value", grow: 2 },
    {
      id: "actions",
      name: "name",
      label: "Actions",
      sortable: false,
      searchable: false,
      grow: 0.5,
      renderer: (name) => (
        <Box style={{ display: "flex", justifyContent: "space-evenly" }}>
          <Tooltip title={"Delete Task Definition"}>
            <IconButton
              onClick={() => {
                setIsConfirmDelete({
                  confirmDelete: true,
                  name: name,
                });
              }}
              label="Delete"
              sx={sharedStyles.deleteIcon}
            >
              <DeleteIcon size={20} />
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
  ];

  const [isConfirmDelete, setIsConfirmDelete] = useState(false);

  const tagVisibility = featureFlags.getValue(FEATURES.TAG_VISIBILITY, "READ");

  const pushHistory = usePushHistory();
  const { data, refetch } = useFetch(`/metadata/tags?access=${tagVisibility}`, {
    initialData: [{ type: "METADATA", key: "team", value: "devops" }],
  });

  const deleteTagAction = useActionWithPath({
    onSuccess: () => {
      refetch();
    },
    onError: (err) => {
      console.error(err);
      refetch();
    },
  });

  return (
    <>
      <Helmet>
        <title>Tags</title>
      </Helmet>
      {isConfirmDelete && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(selectedChoice) => {
            if (selectedChoice) {
              deleteTagAction.mutate({
                method: "delete",
                path: `/metadata/tags/${isConfirmDelete.name}`,
              });
            }
            setIsConfirmDelete(false);
          }}
          message={
            "Are you sure you want to delete this Tag? This cannot be undone."
          }
        />
      )}
      <SectionHeader
        title="Tags"
        _deprecate_marginTop={0}
        actions={
          <>
            <Button
              onClick={() => pushHistory("/newTag")}
              style={{ width: 190, marginLeft: 20 }}
            >
              Add Tag
            </Button>
          </>
        }
      />
      <SectionContainer>
        <Paper variant="outlined">
          {/* <Header loading={isFetching} /> */}
          {data && (
            <>
              <DataTable
                localStorageKey="tagsTable"
                quickSearchEnabled
                quickSearchPlaceholder="Search Tags"
                defaultShowColumns={["key", "value", "type", "actions"]}
                keyField="name"
                default
                data={data}
                columns={columns}
                customActions={[
                  <Tooltip title="Refresh Tags" key="refresh-tags-tooltip">
                    <Button
                      variant="solid"
                      size="small"
                      startIcon={<RefreshIcon />}
                      key="refresh"
                      onClick={refetch}
                    >
                      Refresh
                    </Button>
                  </Tooltip>,
                ]}
              />
            </>
          )}
        </Paper>
      </SectionContainer>
    </>
  );
}
