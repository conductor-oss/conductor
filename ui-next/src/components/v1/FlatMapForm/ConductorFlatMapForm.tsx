import { Box, Grid } from "@mui/material";
import Button from "components/MuiButton";
import { ConductorEmptyGroupField } from "components/v1/ConductorEmptyGroupField";
import { ConductorGroupFieldTitle } from "components/v1/ConductorGroupFieldTitle";
import { ConductorKeyValueInput } from "components/v1/FlatMapForm/ConductorKeyValueInput";
import AddIcon from "components/v1/icons/AddIcon";
import _omit from "lodash/omit";
import maybeVariable from "pages/definition/EditorPanel/TaskFormTab/forms/maybeVariableHOC";
import { FunctionComponent, ReactNode, useMemo } from "react";
import { SWITCH_CASE_PREFIX } from "utils/constants/switch";
import { getSequentiallySuffix, randomChars } from "utils/strings";
import { ConductorAutocompleteVariables } from "./ConductorAutocompleteVariables";

export interface ConductorFlatMapFormProps {
  title?: string | null;
  addItemLabel?: string;
  keyColumnLabel?: string;
  valueColumnLabel?: string;
  typeColumnLabel?: string;
  hideValue?: boolean;
  hideButtons?: boolean;
  showFieldTypes?: boolean;
  value?: Record<string, string>;
  onChange?: (newValues: Record<string, string>) => void;
  hiddenKeys?: string[];
  someKey?: string;
  enableAutocomplete?: boolean;
  autoFocusField?: boolean;
  customInput?: ReactNode;
  keyGenFunction?: () => string;
  valGenFunction?: () => string;
  focusOnField?: string;
  isSwitchCase?: boolean;
  placeholder?: string;
  compact?: boolean;
  emptyListMessage?: ReactNode;
  otherOptions?: string[];
}

const ConductorFlatMapFormBase: FunctionComponent<
  ConductorFlatMapFormProps
> = ({
  title = null,
  addItemLabel = "Add",
  keyColumnLabel = "Key",
  valueColumnLabel = "Value",
  typeColumnLabel = "Type",
  hideButtons = false,
  hideValue = false,
  showFieldTypes = false,
  value = {},
  onChange = (_newValues) => {},
  hiddenKeys,
  someKey = "",
  autoFocusField = true,
  customInput,
  keyGenFunction = () => `SomeKey${randomChars()}`,
  valGenFunction = () => `Some-val-${randomChars()}`,
  focusOnField,
  isSwitchCase,
  enableAutocomplete = true,
  placeholder,
  compact,
  emptyListMessage,
  otherOptions = [],
}: ConductorFlatMapFormProps) => {
  const [valueEntries, entryKeys] = useMemo(() => {
    const entries = Object.entries(value);
    const entryKeys = Object.keys(value);
    return [entries, entryKeys];
  }, [value]);

  const noVisibleKeys = valueEntries.every(([key]) =>
    hiddenKeys?.includes(key),
  );

  const replaceItem = ([newKey, newValue]: [string, any], idx: number) => {
    const modifiedPreservingOrder = Object.fromEntries(
      valueEntries.map(([key, val], innerIdx) =>
        innerIdx === idx ? [newKey, newValue] : [key, val],
      ),
    );

    onChange(modifiedPreservingOrder);
  };

  const addParameter = () => {
    const sequentialSuffix = (name: string) =>
      getSequentiallySuffix({
        name: name,
        refNames: entryKeys,
      }).name;
    const tempKey = isSwitchCase
      ? sequentialSuffix(SWITCH_CASE_PREFIX)
      : keyGenFunction();
    const tempValue = isSwitchCase ? ([] as any) : valGenFunction();

    const newValues = {
      ...value,
      [tempKey]: tempValue,
    };

    onChange(newValues);
  };

  const deleteItem = (key: string) => {
    onChange(_omit(value, key));
  };

  return (
    <>
      {title ? <ConductorGroupFieldTitle title={title} /> : null}

      {noVisibleKeys ? (
        <ConductorEmptyGroupField
          addButtonLabel={addItemLabel}
          handleAddItem={addParameter}
          compact={compact}
          emptyListMessage={emptyListMessage}
        />
      ) : (
        <>
          <Box>
            {valueEntries && (
              <Grid container spacing={4} sx={{ width: "100%" }}>
                {valueEntries.reduce((acc: Array<any>, [key, val], index) => {
                  return !hiddenKeys?.includes(key)
                    ? acc.concat(
                        <Grid
                          key={`${index}_${valueEntries.length}_${someKey}`}
                          container
                          alignItems="flex-start"
                          spacing={4}
                          sx={{ width: "100%" }}
                        >
                          <ConductorKeyValueInput
                            mKey={key}
                            value={val}
                            existingKeys={entryKeys}
                            onChangeKey={(newKey) => {
                              replaceItem([newKey, val], index);
                            }}
                            onChangeValue={(newValue: any) => {
                              replaceItem([key, newValue], index);
                            }}
                            placeholder={placeholder}
                            onDeleteItem={deleteItem}
                            hideValue={hideValue}
                            showFieldTypes={showFieldTypes}
                            hideButtons={hideButtons}
                            autoFocusField={autoFocusField}
                            keyColumnLabel={keyColumnLabel}
                            valueColumnLabel={valueColumnLabel}
                            typeColumnLabel={typeColumnLabel}
                            customInput={
                              enableAutocomplete ? (
                                <ConductorAutocompleteVariables
                                  label={valueColumnLabel}
                                  value={val}
                                  fullWidth
                                  otherOptions={otherOptions}
                                  onChange={(newValue) => {
                                    replaceItem([key, newValue], index);
                                  }}
                                />
                              ) : (
                                customInput
                              )
                            }
                            focusOnField={focusOnField}
                          />
                        </Grid>,
                      )
                    : acc;
                }, [])}
              </Grid>
            )}
          </Box>
          {!hideButtons ? (
            <Button
              size="small"
              onClick={addParameter}
              startIcon={<AddIcon />}
              sx={{ mt: 3 }}
            >
              {addItemLabel}
            </Button>
          ) : null}
        </>
      )}
    </>
  );
};

const ConductorFlatMapForm = maybeVariable(ConductorFlatMapFormBase);

export { ConductorFlatMapForm, ConductorFlatMapFormBase };
