import { Grid, Link } from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import ConductorInputNumber from "components/ui/inputs/ConductorInputNumber";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import _capitalize from "lodash/capitalize";
import { durationStringToPairs } from "pages/definition/EditorPanel/TaskFormTab/forms/WaitTaskForm/helpers";
import { FunctionComponent, useMemo } from "react";

const DurationWaitTaskForm: FunctionComponent<{
  value: string;
  onChange: (val: string) => void;
}> = ({ value, onChange }) => {
  const durationTuples = useMemo(() => {
    return durationStringToPairs(value);
  }, [value]);

  const handlePairUpdate = (idx: number) => (modTuple: [string, string]) => {
    const currentTuples = [...durationTuples];

    // update the latest change
    currentTuples[idx] = modTuple;

    const durationString = currentTuples.reduce(
      (acc, [value, unit]) =>
        Number(value) > 0 ? `${acc} ${value} ${unit}` : acc,
      "",
    );
    onChange(durationString.trim());
  };

  return (
    <Grid container sx={{ width: "100%" }} spacing={2}>
      {durationTuples.map(([value, unit], idx) => (
        <Grid key={unit}>
          <ConductorInputNumber
            label={`${_capitalize(unit)}`}
            placeholder="#"
            type="number"
            value={value ? Number(value) : null}
            onChange={(newValue) =>
              handlePairUpdate(idx)([`${newValue}`, unit])
            }
            sx={{
              maxWidth: 80,
              // ...disabledInputStyle,
            }}
          />
        </Grid>
      ))}
      <Grid
        sx={{
          display: "flex",
          alignItems: "end",
          pb: 1.5,
        }}
      >
        =
      </Grid>
      <Grid flexGrow={1}>
        <ConductorAutocompleteVariables
          fullWidth
          label={
            <>
              Variable&nbsp;
              <MuiTypography component="span">
                (
                <Link
                  href="https://orkes.io/content/reference-docs/operators/wait#input-parameters"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ fontWeight: 500 }}
                >
                  variables
                </Link>
                &nbsp;is autofilled unless override)
              </MuiTypography>
            </>
          }
          InputLabelProps={{
            sx: {
              pointerEvents: "auto",
            },
          }}
          value={value}
          onChange={onChange}
        />
      </Grid>
    </Grid>
  );
};

export default DurationWaitTaskForm;
