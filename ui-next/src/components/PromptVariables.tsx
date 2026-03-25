import { Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { TaskDef } from "types";

type PromptVariablesProps = {
  currentVariables: string | Record<string, string>;
  onChange: (t: Partial<TaskDef>) => void;
  updateField: (
    path: string,
    value: unknown,
    task: Partial<TaskDef>,
  ) => Partial<TaskDef>;
  task: Partial<TaskDef>;
};

const PromptVariables = ({
  currentVariables,
  onChange,
  updateField,
  task,
}: PromptVariablesProps) => {
  return (
    <>
      {typeof currentVariables === "string" ? (
        <Grid size={6}>
          <ConductorAutocompleteVariables
            openOnFocus
            onChange={(value: string) =>
              onChange(
                updateField(`inputParameters.promptVariables`, value, task),
              )
            }
            value={currentVariables}
            label="Prompt variables"
          />
        </Grid>
      ) : (
        <Grid size={12}>
          <ConductorFlatMapFormBase
            showFieldTypes={true}
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add variable"
            onChange={(value: Record<string, unknown>) =>
              onChange(
                updateField(`inputParameters.promptVariables`, value, task),
              )
            }
            value={{ ...currentVariables }}
            autoFocusField={false}
          />
        </Grid>
      )}
    </>
  );
};

export default PromptVariables;
