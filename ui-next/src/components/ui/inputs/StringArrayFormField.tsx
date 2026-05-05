import { Grid, IconButton } from "@mui/material";
import { Trash as DeleteIcon, Plus } from "@phosphor-icons/react";
import { Button, Input } from "components";
import { ChangeEvent, FunctionComponent, useState } from "react";
import { adjust, remove } from "utils";

interface StringArrayFormFieldProps {
  inputParameters: string[];
  onChange: (newInputParams: string[]) => void;
  someKey?: string;
}

export const StringArrayFormField: FunctionComponent<
  StringArrayFormFieldProps
> = ({ inputParameters = [], onChange, someKey = "" }) => {
  const [newItemValue, setNewItemValue] = useState<string>("");
  const replaceItem = (newValue: string, index: number) => {
    onChange(adjust(index, () => newValue, inputParameters));
  };

  const deleteItem = (idx: number) => {
    onChange(remove(idx, 1, inputParameters));
  };
  const addItem = () => {
    onChange(inputParameters.concat(newItemValue));
    setNewItemValue("");
  };

  const handleFocus = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    e.target.select();
  };

  return (
    <>
      {inputParameters.map((value, index) => (
        <Grid
          container
          alignItems="flex-end"
          spacing={3}
          marginBottom={2}
          key={`${index}_${inputParameters.length}_${someKey}`}
          sx={{ width: "100%" }}
        >
          <Grid
            size={{
              md: 5,
            }}
          >
            <Input
              fullWidth
              onChange={(newValue) => {
                replaceItem(newValue, index);
              }}
              value={value}
              autoFocus
              onFocus={(
                e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
              ) => handleFocus(e)}
              placeholder="e.g.: Cache-Control..."
              sx={{ minWidth: "150px" }}
            />
          </Grid>

          <Grid size={2}>
            <IconButton onClick={() => deleteItem(index)}>
              <DeleteIcon size={24} />
            </IconButton>
          </Grid>
        </Grid>
      ))}
      <Grid
        container
        sx={{ width: "100%" }}
        spacing={3}
        marginBottom={2}
        alignItems="flex-end"
      >
        <Grid size={2}>
          <Button
            size="small"
            onClick={() => addItem()}
            startIcon={<Plus size={12} />}
          >
            Add
          </Button>
        </Grid>
      </Grid>
    </>
  );
};
