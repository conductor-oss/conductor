import { yupResolver } from "@hookform/resolvers/yup";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
} from "@mui/material";
import ActionButton from "components/ActionButton";
import Button from "components/MuiButton";
import { MessageContext } from "components/v1/layout/MessageContext";
import ReactHookFormDropdown from "components/v1/react-hook-form/ReactHookFormDropdown";
import ReactHookFormInput from "components/v1/react-hook-form/ReactHookFormInput";
import _last from "lodash/last";
import { getWorkflowDefinitionByNameAndVersion } from "pages/definition/commonService";
import { useContext, useMemo } from "react";
import { DefaultValues, SubmitHandler, useForm } from "react-hook-form";
import { useQueryClient } from "react-query";
import { HTTPMethods } from "types/TaskType";
import { WorkflowDef } from "types/WorkflowDef";
import { WORKFLOW_METADATA_BASE_URL } from "utils/constants/api";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import { logger } from "utils/logger";
import {
  useActionWithPath,
  useAuthHeaders,
  useSharedQueryContext,
} from "utils/query";
import { getSequentiallySuffix } from "utils/strings";
import { getUniqueWorkflowsWithVersions } from "utils/workflow";
import * as yup from "yup";

interface DialogData {
  name: string;
  version: number;
}

export interface CloneWorkflowDialogProps {
  selectedWorkflow: WorkflowDef;
  workflowList: WorkflowDef[];
  onClose: () => void;
  onSuccess: () => void;
}

const CloneWorkflowDialog = ({
  selectedWorkflow,
  onClose,
  onSuccess,
  workflowList,
}: CloneWorkflowDialogProps) => {
  const authHeaders = useAuthHeaders();
  const queryClient = useQueryClient();
  const { cacheQueryKey } = useSharedQueryContext();

  const { setMessage } = useContext(MessageContext);

  const createWorkflowAction = useActionWithPath({
    onSuccess: () => {
      onSuccess();
      // Clear cache to force re-fetch without waiting stale time
      queryClient.removeQueries(cacheQueryKey);
    },
    onError: (err: Error) => {
      logger.error(err);
    },
  });

  const workflowsWithVersions = useMemo<Map<string, number[]>>(
    () => getUniqueWorkflowsWithVersions(workflowList),
    [workflowList],
  );

  const workflowNames = useMemo<string[]>(
    () => [...workflowsWithVersions.keys()],
    [workflowsWithVersions],
  );

  const workflowVersions = useMemo<number[]>(
    () =>
      workflowsWithVersions.get(selectedWorkflow.name)?.map((item) => item) ||
      [],
    [workflowsWithVersions, selectedWorkflow],
  );

  const { name: suffixedWfName } = getSequentiallySuffix({
    name: selectedWorkflow.name,
    refNames: workflowNames,
  });

  const formSchema: yup.ObjectSchema<DialogData> = yup.object().shape({
    name: yup
      .string()
      .required("Name cannot be blank.")
      .matches(WORKFLOW_NAME_REGEX, WORKFLOW_NAME_ERROR_MESSAGE)
      .notOneOf(workflowNames, "This name is existing."),
    version: yup
      .number()
      .required("Version cannot be blank.")
      .typeError("Version cannot be blank."),
  });

  const defaultValues: DefaultValues<DialogData> = {
    name: suffixedWfName,
    version: _last(workflowVersions),
  };

  const {
    control,
    handleSubmit,
    formState: { errors: formErrors, isValid },
  } = useForm<DialogData>({
    mode: "onChange",
    resolver: yupResolver(formSchema),
    defaultValues,
  });

  const onSubmit: SubmitHandler<DialogData> = async (workflowData) => {
    const { name: newName, version } = workflowData;

    // Checking existing cloned workflow
    const existingWorkflow: WorkflowDef | undefined = workflowList?.find(
      (workflow) => workflow.name === newName,
    );

    if (!existingWorkflow) {
      try {
        const clonedWorkflow = await getWorkflowDefinitionByNameAndVersion({
          name: selectedWorkflow.name,
          version: Number(version),
          authHeaders,
        });

        if (clonedWorkflow?.name) {
          // @ts-ignore
          createWorkflowAction.mutate({
            method: HTTPMethods.POST,
            path: WORKFLOW_METADATA_BASE_URL,
            body: JSON.stringify({
              ...clonedWorkflow,
              version: 1,
              name: newName,
            }),
            workflowName: newName,
          });
        }
      } catch (e: any) {
        setMessage({
          severity: "error",
          text: `Unable to clone: ${e.text}`,
        });
        onClose();
      }
    }
  };

  return (
    <Dialog fullWidth maxWidth="sm" open onClose={onClose}>
      <DialogTitle>Clone Workflow Confirmation</DialogTitle>
      <DialogContent>
        <Grid container sx={{ width: "100%" }} spacing={5} pt={5}>
          <Grid size={12}>
            <ReactHookFormInput
              id="workflow-name-field"
              name="name"
              control={control}
              fullWidth
              label="Workflow name"
              required
              error={!!formErrors?.name?.message}
              helperText={formErrors?.name?.message}
              spellCheck={false}
            />
          </Grid>

          <Grid size={12}>
            <ReactHookFormDropdown
              id="user-version-field"
              name="version"
              control={control}
              fullWidth
              label="Version"
              required
              getOptionLabel={(option) => option?.toString()}
              options={workflowVersions}
              error={!!formErrors?.version?.message}
              helperText={formErrors?.version?.message}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button
          id="cancel-btn"
          variant="contained"
          color="secondary"
          onClick={onClose}
        >
          Cancel
        </Button>
        <ActionButton
          id="confirm-clone-btn"
          variant="contained"
          color="primary"
          sx={{
            fontSize: 14,
            lineHeight: 1.5,
          }}
          onClick={() => handleSubmit(onSubmit)()}
          disabled={!isValid}
          progress={createWorkflowAction.isLoading}
        >
          Clone
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
};

export default CloneWorkflowDialog;
