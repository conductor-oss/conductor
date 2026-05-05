import { Grid, Box } from "@mui/material";
import { useCallback, useState, useMemo } from "react";
import { Button, Input } from "components";

const ArrayForm = ({
  title = "Array",
  addItemLabel = "Add Item",
  valueColumnLabel = "Value",
  value = [],
  onChange = (_newValues) => {},
}: {
  title: string;
  addItemLabel: string;
  valueColumnLabel: string;
  value?: Array<string>;
  onChange?: (newValues: Array<string>) => void;
}) => {
  const [localValues, setLocalValues] = useState<Array<string>>(
    useMemo(() => value, [value]),
  );

  const addEmptyItem = useCallback(() => {
    // generate random string of six characters
    const suffix = Math.random().toString(36).substring(2, 7);
    const newValues = [...localValues, `Some-Value-${suffix}`];
    setLocalValues(newValues);
    onChange(newValues);
  }, [localValues, onChange]);

  const deleteItem = useCallback(
    (index: number) => {
      const newValues = [...localValues];
      newValues.splice(index, 1);

      setLocalValues(newValues);
      onChange(newValues);
    },
    [localValues, onChange],
  );

  const replaceItem = useCallback(
    (index: number, value: string) => {
      const newValues = [...localValues];
      newValues[index] = value;

      setLocalValues(newValues);
      onChange(newValues);
    },
    [localValues, onChange],
  );

  return (
    <Box>
      <Grid container sx={{ width: "100%" }} alignItems="flex-end" spacing={3}>
        <Grid size={12}>
          <h4 style={{ margin: 0 }}>{title}</h4>
        </Grid>
      </Grid>
      <Grid container sx={{ width: "100%" }} alignItems="flex-end" spacing={3}>
        <Grid size={7}>{valueColumnLabel}</Grid>
      </Grid>
      {localValues &&
        localValues.map((value, index) => (
          <Grid
            container
            sx={{ width: "100%" }}
            alignItems="flex-end"
            spacing={3}
            key={index}
          >
            <Grid size={10}>
              <Input
                fullWidth
                onChange={(newValue) => {
                  replaceItem(index, newValue);
                }}
                value={value}
                placeholder="e.g.: max-age=..."
              />
            </Grid>
            <Grid size={2}>
              <Button
                color="secondary"
                fullWidth
                onClick={() => deleteItem(index)}
              >
                Delete
              </Button>
            </Grid>
          </Grid>
        ))}
      <Grid container sx={{ width: "100%" }} style={{ display: "flex" }}>
        <Grid style={{ paddingTop: 10 }} size={12}>
          <Button onClick={() => addEmptyItem()}>{addItemLabel}</Button>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ArrayForm;
