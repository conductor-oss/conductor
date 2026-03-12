import { Box, MenuItem, Stack } from "@mui/material";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import { forwardRef, useImperativeHandle, useMemo } from "react";

import { ConductorAutoComplete } from "components/v1";
import ConductorSelect from "components/v1/ConductorSelect";
import { useFetch } from "utils/query";

export interface ConductorNameVersionFieldProps {
  label: string;
  optionsUrl: string;
  value?: {
    name: string;
    version?: number;
  };
  onChange?: (value?: { name?: string; version?: number }) => void;
  mapOptions?: (data: any) => { name: string; versions: number[] }[];
  nameField?: {
    id?: string;
    clearIndicator?: boolean;
  };
  versionField?: {
    id?: string;
    emptyText?: string;
    autocomplete?: boolean;
    required?: boolean;
  };
  showErrorIfItemNotInList?: boolean;
  disabled?: boolean;
}

export const ConductorNameVersionField = forwardRef<
  { refetch: () => void },
  ConductorNameVersionFieldProps
>(
  (
    {
      label,
      optionsUrl,
      value,
      nameField,
      versionField,
      onChange,
      mapOptions,
      showErrorIfItemNotInList = false,
      disabled,
    },
    ref,
  ) => {
    const { data, refetch } = useFetch(optionsUrl);

    // Expose the refetch method to the parent component via the ref
    useImperativeHandle(ref, () => ({
      refetch,
    }));

    const options: {
      name: string;
      versions: number[];
    }[] = useMemo(() => {
      return mapOptions ? mapOptions(data) : data || [];
    }, [data, mapOptions]);

    const versionOptions = useMemo(() => {
      if (_isNil(value?.name)) {
        return [];
      }
      const selectedOption = options.find(({ name }) => name === value?.name);
      if (!selectedOption) {
        return [];
      }
      return selectedOption.versions;
    }, [options, value?.name]);

    return (
      <>
        <Stack direction="row" spacing={2}>
          <Box
            sx={{
              flex: 1,
            }}
          >
            <ConductorAutoComplete
              fullWidth
              disabled={disabled}
              label={label}
              id={nameField?.id}
              options={options.map(({ name }) => name)}
              onChange={(_, val) => {
                const selectedOption = options.find(({ name }) => name === val);
                if (!selectedOption) {
                  onChange?.(undefined);
                  return;
                }
                onChange?.({
                  name: val,
                  version:
                    versionField?.autocomplete || versionField?.required
                      ? _last(selectedOption.versions)
                      : undefined,
                });
              }}
              error={
                showErrorIfItemNotInList &&
                value != null &&
                !options.some(({ name }) => name === value?.name)
              }
              value={value?.name || null}
              slotProps={
                nameField?.clearIndicator
                  ? {
                      clearIndicator: {
                        onClick: () => {
                          onChange?.(undefined);
                        },
                      },
                    }
                  : undefined
              }
            />
          </Box>
          <Box
            sx={{
              flexBasis: { xs: 100, md: 150 },
              minWidth: { xs: 100, md: 150 },
            }}
          >
            <ConductorSelect
              id={versionField?.id}
              label="Version"
              fullWidth
              disabled={!value?.name || disabled}
              value={
                value?.name && value?.version === undefined
                  ? "Latest Version"
                  : (value?.version ?? "")
              }
              onTextInputChange={(val) =>
                onChange?.({
                  name: value?.name,
                  version: val === "Latest Version" ? undefined : Number(val),
                })
              }
            >
              {!versionField?.required && value?.name && (
                <MenuItem value="Latest Version">
                  {versionField?.emptyText ?? "Latest version"}
                </MenuItem>
              )}
              {versionOptions.map((version) => (
                <MenuItem value={version} key={version}>
                  {`Version ${version}`}
                </MenuItem>
              ))}
            </ConductorSelect>
          </Box>
        </Stack>
      </>
    );
  },
);
