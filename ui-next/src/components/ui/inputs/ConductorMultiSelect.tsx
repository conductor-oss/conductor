import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import { ReactNode, useEffect, useState } from "react";

import MuiCheckbox from "components/ui/MuiCheckbox";
import ConductorSelect from "./ConductorSelect";

const ALL_VALUE = "all";
const itemHeight = 48;
const itemPaddingTop = 8;
const additionalHeight = 4.5;

type ConductorMultiSelectProp = {
  label: string;
  options: string[];
  onSelected: (val: string[]) => void;
  allText: string;
  value: string[];
  renderer?: (val: string) => ReactNode;
  dataTestId?: string;
  error?: boolean;
  helperText?: string;
};

type MenuPropsType = {
  PaperProps: {
    style: {
      maxHeight: number;
      width: string;
    };
  };
};

export default function ConductorMultiSelect({
  label,
  options,
  onSelected,
  allText,
  value = [],
  renderer,
  dataTestId,
  error,
  helperText = "",
}: ConductorMultiSelectProp) {
  const menuProps: MenuPropsType = {
    PaperProps: {
      style: {
        maxHeight: itemHeight * additionalHeight + itemPaddingTop,
        width: "auto",
      },
    },
  };
  const [selected, setSelected] = useState(value);
  const isAllChecked = options.length > 0 && selected.length === options.length;
  const isIndeterminate =
    selected.length > 0 && selected.length < options.length;

  const handleChange = (event: any) => {
    const { value } = event.target;

    if (value[value.length - 1] === "all") {
      setSelected(selected.length === options.length ? [] : options);
      return;
    }

    setSelected(value);
  };

  const rendersSelectedValues = (selectedValues: string[]) => {
    if (isAllChecked || selectedValues.length === 0) {
      return allText;
    }

    return renderer
      ? selectedValues.map((value) => renderer(value))
      : selectedValues.join(", ");
  };

  useEffect(() => {
    onSelected(selected.filter((x: string) => allText !== x));
  }, [selected, onSelected, allText]);

  return (
    <ConductorSelect
      id="multiple-checkbox"
      fullWidth
      data-testid={dataTestId}
      label={label}
      SelectProps={{
        multiple: true,
        value: selected,
        onChange: handleChange,
        renderValue: (value) => rendersSelectedValues(value as string[]),
        MenuProps: menuProps,
      }}
      error={error}
      helperText={helperText}
      sx={[
        // reduce padding top when having value
        ![0, 5].includes(value.length) && {
          ".MuiInputBase-root": {
            ".MuiSelect-select": {
              pt: "10px",
              ".MuiInputBase-input": {
                pt: "10px",
              },
            },
          },
        },
      ]}
    >
      <MenuItem value={ALL_VALUE} style={{ padding: "0px" }}>
        <MuiCheckbox checked={isAllChecked} indeterminate={!!isIndeterminate} />
        <ListItemText primary="Select All" />
      </MenuItem>
      {options.map((option: string) => (
        <MenuItem key={option} value={option} style={{ padding: "0px" }}>
          <MuiCheckbox checked={selected.indexOf(option) > -1} />
          {renderer ? renderer(option) : <ListItemText primary={option} />}
        </MenuItem>
      ))}
    </ConductorSelect>
  );
}
