import { Grid, IconButton } from "@mui/material";
import { Button } from "components";
import { ConductorGroupFieldTitle } from "components/ui/inputs/ConductorGroupFieldTitle";
import { ChangeEvent, FunctionComponent, ReactNode, useState } from "react";
import { adjust, remove } from "utils";
import { ConductorEmptyGroupField } from "./ConductorEmptyGroupField";
import ConductorInput from "./ConductorInput";
import AddIcon from "../../icons/AddIcon";
import TrashIcon from "../../icons/TrashIcon";

interface ConductorStringArrayFormFieldProps {
  inputParameters: string[];
  onChange: (newInputParams: string[]) => void;
  someKey?: string;
  addButtonLabel?: ReactNode;
  label?: ReactNode;
  title?: ReactNode;
  compact?: boolean;
  emptyListMessage?: ReactNode;
}

export const ConductorStringArrayFormField: FunctionComponent<
  ConductorStringArrayFormFieldProps
> = ({
  inputParameters = [],
  onChange,
  someKey = "",
  addButtonLabel = "Add",
  label = "Value",
  title,
  compact,
  emptyListMessage,
}) => {
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
      {title ? <ConductorGroupFieldTitle title={title} /> : null}
      {inputParameters.length > 0 ? (
        <>
          <Grid container spacing={3}>
            {inputParameters.map((value, index) => (
              <Grid
                container
                spacing={1}
                key={`${index}_${inputParameters.length}_${someKey}`}
                sx={{ width: "100%" }}
              >
                <Grid flexGrow={1}>
                  <ConductorInput
                    label={label}
                    fullWidth
                    onTextInputChange={(newValue) => {
                      replaceItem(newValue, index);
                    }}
                    value={value}
                    onFocus={(
                      e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
                    ) => handleFocus(e)}
                    placeholder="e.g.: Cache-Control..."
                    sx={{ minWidth: "150px" }}
                  />
                </Grid>

                <Grid alignSelf="center">
                  <IconButton onClick={() => deleteItem(index)}>
                    <TrashIcon />
                  </IconButton>
                </Grid>
              </Grid>
            ))}
          </Grid>
          <Button
            size="small"
            onClick={addItem}
            startIcon={<AddIcon />}
            sx={{ mt: 3 }}
          >
            {addButtonLabel}
          </Button>
        </>
      ) : (
        <ConductorEmptyGroupField
          addButtonLabel={addButtonLabel}
          handleAddItem={addItem}
          compact={compact}
          emptyListMessage={emptyListMessage}
        />
      )}
    </>
  );
};
