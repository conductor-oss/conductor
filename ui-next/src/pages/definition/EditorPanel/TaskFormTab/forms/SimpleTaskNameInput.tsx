import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Snackbar from "@mui/material/Snackbar";
import { useSelector } from "@xstate/react";
import MuiAlert from "components/ui/MuiAlert";
import Button from "components/ui/buttons/MuiButton";
import ConductorInput from "components/ui/inputs/ConductorInput";
import XCloseIcon from "components/icons/XCloseIcon";
import { WorkflowEditContext } from "pages/definition/state";
import React, {
  FunctionComponent,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";
import { Link } from "react-router";
import { autocompleteStyle } from "shared/styles";
import { NEW_TASK_TEMPLATE } from "templates/JSONSchemaWorkflow";
import { fontWeights } from "theme/tokens/variables";
import { TaskDef, WorkflowDef } from "types";
import { handleValidChars, handleValidCharsForEvents } from "utils";
import { featureFlags, FEATURES } from "utils/flags";
import { useAction, useFetch } from "utils/query";

const filter = createFilterOptions();

interface SimpleTaskNameInputProps {
  onChange?: any;
  value?: string;
  error?: boolean;
  helperText?: string;
  isMetaBarEditing?: boolean;
  triggerSuccessEvent?: () => void;
}

const SimpleTaskNameInput: FunctionComponent<SimpleTaskNameInputProps> = ({
  onChange,
  value,
  error,
  helperText,
  triggerSuccessEvent,
}: SimpleTaskNameInputProps) => {
  const [dialogValue, setDialogValue] = useState<{
    name?: string;
    inputValue?: string;
    description?: string;
  }>({
    name: "",
    inputValue: "",
    description: "",
  });

  const [open, toggleOpen] = useState(false);
  const [maybeFormError, setFormError] = useState<string>("");
  const [options, setOptions] = useState<
    { name: string; description: string }[]
  >([]);

  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const currentWorkflow = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.currentWf,
  );

  const taskVisibility = featureFlags.getValue(
    FEATURES.TASK_VISIBILITY,
    "READ",
  );
  const { refetch: refetchAllDefinitions } = useFetch(
    `/metadata/taskdefs?access=${taskVisibility}`,
    {
      onSuccess: (data: TaskDef[]) => {
        setOptions(
          data
            .map((task: TaskDef) => ({
              name: task.name,
              description: task.description,
            }))
            .sort((a, b) => a.name.localeCompare(b.name)),
        );
      },
    },
  );

  const handleClose = () => {
    setDialogValue({
      name: "",
      inputValue: "",
      description: "",
    });

    toggleOpen(false);
  };

  const {
    mutate: persistNewTaskDefinition,
    isLoading: isSavingNewTaskDefintion,
  } = useAction("/metadata/taskdefs", "post", {
    onSuccess: () => {
      if (triggerSuccessEvent) {
        triggerSuccessEvent();
      }

      refetchAllDefinitions();
      handleClose();
    },
    onError: (err: any) => {
      console.error("There was an error", err);
      setFormError("Error creating task definition");
    },
  });

  const ownerEmail = useMemo(
    () => (currentWorkflow as WorkflowDef).ownerEmail,
    [currentWorkflow],
  );

  const maybeNameErrorProps = useMemo(
    () =>
      options.findIndex(({ name }) => name === dialogValue.name) === -1
        ? {}
        : { error: true, helperText: "Name already exists" },
    [options, dialogValue],
  );

  const handleSubmit = useCallback(
    (event: any) => {
      event.preventDefault();
      onChange(dialogValue.name);

      const newTaskDefinition = {
        ...NEW_TASK_TEMPLATE,
        ownerEmail,
        ...dialogValue,
      };
      // @ts-ignore
      persistNewTaskDefinition({
        body: JSON.stringify([newTaskDefinition]),
      });
    },
    [dialogValue, persistNewTaskDefinition, ownerEmail, onChange],
  );

  const isValidTaskDefinition = useMemo(
    () => options?.some((option) => option?.name === value),
    [value, options],
  );

  return (
    <>
      <Autocomplete
        fullWidth
        value={value ? value : ""}
        isOptionEqualToValue={(option: any, currentValue: any) =>
          option?.name === currentValue
        }
        autoHighlight
        componentsProps={{ paper: { elevation: 3 } }}
        onChange={(_event, newValue: any) => {
          if (typeof newValue === "string") {
            // From Material docs:
            // timeout to avoid instant validation of the dialog's form.
            setTimeout(() => {
              toggleOpen(true);
              setDialogValue({
                name: newValue,
              });
            });
          } else if (newValue && newValue.inputValue) {
            toggleOpen(true);
            setDialogValue({
              name: newValue.inputValue,
            });
          } else {
            if (typeof newValue?.name === "undefined") {
              onChange("");
            } else {
              onChange(newValue.name);
            }
          }
        }}
        filterOptions={(options, params) => {
          const filtered = filter(options, params);
          const currentValue = params.inputValue || value;

          if (currentValue) {
            const currentIndex = options.findIndex(
              (option) => option.name === currentValue,
            );

            if (currentIndex === -1) {
              filtered.unshift({
                inputValue: currentValue,
                name: `Create new: "${currentValue}"`,
              });
            }
          }

          return filtered;
        }}
        id="simple-task-name-input-autocomplete-text-field"
        options={options}
        getOptionLabel={(option) => {
          // e.g value selected with enter, right from the input
          if (typeof option === "string") {
            return option;
          }
          if (option.inputValue) {
            return option.inputValue;
          }
          return option.name;
        }}
        selectOnFocus
        clearOnBlur
        handleHomeEndKeys
        renderOption={(props, option) => (
          <li
            {...props}
            style={{
              ...props.style,
              borderBottom: "1px solid",
              borderColor: "rgba(128, 128, 128, .25)",
            }}
          >
            <Box
              sx={{
                paddingTop: 2,
                paddingBottom: 2,
              }}
            >
              <Box
                sx={{
                  fontWeight: fontWeights.fontWeight1,
                }}
              >
                {option.name}
              </Box>
              <Box>{option.description}</Box>
            </Box>
          </li>
        )}
        freeSolo
        renderInput={(params) => {
          const { inputProps: originalInputProps, ...restParams } = params;
          const {
            onChange: originalOnChange = (
              _e: React.ChangeEvent<HTMLInputElement>,
            ) => ({}),
            ...restInputProps
          } = originalInputProps;

          return (
            <ConductorInput
              {...restParams}
              inputProps={{
                ...restInputProps,
                onChange: handleValidCharsForEvents(originalOnChange),
              }}
              error={error}
              helperText={helperText}
              label="Task Definition"
            />
          );
        }}
        sx={[autocompleteStyle({ value })]}
        clearIcon={<XCloseIcon />}
      />
      {value && isValidTaskDefinition && (
        <Box pt={2}>
          <Link
            to={`/taskDef/${encodeURIComponent(value)}`}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: "flex",
              alignItems: "center",
              fontSize: "9pt",
              textDecoration: "none",
              opacity: 0.7,
            }}
          >
            Edit task definition
          </Link>
        </Box>
      )}
      <Dialog open={open} onClose={handleClose}>
        <form onSubmit={handleSubmit}>
          <DialogTitle>Create task definition</DialogTitle>
          <DialogContent>
            <Snackbar
              open={maybeFormError !== ""}
              autoHideDuration={1000}
              onClose={() => setFormError("")}
            >
              <MuiAlert severity="error">{maybeFormError}</MuiAlert>
            </Snackbar>
            <DialogContentText>
              A new task definition with default values will be created.
            </DialogContentText>
            <ConductorInput
              fullWidth
              margin="normal"
              id="name"
              value={dialogValue.name}
              onTextInputChange={handleValidChars((value) =>
                setDialogValue({
                  ...dialogValue,
                  name: value,
                }),
              )}
              label="Name"
              type="text"
              {...maybeNameErrorProps}
            />
            <ConductorInput
              autoFocus
              fullWidth
              margin="normal"
              id="description"
              value={dialogValue.description}
              onTextInputChange={(value) =>
                setDialogValue({
                  ...dialogValue,
                  description: value,
                })
              }
              label="Description"
              type="text"
            />
          </DialogContent>
          <DialogActions>
            <Button color="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Box sx={{ m: 1, position: "relative" }}>
              <Button
                color="primary"
                type="submit"
                disabled={isSavingNewTaskDefintion}
              >
                Create
              </Button>
              {isSavingNewTaskDefintion && (
                <CircularProgress
                  size={24}
                  sx={{
                    position: "absolute",
                    top: "50%",
                    left: "50%",
                    marginTop: "-12px",
                    marginLeft: "-12px",
                  }}
                />
              )}
            </Box>
          </DialogActions>
        </form>
      </Dialog>
    </>
  );
};

export default SimpleTaskNameInput;
