import { useState } from "react";
import {
  Box,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Switch,
} from "@mui/material";
import { Trash as DeleteIcon } from "@phosphor-icons/react";
import {
  ButtonGroup,
  DropdownButton,
  Heading,
  IconButton,
  Input,
  NavLink,
  Paper,
  Select,
  SplitButton,
  Tab,
  Tabs,
  Text,
} from "components";

import EnhancedTable from "./EnhancedTable";
import DataTableDemo from "./DataTableDemo";
import { useAction } from "utils/query";
import top100Films from "./sampleMovieData";
import Dropdown from "components/Dropdown";
import sharedStyles from "../styles";
import { logger } from "utils/index";
import Button from "components/MuiButton";
import MuiCheckbox from "components/MuiCheckbox";

export default function KitchenSink() {
  return (
    <Box sx={[sharedStyles.wrapper, sharedStyles.padded]}>
      <Grid container sx={{ width: "100%" }} spacing={5}>
        <Grid size={12}>
          <p>This is a Hawkins-like theme based on vanilla Material-UI.</p>
        </Grid>
        <Grid size={12}>
          <NavLink path="/kitchen/gantt">Gantt</NavLink>
        </Grid>
        <Grid size={12}>
          <HeadingSection />
        </Grid>
        <Grid size={12}>
          <TabsSection />
        </Grid>
        <Grid size={12}>
          <Buttons />
        </Grid>
        <Grid size={12}>
          <Toggles />
        </Grid>
        <Grid size={12}>
          <Checkboxes />
        </Grid>
        <Grid size={12}>
          <Inputs />
        </Grid>
        <Grid size={12}>
          <Selects />
        </Grid>
        <Grid size={12}>
          <EnhancedTable />
        </Grid>
        <Grid size={12}>
          <DataTableDemo />
        </Grid>
        <Grid size={12}>
          <MutationTest />
        </Grid>
      </Grid>
    </Box>
  );
}

const HeadingSection = () => {
  return (
    <Paper padded>
      <Heading level={0}>Heading Level Zero</Heading>
      <Heading level={1}>Heading Level One</Heading>
      <Heading level={2}>Heading Level Two</Heading>
      <Heading level={3}>Heading Level Three</Heading>
      <Heading level={4}>Heading Level Four</Heading>
      <Heading level={5}>Heading Level Five</Heading>
      <Text level={0}>Text Level Zero</Text>
      <Text level={1}>Text Level One</Text>
      <Text level={2}>Text Level Two</Text>

      <div>Default &lt;div&gt;</div>
      <div>Default &lt;p&gt;</div>
    </Paper>
  );
};

const TabsSection = () => {
  const [tabIndex, setTabIndex] = useState(0);

  return (
    <Paper padded>
      <Heading level={3} gutterBottom>
        Tabs
      </Heading>
      <Heading level={2} gutterBottom>
        Page Level
      </Heading>
      <Heading level={1} gutterBottom>
        Full Width
      </Heading>
      <Paper variant="outlined" style={{ width: 800, marginBottom: 30 }}>
        <Tabs value={tabIndex} variant="fullWidth">
          <Tab label="Tab A" onClick={() => setTabIndex(0)} />
          <Tab label="Tab B" onClick={() => setTabIndex(1)} />
          <Tab label="Tab C" onClick={() => setTabIndex(2)} />
          <Tab label="Tab D" onClick={() => setTabIndex(3)} />
        </Tabs>
        <div style={{ padding: 15 }}>Tab content {tabIndex}</div>
      </Paper>

      <Heading level={1} gutterBottom>
        Fixed Width
      </Heading>
      <Paper variant="outlined" style={{ width: 800, marginBottom: 30 }}>
        <Tabs value={tabIndex}>
          <Tab label="Tab A" onClick={() => setTabIndex(0)} />
          <Tab label="Tab B" onClick={() => setTabIndex(1)} />
          <Tab label="Tab C" onClick={() => setTabIndex(2)} />
          <Tab label="Tab D" onClick={() => setTabIndex(3)} />
        </Tabs>
        <div style={{ padding: 15 }}>Tab content {tabIndex}</div>
      </Paper>

      <Heading level={2} gutterBottom>
        Contextual
      </Heading>

      <Heading level={1} gutterBottom>
        Full Width
      </Heading>
      <Paper variant="outlined" style={{ width: 500, marginBottom: 30 }}>
        <Tabs value={tabIndex} variant="fullWidth" contextual>
          <Tab label="Tab A" onClick={() => setTabIndex(0)} />
          <Tab label="Tab B" onClick={() => setTabIndex(1)} />
          <Tab label="Tab C" onClick={() => setTabIndex(2)} />
          <Tab label="Tab D" onClick={() => setTabIndex(3)} />
        </Tabs>
        <div style={{ padding: 15 }}>Tab content {tabIndex}</div>
      </Paper>
      <Heading level={1} gutterBottom>
        Fixed Width
      </Heading>

      <Paper variant="outlined" style={{ width: 800 }}>
        <Tabs value={tabIndex} contextual>
          <Tab label="Tab A" onClick={() => setTabIndex(0)} />
          <Tab label="Tab B" onClick={() => setTabIndex(1)} />
          <Tab label="Tab C" onClick={() => setTabIndex(2)} />
          <Tab label="Tab D" onClick={() => setTabIndex(3)} />
        </Tabs>
        <div style={{ padding: 15 }}>Tab content {tabIndex}</div>
      </Paper>
    </Paper>
  );
};

