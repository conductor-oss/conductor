import { useState } from "react";
import { Paper, Heading, Select } from "../../components";
import { MenuItem } from "@material-ui/core";
import top100Films from "./sampleMovieData";
import Dropdown from "../../components/Dropdown";

export default function () {
  const [value, setValue] = useState(10);
  const [dropdownValue, setDropdownValue] = useState();
  const [dropdownValues, setDropdownValues] = useState([]);

  return (
    <Paper style={{ padding: 15 }}>
      <Heading level={3} gutterBottom>
        Select
      </Heading>

      <Select
        style={{ marginBottom: 10 }}
        value={value}
        onChange={(evt) => setValue(evt.target.value)}
      >
        <MenuItem value={10}>Ten</MenuItem>
        <MenuItem value={20}>Twenty</MenuItem>
        <MenuItem value={30}>Thirty</MenuItem>
      </Select>

      <Select
        style={{ marginBottom: 20 }}
        label="With Label"
        value={value}
        onChange={(evt) => setValue(evt.target.value)}
      >
        <MenuItem value={10}>Ten</MenuItem>
        <MenuItem value={20}>Twenty</MenuItem>
        <MenuItem value={30}>Thirty</MenuItem>
      </Select>

      <Select
        fullWidth
        style={{ marginBottom: 20 }}
        label="Fullwidth"
        value={value}
        onChange={(evt) => setValue(evt.target.value)}
      >
        <MenuItem value={10}>Ten</MenuItem>
        <MenuItem value={20}>Twenty</MenuItem>
        <MenuItem value={30}>Thirty</MenuItem>
      </Select>

      <Dropdown
        style={{ marginBottom: 20, width: 300 }}
        label="Autocomplete"
        disableClearable
        options={top100Films}
        value={dropdownValue}
        getOptionLabel={(option) => option.title}
        onChange={(e, v) => setDropdownValue(v)}
      />

      <Dropdown
        style={{ marginBottom: 20, width: 300 }}
        label="Autocomplete Loading"
        disableClearable
        options={top100Films}
        value={dropdownValue}
        getOptionLabel={(option) => option.title}
        loading
      />

      <Dropdown
        style={{ marginBottom: 20, width: 300 }}
        label="Autocomplete Disabled"
        disabled
        options={top100Films}
        value={dropdownValue}
        getOptionLabel={(option) => option.title}
        onChange={(e, v) => setDropdownValue(v)}
      />

      <Dropdown
        style={{ marginBottom: 20, width: 300 }}
        label="Autocomplete Clearable"
        options={top100Films}
        value={dropdownValue}
        getOptionLabel={(option) => option.title}
        onChange={(e, v) => setDropdownValue(v)}
      />

      <Dropdown
        fullWidth
        debug
        style={{ marginBottom: 20 }}
        label="Autocomplete Fullwidth"
        disableClearable
        options={top100Films}
        value={dropdownValue}
        getOptionLabel={(option) => option.title}
        onChange={(e, v) => setDropdownValue(v)}
      />

      <Dropdown
        multiple
        label="Multiple Pills"
        options={top100Films}
        getOptionLabel={(option) => option.title}
        style={{ width: 500 }}
        value={dropdownValues}
        onChange={(e, v) => setDropdownValues(v)}
      />
    </Paper>
  );
}
