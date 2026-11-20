import { Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
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
  /**
   * Remount hint for the rows. Each row keeps its key name in local state seeded on
   * mount, so when the variable set is replaced wholesale — switching prompt version —
   * the rows have to be rebuilt or they keep showing the previous names.
   */
  someKey?: string;
};

const PromptVariables = ({
  currentVariables,
  onChange,
  updateField,
  task,
  someKey,
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
            someKey={someKey}
            autoFocusField={false}
          />
        </Grid>
      )}
    </>
  );
};

export default PromptVariables;
