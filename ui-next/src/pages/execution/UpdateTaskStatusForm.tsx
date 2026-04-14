import { Box, Grid } from "@mui/material";
import MenuItem from "@mui/material/MenuItem";
import { Button, Text } from "components";
import { ConductorSelect } from "components/ui/inputs";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import debounce from "lodash/debounce";
import _isEmpty from "lodash/isEmpty";
import sharedStyles from "pages/styles";
import { ChangeEvent, FunctionComponent, useState } from "react";

const style = {
  ...sharedStyles,
  paper: {
    margin: "20px",
    padding: "20px",
  },
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
  },
  fields: {
    display: "flex",
    flexDirection: "column",
    gap: "15px",
  },
  controls: {
    marginLeft: "15px",
    marginTop: "20px",
    height: "calc(100% - 83px)",
    overflowY: "scroll",
    width: "calc(100% - 15px)",
    overflowX: "hidden",
    paddingBottom: "60px",
  },
  monaco: {
    padding: "10px",
    borderColor: "rgba(128, 128, 128, 0.2)",
    borderStyle: "solid",
    borderWidth: "1px",
    borderRadius: "4px",
    backgroundColor: "rgb(255, 255, 255)",
    "&:focus-within": {
      margin: "-2px",
      borderColor: "rgb(73, 105, 228)",
      borderStyle: "solid",
      borderWidth: "2px",
    },
  },
  labelText: {
    position: "relative",
    fontSize: "13px",
    transform: "none",
    fontWeight: 600,
    paddingLeft: 0,
    paddingBottom: "8px",
  },
  inputBox: {
    marginTop: "10px",
    "& textarea": {
      minWidth: "368px",
      fontFamily: "monospace",
    },
    "& input": {
      minWidth: "368px",
    },
    "& label": {},
  },
  roBox: {
    marginTop: "10px",
    "& .MuiOutlinedInput-root": {
      background: "transparent",
      border: "none",
    },
    "& fieldset": {
      border: "none",
    },
    "& textarea": {
      minWidth: "450px",
      minHeight: "140px",
      fontFamily: "monospace",
      overflow: "none",
    },
    "& input": {
      minWidth: "368px",
    },
    "& label": {},
  },
  cronApply: {
    marginTop: "-12px",
    "& svg": {
      fontSize: "18px",
    },
  },
  toggleButton: {
    marginTop: "-12px",
    "& svg": {
      fontSize: "22px",
    },
  },
  cronSample: {
    fontSize: "12px",
    height: "50px",
  },
};

const possibleTaskStatus = [
  "COMPLETED",
  "FAILED",
  "FAILED_WITH_TERMINAL_ERROR",
];

const taskMenuItems = possibleTaskStatus.map((n) => (
  <MenuItem key={n} value={n}>
    {n}
  </MenuItem>
));

interface UpdateTaskStatusFormProps {
  onConfirm: (status: string, body: string) => void;
}

export const UpdateTaskStatusForm: FunctionComponent<
  UpdateTaskStatusFormProps
> = ({ onConfirm }) => {
  const [selected, setSelected] = useState("");
  const [params, setParams] = useState("{}");
  const [isValidJson, setIsValidaJson] = useState(true);

  const parsedValue = debounce((params: string) => {
    try {
      const parsedValue = JSON.parse(params);
      if (Array.isArray(parsedValue)) {
        setIsValidaJson(false);
      } else {
        setIsValidaJson(true);
      }
    } catch {
      setIsValidaJson(false);
    }
  }, 500);

  const handleSelectChange = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    setSelected(event.target.value as string);
  };

  const handleInputChange = (val: string) => {
    parsedValue(val);
    setParams(val);
  };

  return (
    <Box px={6}>
      <Grid
        container
        spacing={2}
        alignContent="center"
        sx={{ width: "100%", paddingBottom: 4 }}
      >
        <Grid sx={{ display: "flex", alignItems: "center" }} size={2}>
          <Text sx={style.labelText} style={{ paddingBottom: 0 }}>
            Update task
          </Text>
        </Grid>
        <Grid sx={{ display: "flex", alignItems: "center" }} size={10}>
          <ConductorSelect
            label="Status"
            value={selected}
            onChange={handleSelectChange}
            variant="outlined"
            size="small"
            style={{ minWidth: 50 }}
            SelectProps={{
              displayEmpty: true,
            }}
          >
            <MenuItem value="" disabled>
              Select Status
            </MenuItem>
            {taskMenuItems}
          </ConductorSelect>
        </Grid>
        <Grid size={12}>
          <ConductorCodeBlockInput
            value={params}
            onChange={handleInputChange}
            error={!isValidJson}
          />
        </Grid>
        <Grid size={12}>
          <Button
            onClick={() => onConfirm(selected, params)}
            variant="contained"
            color="primary"
            disabled={_isEmpty(selected) || !isValidJson}
            style={{ marginTop: 12 }}
          >
            Update
          </Button>
        </Grid>
      </Grid>
    </Box>
  );
};
