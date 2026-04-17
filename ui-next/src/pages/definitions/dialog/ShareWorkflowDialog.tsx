import {
  Avatar,
  Box,
  FormControlLabel,
  Grid,
  IconButton,
  Tooltip,
} from "@mui/material";
import { Share, X } from "@phosphor-icons/react";
import { Button, Paper } from "components";
import MuiCheckbox from "components/ui/MuiCheckbox";
import UIModal from "components/ui/dialogs/UIModal";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { MessageContext } from "components/providers/messageContext";
import _ from "lodash";
import _last from "lodash/last";
import { ChangeEvent, useContext, useEffect, useState } from "react";
import { useQueryClient } from "react-query";
import { HTTPMethods } from "types/TaskType";
import { WorkflowDef } from "types/WorkflowDef";
import { logger } from "utils/logger";
import { useActionWithPath, useSharedQueryContext } from "utils/query";

interface ShareWorkflowDialogProps {
  onClose: () => void;
  onSuccess: () => void;
  selectedWorkflow: WorkflowDef;
}

const ShareWorkflowDialog = ({
  onClose,
  onSuccess,
  selectedWorkflow,
}: ShareWorkflowDialogProps) => {
  const queryClient = useQueryClient();
  const { setMessage } = useContext(MessageContext);
  const { cacheQueryKey } = useSharedQueryContext();
  const [userId, setUserId] = useState<string | null>(null);
  const [peopleWithAccess, setPeopleWithAccess] = useState<string[]>([]);
  const [shareWithEveryone, setShareWithEveryone] = useState<boolean>(false);

  const shareWorkflowAction = useActionWithPath({
    onSuccess: () => {
      onSuccess();
      setMessage({
        severity: "success",
        text: `Workflow shared successfully`,
      });
      queryClient.removeQueries(cacheQueryKey);
    },
    onError: (err: Error) => {
      logger.error(err);
    },
  });

  const getAllsharedResources = useActionWithPath({
    onSuccess: (data: any) => {
      const allResources = data;
      if (allResources && allResources.length > 0) {
        const sharedWithList: string[] = _.chain(allResources)
          .filter(
            (obj) =>
              _last(obj.resourceName.split("#")) === selectedWorkflow?.name,
          )
          .map("sharedWith")
          .value();
        setPeopleWithAccess(sharedWithList);
      }
    },
    onError: (err: Error) => {
      logger.error(err);
    },
  });

  const removeSharingAction = useActionWithPath({
    onSuccess: () => {
      setMessage({
        severity: "success",
        text: `Access updated`,
      });
      setPeopleWithAccess([]);
      fetchAllSharedResources();
    },
    onError: (err: Error) => {
      logger.error(err);
    },
  });

  const handleUserId = (value: string) => {
    setUserId(value);
  };

  const handleShareWithEveryoneChange = (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    setShareWithEveryone(event.target.checked);
    if (event.target.checked) {
      setUserId("*");
    } else {
      setUserId(null);
    }
  };

  const handleShareWorkflow = () => {
    try {
      // @ts-ignore
      shareWorkflowAction.mutate({
        method: HTTPMethods.POST,
        path: `/share/shareResource?resourceType=WORKFLOW_DEF&resourceName=${selectedWorkflow?.name}&sharedWith=${userId}`,
      });
    } catch (e: any) {
      setMessage({
        severity: "error",
        text: `Unable to share: ${e.text}`,
      });
    }
  };

  const fetchAllSharedResources = () => {
    try {
      // @ts-ignore
      getAllsharedResources.mutate({
        method: HTTPMethods.GET,
        path: `/share/getSharedResources`,
      });
    } catch (e: any) {
      setMessage({
        severity: "error",
        text: `Unable to fetch shared resources: ${e.text}`,
      });
    }
  };

  useEffect(() => {
    fetchAllSharedResources();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRemoveSharing = (user: string) => {
    try {
      // @ts-ignore
      removeSharingAction.mutate({
        method: "delete",
        path: `/share/removeSharingResource?resourceType=WORKFLOW_DEF&resourceName=${selectedWorkflow?.name}&sharedWith=${user}`,
      });
    } catch (e: any) {
      setMessage({
        severity: "error",
        text: `Unable to fetch shared resources: ${e.text}`,
      });
    }
  };
  return (
    <UIModal
      open={true}
      setOpen={onClose}
      icon={<Share color="#000000" />}
      title={`Share "${selectedWorkflow?.name}" workflow`}
      description="Share this workflow with others you choose."
      titleSx={{ textTransform: "none" }}
      enableCloseButton
    >
      <Paper>
        <Grid container sx={{ width: "100%" }} spacing={4}>
          <Grid size={12}>
            <ConductorInput
              id="workflow-sharing-user-id-field"
              fullWidth
              label={"User Id"}
              placeholder={"Add people"}
              showClearButton
              value={shareWithEveryone ? "" : (userId ?? "")}
              onTextInputChange={(value) => handleUserId(value)}
              autoFocus={true}
              disabled={shareWithEveryone}
            />
            <FormControlLabel
              control={
                <MuiCheckbox
                  checked={shareWithEveryone}
                  onChange={handleShareWithEveryoneChange}
                  name="shareWithEveryone"
                  disableRipple
                />
              }
              label="Share this workflow with everyone"
            />
          </Grid>
          {peopleWithAccess && peopleWithAccess?.length > 0 ? (
            <Grid size={12}>
              <Box>
                <Box sx={{ fontSize: "14px", fontWeight: 500 }}>
                  People with access
                </Box>
                <Box pt={2}>
                  {peopleWithAccess.map((user, index) => (
                    <Box
                      key={user + index}
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: 2,
                        pb: 1,
                      }}
                    >
                      <Avatar sx={{ fontSize: "13px", width: 24, height: 24 }}>
                        {user[0]}
                      </Avatar>
                      <Box>{user}</Box>
                      <Box
                        sx={{
                          display: "flex",
                          alignItems: "center",
                          ml: "auto",
                        }}
                      >
                        <Tooltip title={"Remove access"}>
                          <IconButton
                            onClick={() => handleRemoveSharing(user)}
                            size="small"
                            sx={{
                              whiteSpace: "nowrap",
                            }}
                          >
                            <X size={15} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </Box>
                  ))}
                </Box>
              </Box>
            </Grid>
          ) : null}
          <Grid
            display={"flex"}
            justifyContent={"flex-end"}
            width={"100%"}
            gap={2}
          >
            <Button
              startIcon={<Share />}
              onClick={handleShareWorkflow}
              variant="contained"
              color="primary"
              disabled={!userId && !shareWithEveryone}
              id={"workflow-sharing-dialog-save-btn"}
            >
              Share
            </Button>
          </Grid>
        </Grid>
      </Paper>
    </UIModal>
  );
};

export default ShareWorkflowDialog;
