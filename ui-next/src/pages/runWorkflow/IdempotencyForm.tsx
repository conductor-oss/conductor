import { FormControlLabel, Grid } from "@mui/material";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import Text from "components/ui/Text";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { colors } from "theme/tokens/variables";

const style = {
  labelText: {
    position: "relative",
    fontSize: "13px",
    transform: "none",
    fontWeight: 600,
    paddingLeft: 0,
    marginBottom: "0.3em",
    color: colors.black,
  },
};

export interface IdempotencyFormProps {
  idempotencyValues: {
    idempotencyKey?: string;
    idempotencyStrategy?: IdempotencyStrategyEnum;
  };
  onChange: (data: {
    idempotencyKey: string;
    idempotencyStrategy?: IdempotencyStrategyEnum;
  }) => void;
  showStrategyInitially?: boolean;
}

enum IdempotencyStrategyEnum {
  FAIL = "FAIL",
  RETURN_EXISTING = "RETURN_EXISTING",
  FAIL_ON_RUNNING = "FAIL_ON_RUNNING",
}

export default function IdempotencyForm({
  idempotencyValues,
  onChange,
  showStrategyInitially,
}: IdempotencyFormProps) {
  const { idempotencyKey, idempotencyStrategy } = idempotencyValues;
  return (
    <>
      <Grid size={12}>
        <ConductorInput
          id="idempotency-key-field"
          fullWidth
          label="Idempotency key"
          value={idempotencyKey ?? ""}
          onTextInputChange={(value) =>
            onChange({
              idempotencyKey: value,
              idempotencyStrategy: idempotencyStrategy,
            })
          }
          tooltip={{
            title: "Idempotency key",
            content:
              "Idempotency Key is a user generated key to avoid conflicts with other workflows. Idempotency data is retained for the life of the workflow executions.",
          }}
        />
      </Grid>
      {(idempotencyKey || showStrategyInitially) && (
        <>
          <Grid mt="12px" size={12}>
            <Text sx={style.labelText} opacity={0.6}>
              Idempotency strategy
            </Text>
            <FormControlLabel
              labelPlacement="top"
              label=""
              sx={{
                marginLeft: 0,
                alignItems: "start",
                "& .MuiFormControlLabel-label": {
                  fontWeight: 500,
                  color: colors.gray07,
                },
                "& .MuiFormGroup-root": {
                  marginLeft: 0,
                  flexWrap: "nowrap",
                  paddingTop: "10px",
                },
              }}
              control={
                <RadioButtonGroup
                  items={[
                    {
                      value: IdempotencyStrategyEnum.RETURN_EXISTING,
                      label: "Return Existing",
                      helperText:
                        "Request will not fail rather it will return the workflowId of the workflow which was triggered with the same idempotencyKey.",
                    },
                    {
                      value: IdempotencyStrategyEnum.FAIL,
                      label: "Fail",
                      helperText:
                        "Request will fail if the workflow has been triggered with the same idempotencyKey in the past.",
                    },
                    {
                      value: IdempotencyStrategyEnum.FAIL_ON_RUNNING,
                      label: "Fail on Running",
                      helperText:
                        "Request will fail if another workflow with the same idempotencyKey is Running or Paused.",
                    },
                  ]}
                  name="idempotency strategy"
                  value={idempotencyStrategy ?? ""}
                  onChange={(_event, value) => {
                    onChange({
                      idempotencyKey: idempotencyKey ?? "",
                      idempotencyStrategy: value as IdempotencyStrategyEnum,
                    });
                  }}
                />
              }
            />
          </Grid>
        </>
      )}
    </>
  );
}
