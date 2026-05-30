import { Box, Grid, IconButton } from "@mui/material";
import _difference from "lodash/difference";
import _first from "lodash/first";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import { FunctionComponent, useMemo, useState } from "react";

import { Button } from "components";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorEmptyGroupField } from "components/ui/inputs/ConductorEmptyGroupField";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import AddIcon from "components/icons/AddIcon";
import TrashIcon from "components/icons/TrashIcon";
import { HEADER_SUGGESTIONS } from "utils/constants/httpSuggestions";
import { OBJECT_PROPERTY_NAME_REGEX } from "utils/regex";
import maybeVariable from "../maybeVariableHOC";

interface HeaderFieldProps {
  availableOptions: string[];
  onChange: (key: string, value: string) => void;
  headerKey?: string;
  value?: string;
  onRemove?: (key: string) => void;
  autoFocus?: boolean;
  existingKeys: string[];
}

const HeaderField: FunctionComponent<HeaderFieldProps> = ({
  availableOptions,
  onChange,
  headerKey,
  value,
  onRemove,
  existingKeys,
}) => {
  const [keyValuePair, setKeyValuePair] = useState<
    [string | undefined, string | undefined]
  >([headerKey, value]);
  const [isDuplicatedKey, setIsDuplicatedKey] = useState(false);
  const [invalidKey, setInvalidKey] = useState(false);
  const [errorMessage, setErrorMessage] = useState<{
    [key: string]: string;
  }>({});

  const handleSetKeyValuePair = (key?: string, value?: string) => {
    const newValue: [string | undefined, string | undefined] = [
      key && key !== "null" ? key : "",
      value,
    ];

    setKeyValuePair(newValue);
    if (key && !newValue.some(_isNil)) {
      onChange(key, value!);
    }
  };

  const handleDropdownInputChange = (value: string) => {
    const invalid = !OBJECT_PROPERTY_NAME_REGEX.test(value);
    const duplicated =
      _first(keyValuePair) === "" &&
      existingKeys.filter((key) => key === value).length >= 1;

    setErrorMessage((prevState) => {
      const previousState = { ...prevState };
      if (invalid) {
        previousState.invalid =
          "Key can only contain letters, numbers, and the following special characters: !#$%&'*+-.^_`|~";
      } else {
        delete previousState.invalid;
      }

      if (duplicated) {
        previousState.duplicated = "Key is duplicated";
      } else {
        delete previousState.duplicated;
      }

      return previousState;
    });

    setInvalidKey(invalid);
    setIsDuplicatedKey(duplicated);
  };

  return (
    <Grid
      container
      sx={{ width: "100%", display: "grid", gridTemplateColumns: "1fr auto" }}
      spacing={4}
      size={12}
    >
      <Grid container sx={{ width: "100%" }} spacing={4}>
        <Grid size={4}>
          <ConductorAutoComplete
            fullWidth
            label="Header"
            options={availableOptions}
            freeSolo
            value={_first(keyValuePair)}
            conductorInputProps={{
              placeholder: _isNil(headerKey) ? "New header" : "",
            }}
            onChange={(__, selectedKey: any) => {
              handleSetKeyValuePair(selectedKey, _last(keyValuePair));
            }}
            onBlur={(event: any) => {
              if (!invalidKey && !isDuplicatedKey) {
                handleSetKeyValuePair(event.target.value, _last(keyValuePair));
              }
            }}
            onTextInputChange={handleDropdownInputChange}
            error={isDuplicatedKey || invalidKey}
            helperText={Object.values(errorMessage).join("\n")}
          />
        </Grid>
        <Grid flexGrow={1}>
          <ConductorAutocompleteVariables
            label="Value"
            onChange={(val: any) =>
              handleSetKeyValuePair(_first(keyValuePair), val)
            }
            placeholder={_isEmpty(value) ? "New value" : ""}
            value={_last(keyValuePair)}
          />
        </Grid>
      </Grid>
      <Grid>
        {onRemove && (
          <IconButton
            onClick={() => onRemove!(headerKey!)}
            style={{ paddingTop: "0.42em" }}
          >
            <TrashIcon />
          </IconButton>
        )}
      </Grid>
    </Grid>
  );
};

interface ConductorAdditionalHeadersProps {
  headers: Record<string, string>;
  onChangeHeaders: (headers: Record<string, string>) => void;
}

const ConductorAdditionalHeadersBase: FunctionComponent<
  ConductorAdditionalHeadersProps
> = ({ headers = {}, onChangeHeaders }) => {
  const [valueEntries, entryKeys] = useMemo(() => {
    const entries = Object.entries(headers);
    const entryKeys = Object.keys(headers);
    return [entries, entryKeys];
  }, [headers]);
  const handleAddChangeItem = (
    headerKey: string,
    headerValue: string,
    idx: number,
  ) => {
    const modifiedPreservingOrder =
      idx === entryKeys.length
        ? Object.assign({}, headers, { [headerKey]: headerValue })
        : Object.fromEntries(
            valueEntries.map(([key, val], innerIdx) =>
              innerIdx === idx ? [headerKey, headerValue] : [key, val],
            ),
          );

    onChangeHeaders(modifiedPreservingOrder);
  };

  const handleRemoveItem = (key: string) => {
    const valueClone = { ...headers };
    delete valueClone[key];
    onChangeHeaders(valueClone);
  };

  return valueEntries.length === 0 ? (
    <ConductorEmptyGroupField
      addButtonLabel="Add header"
      handleAddItem={() => handleAddChangeItem("", "", entryKeys.length)}
    />
  ) : (
    <>
      <Box
        sx={{
          p: 6,
          borderRadius: "6px",
          border: "1px solid rgba(0, 0, 0, 0.12)",
          background: "rgba(0, 0, 0, 0.04)",
        }}
      >
        <Grid container sx={{ width: "100%" }} spacing={4}>
          {valueEntries.map(([key, value], idx) => (
            <HeaderField
              availableOptions={[key]
                .concat(_difference(HEADER_SUGGESTIONS, entryKeys))
                .filter((option) => option !== "")}
              key={`${idx}_${valueEntries.length}`}
              headerKey={key}
              value={value}
              onChange={(changedKey, changedValue) =>
                handleAddChangeItem(changedKey, changedValue, idx)
              }
              onRemove={handleRemoveItem}
              existingKeys={valueEntries.map((entry) => _first(entry)!)}
            />
          ))}
        </Grid>
      </Box>

      <Button
        size="small"
        onClick={() => handleAddChangeItem("", "", entryKeys.length)}
        startIcon={<AddIcon />}
        sx={{ mt: 3 }}
      >
        Add header
      </Button>
    </>
  );
};

const ConductorAdditionalHeaders = maybeVariable(
  ConductorAdditionalHeadersBase,
);
export { ConductorAdditionalHeaders, ConductorAdditionalHeadersBase };
