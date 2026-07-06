import { yupResolver } from "@hookform/resolvers/yup";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
} from "@mui/material";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { DefaultValues, SubmitHandler, useForm } from "react-hook-form";
import ActionButton from "components/ui/buttons/ActionButton";
import Button from "components/ui/buttons/MuiButton";
import ReactHookFormInput from "components/ui/react-hook-form/ReactHookFormInput";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import { useAuthHeaders } from "utils/query";
import * as yup from "yup";

interface DialogData {
  name: string;
}
export interface CloneScheduleDialogProps {
  name: string;
  onClose: () => void;
  onSuccess: (data: DialogData) => void;
  isFetching?: boolean;
}

const CloneScheduleDialog = ({
  name,
  onClose,
  onSuccess,
  isFetching,
}: CloneScheduleDialogProps) => {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  const formSchema = yup.object().shape({
    name: yup
      .string()
      .required("Name cannot be blank.")
      .matches(WORKFLOW_NAME_REGEX, WORKFLOW_NAME_ERROR_MESSAGE)
      .test("unique-name", "This name is existing.", async (value) => {
        if (!value) {
          return true;
        }

        try {
          await fetchWithContext(
            `/scheduler/schedules/${encodeURIComponent(value)}`,
            fetchContext,
            { headers: authHeaders },
          );
          return false;
        } catch (error) {
          if (error instanceof Response) {
            if (error.status === 404) {
              return true;
            }
            // Schedule exists but caller may lack READ, or other client error
            if (error.status === 403) {
              return false;
            }
          }
          return true;
        }
      }),
  });

  const defaultValues: DefaultValues<DialogData> = {
    name: name,
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

  const onSubmit: SubmitHandler<DialogData> = (data) => {
    onSuccess(data);
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
          progress={isFetching}
        >
          Clone
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
};

export default CloneScheduleDialog;
