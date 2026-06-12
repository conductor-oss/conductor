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
import { DefaultValues, SubmitHandler, useForm } from "react-hook-form";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import { WORKFLOW_NAME_REGEX } from "utils/constants/regex";
import * as yup from "yup";

interface DialogData {
  name: string;
}
export interface CloneDialogProps {
  name: string;
  namesList: string[];
  onClose: () => void;
  onSuccess: (data: DialogData) => void;
  isFetching?: boolean;
  title?: string;
  id?: string;
  label?: string;
}

const CloneDialog = ({
  name,
  onClose,
  onSuccess,
  namesList,
  isFetching,
  title,
  id,
  label,
}: CloneDialogProps) => {
  const formSchema = yup.object().shape({
    name: yup
      .string()
      .required("Name cannot be blank.")
      .matches(WORKFLOW_NAME_REGEX, WORKFLOW_NAME_ERROR_MESSAGE)
      .notOneOf(namesList, "This name is existing."),
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
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Grid container sx={{ width: "100%" }} spacing={5} pt={5}>
          <Grid size={12}>
            <ReactHookFormInput
              id={id}
              name="name"
              control={control as any}
              fullWidth
              label={label}
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

export default CloneDialog;