const Buttons = () => (
  <Paper style={{ padding: 15 }}>
    <Heading level={3} gutterBottom>
      Button
    </Heading>

    <Grid container sx={{ width: "100%" }} spacing={4}>
      <Grid>
        <Button>Primary</Button>
      </Grid>
      <Grid>
        <Button variant="outlined" color="secondary">
          Secondary
        </Button>
      </Grid>
      <Grid>
        <Button>Tertiary</Button>
      </Grid>
      <Grid>
        <ButtonGroup
          options={[{ label: "One" }, { label: "Two" }, { label: "Three" }]}
        />
      </Grid>
      <Grid>
        <SplitButton
          options={[
            {
              label: "Create a merge commit",
              handler: () => alert("you clicked 1"),
            },
            {
              label: "Squash and merge",
              handler: () => alert("you clicked 2"),
            },
            {
              label: "Rebase and merge",
              handler: () => alert("you clicked 3"),
            },
          ]}
          onPrimaryClick={() => alert("main button")}
        >
          Split Button
        </SplitButton>
      </Grid>
      <Grid>
        <DropdownButton
          options={[
            {
              label: "Create a merge commit",
              handler: () => alert("you clicked 1"),
            },
            {
              label: "Squash and merge",
              handler: () => alert("you clicked 2"),
            },
            {
              label: "Rebase and merge",
              handler: () => alert("you clicked 3"),
            },
          ]}
        >
          Dropdown Button
        </DropdownButton>
      </Grid>
      <Grid>
        <IconButton>
          <DeleteIcon size={20} />
        </IconButton>
      </Grid>
      <Grid size={12}>
        <ButtonGroup
          label="Button Group with Label"
          options={[{ label: "One" }, { label: "Two" }, { label: "Three" }]}
        />
      </Grid>
    </Grid>
  </Paper>
);

const Toggles = () => {
  const [toggleChecked, setToggleChecked] = useState(false);

  return (
    <Paper style={{ padding: 15 }}>
      <Heading level={3} gutterBottom>
        Toggle
      </Heading>
      <Switch
        checked={toggleChecked}
        onChange={() => setToggleChecked(!toggleChecked)}
        color="primary"
      />
    </Paper>
  );
};

const Checkboxes = () => {
  const [toggleChecked, setToggleChecked] = useState(false);

  return (
    <Paper style={{ padding: 15 }}>
      <Heading level={3} gutterBottom>
        Checkbox
      </Heading>
      <MuiCheckbox
        checked={toggleChecked}
        onChange={() => setToggleChecked(!toggleChecked)}
        color="primary"
      />
    </Paper>
  );
};

const Inputs = () => (
  <Paper style={{ padding: 15 }}>
    <Heading level={3} gutterBottom>
      Input
    </Heading>

    <Input
      label="Input Label via label attribute"
      style={{ marginBottom: 20 }}
    />

    <Input label="Fullwidth" fullWidth style={{ marginBottom: 20 }} />

    <Input label="Clearable" clearable style={{ marginBottom: 20 }} />

    <FormControl style={{ display: "block", marginBottom: 20 }}>
      <InputLabel>Input Label via FormControl/InputLabel</InputLabel>
      <Input />
    </FormControl>

    <Input label="DateTime" type="datetime-local" />
  </Paper>
);

const Selects = () => {
  const [value, setValue] = useState(10);
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
        getOptionLabel={(option) => option.title}
      />

      <Dropdown
        style={{ marginBottom: 20, width: 300 }}
        label="Autocomplete Clearable"
        options={top100Films}
        getOptionLabel={(option) => option.title}
      />

      <Dropdown
        fullWidth
        debug
        style={{ marginBottom: 20 }}
        label="Autocomplete Fullwidth"
        disableClearable
        options={top100Films}
        getOptionLabel={(option) => option.title}
      />

      <Dropdown
        multiple
        label="Multiple Pills"
        options={top100Films}
        getOptionLabel={(option) => option.title}
        defaultValue={[top100Films[13]]}
        style={{ width: 500 }}
        filterSelectedOptions
      />
    </Paper>
  );
};

const MutationTest = () => {
  const postAction = useAction("/dummy/post", "post", {
    onSuccess: (data) => logger.log("onsuccess", data),
    onError: (err) => logger.log("onerror", err),
  });

  const putAction = useAction("/dummy/put", "put", {
    onSuccess: (data) => logger.log("onsuccess", data),
    onError: (err) => logger.log("onerror", err),
  });

  const deleteAction = useAction("/dummy/delete", "delete", {
    onSuccess: (data) => logger.log("onsuccess", data),
    onError: (err) => logger.log("onerror", err),
  });

  return (
    <Paper style={{ padding: 15 }}>
      <Heading level={3} gutterBottom>
        Mutations
      </Heading>
      <Grid container sx={{ width: "100%" }} spacing={4}>
        <Grid>
          <Button onClick={() => postAction.mutate({ body: "{}" })}>
            POST
          </Button>
        </Grid>
        <Grid>
          <Button onClick={() => putAction.mutate()}>PUT</Button>
        </Grid>
        <Grid>
          <Button onClick={() => deleteAction.mutate()}>DELETE</Button>
        </Grid>
      </Grid>
    </Paper>
  );
};
