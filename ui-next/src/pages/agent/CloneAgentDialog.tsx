import { yupResolver } from "@hookform/resolvers/yup";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
} from "@mui/material";
import ActionButton from "components/ui/buttons/ActionButton";
import Button from "components/ui/buttons/MuiButton";
import ReactHookFormDropdown from "components/ui/react-hook-form/ReactHookFormDropdown";
import ReactHookFormInput from "components/ui/react-hook-form/ReactHookFormInput";
import { useMemo } from "react";
import { DefaultValues, SubmitHandler, useForm } from "react-hook-form";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import { useActionWithPath, useFetch } from "utils/query";
import { getSequentiallySuffix } from "utils/strings";
import * as yup from "yup";
import { AgentSummary } from "./types";

interface DialogData {
  name: string;
  version: number;
}

interface CloneAgentDialogProps {
  selectedAgent: AgentSummary;
  agentList: AgentSummary[];
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * Mirrors the workflow clone dialog while deploying the source agent definition under a new name.
 */
export default function CloneAgentDialog({
  selectedAgent,
  agentList,
  onClose,
  onSuccess,
}: CloneAgentDialogProps) {
  const { data: agentDefinitions = [] } = useFetch<AgentSummary[]>(
    "/metadata/workflow?short=true&metadata=true&classifier=agent",
  );
  const agentNames = useMemo(
    () => Array.from(new Set(agentList.map((agent) => agent.name))),
    [agentList],
  );
  const { name: suffixedAgentName } = getSequentiallySuffix({
    name: selectedAgent.name,
    refNames: agentNames,
  });
  const agentVersions = useMemo(() => {
    const versions = agentDefinitions
      .filter((agent) => agent.name === selectedAgent.name)
      .map((agent) => agent.version);
    return versions.length > 0 ? versions : [selectedAgent.version];
  }, [agentDefinitions, selectedAgent]);
  const formSchema: yup.ObjectSchema<DialogData> = yup.object().shape({
    name: yup
      .string()
      .required("Name cannot be blank.")
      .matches(WORKFLOW_NAME_REGEX, WORKFLOW_NAME_ERROR_MESSAGE)
      .notOneOf(agentNames, "This name is existing."),
    version: yup
      .number()
      .required("Version cannot be blank.")
      .typeError("Version cannot be blank."),
  });
  const defaultValues: DefaultValues<DialogData> = {
    name: suffixedAgentName,
    version: selectedAgent.version,
  };
  const {
    control,
    handleSubmit,
    watch,
    formState: { errors: formErrors, isValid },
  } = useForm<DialogData>({
    mode: "onChange",
    resolver: yupResolver(formSchema),
    defaultValues,
  });
  const selectedVersion = watch("version");
  const { data: sourceDefinition, isFetching } = useFetch<
    Record<string, unknown>
  >(
    `/agent/${encodeURIComponent(selectedAgent.name)}?version=${selectedVersion}`,
    { when: Number.isInteger(selectedVersion) },
  );
  const deployAgentAction = useActionWithPath({ onSuccess });
  const onSubmit: SubmitHandler<DialogData> = ({ name }) => {
    if (!sourceDefinition) {
      return;
    }
    deployAgentAction.mutate({
      method: "post",
      path: "/agent/deploy",
      body: JSON.stringify({ agentConfig: { ...sourceDefinition, name } }),
    });
  };

  return (
    <Dialog fullWidth maxWidth="sm" open onClose={onClose}>
      <DialogTitle>Clone Agent Confirmation</DialogTitle>
      <DialogContent>
        <Grid container sx={{ width: "100%" }} spacing={5} pt={5}>
          <Grid size={12}>
            <ReactHookFormInput
              id="agent-name-field"
              name="name"
              control={control}
              fullWidth
              label="Agent name"
              required
              error={!!formErrors?.name?.message}
              helperText={formErrors?.name?.message}
              spellCheck={false}
            />
          </Grid>
          <Grid size={12}>
            <ReactHookFormDropdown
              id="agent-version-field"
              name="version"
              control={control}
              fullWidth
              label="Version"
              required
              getOptionLabel={(option) => option?.toString()}
              options={agentVersions}
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
          sx={{ fontSize: 14, lineHeight: 1.5 }}
          onClick={() => handleSubmit(onSubmit)()}
          disabled={!isValid || isFetching}
          progress={deployAgentAction.isLoading}
        >
          Clone
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
}
