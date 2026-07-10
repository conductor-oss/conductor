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
import ReactHookFormInput from "components/ui/react-hook-form/ReactHookFormInput";
import { useSaveSchedule } from "pages/scheduler/schedulerHooks";
import { DefaultValues, SubmitHandler, useForm } from "react-hook-form";
import { IScheduleDto } from "types/Schedulers";
import {
  formatScheduleNameConflictMessage,
  WORKFLOW_NAME_ERROR_MESSAGE,
} from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import * as yup from "yup";

interface DialogData {
  name: string;
}

async function readResponseMessage(response: Response): Promise<string> {
  const isJSON = response.headers
    .get("content-type")
    ?.includes("application/json");
  if (isJSON) {
    const body = await response.json();
    return body?.message ?? response.statusText;
  }
  return response.text();
}

export interface CloneScheduleDialogProps {
  schedule: IScheduleDto;
  defaultName: string;
  onClose: () => void;
  onSuccess: () => void;
  onError?: (error: Response) => void | Promise<void>;
}

const CloneScheduleDialog = ({
  schedule,
  defaultName,
  onClose,
  onSuccess,
  onError,
}: CloneScheduleDialogProps) => {
  const formSchema = yup.object().shape({
    name: yup
      .string()
      .required("Name cannot be blank.")
      .matches(WORKFLOW_NAME_REGEX, WORKFLOW_NAME_ERROR_MESSAGE),
  });

  const defaultValues: DefaultValues<DialogData> = {
    name: defaultName,
  };

  const {
    control,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors: formErrors, isValid },
  } = useForm<DialogData>({
    mode: "onChange",
    resolver: yupResolver(formSchema),
    defaultValues,
  });

  const { mutate: saveSchedule, isLoading: isSavingSchedule } = useSaveSchedule(
    {
      onSuccess: () => {
        onSuccess();
      },
      onError: async (error: Response) => {
        if (error.status === 409) {
          const message = formatScheduleNameConflictMessage(
            await readResponseMessage(error),
          );
          setError("name", { type: "server", message });
          return;
        }
        await onError?.(error);
      },
    },
  );

  const onSubmit: SubmitHandler<DialogData> = (data) => {
    saveSchedule({
      body: JSON.stringify({ ...schedule, name: data.name }),
    });
  };

  return (
    <Dialog fullWidth maxWidth="sm" open onClose={onClose}>
      <DialogTitle>Clone Schedule Confirmation</DialogTitle>
      <DialogContent>
        <Grid container sx={{ width: "100%" }} spacing={5} pt={5}>
          <Grid size={12}>
            <ReactHookFormInput
              id="schedule-name-field"
              name="name"
              control={control}
              fullWidth
              label="Schedule name"
              required
              error={!!formErrors?.name?.message}
              helperText={formErrors?.name?.message}
              spellCheck={false}
              onChangeCallback={() => clearErrors("name")}
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
          progress={isSavingSchedule}
        >
          Clone
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
};

export default CloneScheduleDialog;
