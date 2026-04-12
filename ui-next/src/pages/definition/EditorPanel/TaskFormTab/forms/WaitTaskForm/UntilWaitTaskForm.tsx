import { Grid, Link } from "@mui/material";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import ConductorDateTimePicker from "components/ui/date-time/ConductorDateTimePicker";
import _isEmpty from "lodash/isEmpty";
import { FunctionComponent } from "react";
import { CONTAIN_VARIABLE_SYNTAX_REGEX } from "utils/constants/regex";
import {
  DATE_FORMAT,
  DateAdapter,
  formatDate,
  getMomentStyleOffset,
  getTimeZoneAbbreviation,
  getTimeZoneNames,
  guessUserTimeZone,
  parse,
} from "utils/date";

const fixValueForName = (name: string) => {
  const offset = getMomentStyleOffset(name);
  return `GMT${offset}`;
};

const getTimeZoneLabel = (name: string) => {
  const offset = getMomentStyleOffset(name);
  const gmtString = offset === "+00:00" ? "GMT" : `GMT ${offset}`;
  const abbr = getTimeZoneAbbreviation(name);
  return `(${gmtString}) ${abbr} (${name})`;
};

const TIME_ZONES_OPTIONS = getTimeZoneNames().map((name) => ({
  label: getTimeZoneLabel(name),
  value: fixValueForName(name),
}));

const defaultTimeZone = () => {
  return fixValueForName(guessUserTimeZone());
};

const extractDateStringFromValue = (val?: string) => {
  if (!val || (val && CONTAIN_VARIABLE_SYNTAX_REGEX.test(val))) {
    return [null, ""];
  }

  const splittedVal = val.split(" ");

  if (splittedVal!.length >= 1 && splittedVal!.length < 3) {
    return [`${splittedVal[0]} 00:00`, defaultTimeZone()];
  }

  // If it's greater than 3 I don't care because it's wrong
  const [dateField, hourField, timeZone] = splittedVal;

  return [`${dateField} ${hourField}`, timeZone];
};

const UntilWaitTaskForm: FunctionComponent<{
  value: string;
  onChange: (val: string) => void;
}> = ({ value, onChange }) => {
  const [datetime, timeZone] = extractDateStringFromValue(value);

  return (
    <LocalizationProvider dateAdapter={DateAdapter}>
      <Grid container sx={{ width: "100%" }} spacing={3}>
        <Grid
          size={{
            xs: 12,
            md: 6,
          }}
        >
          <ConductorDateTimePicker
            label="Date & Time"
            value={datetime ? parse(datetime, DATE_FORMAT, new Date()) : null}
            onChange={(val) => {
              const validDateFormat = val ? formatDate(val, DATE_FORMAT) : "";

              if (
                !_isEmpty(validDateFormat) &&
                !validDateFormat.includes("Invalid date")
              ) {
                onChange(`${validDateFormat} ${timeZone}`);
              } else {
                onChange("");
              }
            }}
            inputProps={{ fullWidth: true }}
          />
        </Grid>

        <Grid
          size={{
            xs: 12,
            md: 6,
          }}
        >
          <ConductorAutoComplete
            label="Timezone"
            placeholder="Timezone"
            sx={{ minWidth: "150px" }}
            fullWidth
            disabled={_isEmpty(datetime)}
            freeSolo
            value={timeZone}
            options={TIME_ZONES_OPTIONS}
            onChange={(__, selectedVal: { value: string; label: string }) => {
              if (selectedVal && !_isEmpty(datetime)) {
                onChange(`${datetime} ${selectedVal.value}`);
              }
            }}
            onInputChange={(__, typed: string) => {
              if (!_isEmpty(typed) && !_isEmpty(datetime)) {
                onChange(`${datetime} ${typed}`);
              }
            }}
            renderOption={(props, option) => (
              <li {...props}>{option?.label}</li>
            )}
          />
        </Grid>

        <Grid
          size={{
            xs: 12,
            md: 6,
          }}
        >
          <ConductorAutocompleteVariables
            label={
              <>
                Computed value:&nbsp;
                <MuiTypography component="span" fontSize={10}>
                  (can also be a &nbsp;
                  <Link
                    href="https://orkes.io/content/reference-docs/operators/wait#input-parameters"
                    target="_blank"
                    rel="noreferrer"
                    sx={{ fontWeight: 500 }}
                  >
                    variable
                  </Link>
                  )
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
    </LocalizationProvider>
  );
};

export default UntilWaitTaskForm;
