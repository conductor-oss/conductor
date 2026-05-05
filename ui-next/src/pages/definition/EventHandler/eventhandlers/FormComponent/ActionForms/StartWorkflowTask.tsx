import { Grid } from "@mui/material";
import IconButton from "components/ui/buttons/MuiIconButton";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import XCloseIcon from "components/icons/XCloseIcon";
import _isEmpty from "lodash/isEmpty";
import _isUndefined from "lodash/isUndefined";
import { FocusEvent, useMemo } from "react";
import { useWorkflowNamesAndVersions } from "utils/query";
import { Props } from "./common";
import IdempotencyForm from "pages/runWorkflow/IdempotencyForm";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { IdempotencyValuesProp } from "pages/definition/RunWorkflow/state";

export const StartWorkflowActionForm = ({
  onRemove,
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { start_workflow } = payload;
  const fetchedNamesAndVersions = useWorkflowNamesAndVersions();
  const options = useMemo(
    () =>
      fetchedNamesAndVersions.size === 0
        ? []
        : Array.from(fetchedNamesAndVersions.keys()),
    [fetchedNamesAndVersions],
  );

  const maybeSelectedWorkflowName = useMemo(
    () => (_isEmpty(start_workflow?.name) ? undefined : start_workflow?.name),
    [start_workflow?.name],
  );

  const availableVersions: string[] = useMemo(() => {
    const versions: number[] =
      fetchedNamesAndVersions.get(maybeSelectedWorkflowName) || [];

    return _isUndefined(maybeSelectedWorkflowName) && !_isEmpty(options)
      ? []
      : versions.map((val) => val.toString());
  }, [maybeSelectedWorkflowName, fetchedNamesAndVersions, options]);

  const handleIdempotencyValues = (data: IdempotencyValuesProp) => {
    const idempotencyStrategy = () => {
      if (data.idempotencyStrategy) {
        return data.idempotencyStrategy;
      }
      if (start_workflow?.idempotencyStrategy) {
        return start_workflow?.idempotencyStrategy;
      }
      return IdempotencyStrategyEnum.RETURN_EXISTING;
    };

    const updatedPayload = {
      ...payload,
      start_workflow: {
        ...start_workflow,
        idempotencyKey: data?.idempotencyKey,
        idempotencyStrategy: data?.idempotencyKey
          ? idempotencyStrategy()
          : undefined,
      },
    };
    handleChangeAction(index, updatedPayload);
  };

  return (
    <Grid
      container
      spacing={4}
      my={2}
      sx={{ width: "100%", position: "relative" }}
    >
      <Grid size={12}>
        <MuiTypography fontWeight={800} fontSize={16}>
          Start Workflow
        </MuiTypography>
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 9,
        }}
      >
        <ConductorAutoComplete
          label="Workflow name"
          freeSolo
          fullWidth
          value={start_workflow?.name}
          options={options}
          onChange={(_, value) =>
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                name: value,
              },
            })
          }
          onBlur={(event: FocusEvent<HTMLInputElement>) => {
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                name: event.target.value,
              },
            });
          }}
          conductorInputProps={{
            placeholder: `\${event.payload.workflow_name}`,
          }}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 3,
        }}
      >
        <ConductorAutoComplete
          label="Workflow version"
          freeSolo
          fullWidth
          value={start_workflow?.version}
          options={availableVersions}
          onChange={(_, value) =>
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                version: value,
              },
            })
          }
          onBlur={(event: FocusEvent<HTMLInputElement>) => {
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                version: event.target.value,
              },
            });
          }}
          conductorInputProps={{
            placeholder: "latest",
          }}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          md: 6,
        }}
      >
        <ConductorInput
          fullWidth
          label="Workflow correlation id"
          placeholder={`\${event.payload.correlation_id}`}
          value={start_workflow?.correlationId}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                correlationId: value,
              },
            })
          }
        />
      </Grid>
      <Grid size={12}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <IdempotencyForm
            idempotencyValues={{
              idempotencyKey: start_workflow?.idempotencyKey,
              idempotencyStrategy: start_workflow?.idempotencyStrategy,
            }}
            onChange={handleIdempotencyValues}
          />
        </Grid>
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                input: newValues,
              },
            });
          }}
          value={{ ...start_workflow?.input }}
          title="Input variables"
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          showFieldTypes
          enableAutocomplete={false}
          autoFocusField={false}
        />
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              start_workflow: {
                ...start_workflow,
                taskToDomain: newValues,
              },
            });
          }}
          value={{ ...start_workflow?.taskToDomain }}
          title="Tasks to domain mapping"
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          showFieldTypes={false}
          enableAutocomplete={false}
          autoFocusField={false}
        />
      </Grid>
      <IconButton onClick={onRemove} sx={{ position: "absolute", right: 0 }}>
        <XCloseIcon size={26} />
      </IconButton>
    </Grid>
  );
};
