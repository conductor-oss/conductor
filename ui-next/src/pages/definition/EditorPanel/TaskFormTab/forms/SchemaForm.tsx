import { Grid, Stack, Tooltip } from "@mui/material";
import { NotePencilIcon as EditIcon, EyeIcon } from "@phosphor-icons/react";
import MuiIconButton from "components/ui/buttons/MuiIconButton";
import { chain, map } from "lodash";
import { ConductorNameVersionField } from "components/inputs/ConductorNameVersionField";
import { pluginRegistry } from "plugins/registry";
import {
  forwardRef,
  FunctionComponent,
  useCallback,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { SchemaDefinition } from "types/SchemaDefinition";
import { EnforceSchema } from "./EnforceSchemaForm";
import TaskFormSection from "./TaskFormSection";

export interface SchemaFormValue {
  name: string;
  version?: number;
  type?: string;
}

interface SchemaFormItemProps {
  onChange: (value?: SchemaFormValue) => void;
  value?: SchemaFormValue;
  label: string;
  onRefetch: () => void;
  disabled?: boolean;
}

const SchemaFormItem = forwardRef<
  {
    refetch: () => void;
  },
  SchemaFormItemProps
>(({ onChange, value, label, disabled, onRefetch }, ref) => {
  const conductorNameVersionFieldRef = useRef<{ refetch: () => void }>(null);
  const [editingSchema, setEditingSchema] = useState<boolean>(false);
  const [previewSchema, setPreviewSchema] = useState<boolean>(false);

  // Get dialog components from plugin registry (enterprise-only)
  const SchemaEditDialog = pluginRegistry.getSchemaEditDialog();
  const SchemaPreviewDialog = pluginRegistry.getSchemaPreviewDialog();

  useImperativeHandle(ref, () => ({
    refetch: () => {
      conductorNameVersionFieldRef.current?.refetch();
    },
  }));

  const openEditSchema = useCallback(() => {
    setEditingSchema(true);
  }, []);

  const handlePreviewSchema = () => setPreviewSchema(true);
  const closePreviewSchema = () => setPreviewSchema(false);

  const closeEditSchema = useCallback(
    (
      schema:
        | {
            name: string;
            version?: number;
          }
        | undefined,
    ) => {
      if (schema) {
        onChange({ ...schema, type: "JSON" });
        onRefetch();
      }
      setEditingSchema(false);
    },
    [onChange, onRefetch],
  );

  const handleNameVersionChange = (
    val:
      | {
          name?: string;
          version?: number;
        }
      | undefined,
  ) => {
    if (val && val.name) {
      onChange({
        name: val.name,
        version: val.version,
        type: "JSON",
      });
    } else {
      onChange(undefined);
    }
  };
  return (
    <>
      <Stack direction="row" alignItems="flex-start" spacing={2}>
        <Stack
          sx={{
            flex: 1,
          }}
        >
          <ConductorNameVersionField
            disabled={disabled}
            ref={conductorNameVersionFieldRef}
            label={label}
            nameField={{
              id: `${label?.toLowerCase()?.replaceAll(" ", "-")}-name-input`,
            }}
            optionsUrl="/schema"
            versionField={{
              emptyText: "Latest Version",
            }}
            showErrorIfItemNotInList={true}
            mapOptions={(data: SchemaDefinition[]) =>
              chain(data)
                .groupBy("name")
                .map((group, key) => ({
                  name: key,
                  versions: map(group, "version"),
                }))
                .value()
            }
            value={value}
            onChange={handleNameVersionChange}
          />
        </Stack>
        {/* Preview button - only show if SchemaPreviewDialog is available */}
        {SchemaPreviewDialog && (
          <Tooltip title={"Preview"} placement="top">
            <MuiIconButton
              disabled={!value?.name || disabled}
              onClick={handlePreviewSchema}
            >
              <EyeIcon size={20} />
            </MuiIconButton>
          </Tooltip>
        )}
        {/* Edit button - only show if SchemaEditDialog is available */}
        {SchemaEditDialog && (
          <Tooltip
            title={!value?.name ? "Select a schema to edit it" : ""}
            placement="top"
          >
            <MuiIconButton
              disabled={!value?.name || disabled}
              onClick={openEditSchema}
            >
              <EditIcon size={20} />
            </MuiIconButton>
          </Tooltip>
        )}
      </Stack>

      {editingSchema && value && SchemaEditDialog && (
        <SchemaEditDialog
          open
          onClose={closeEditSchema}
          initialData={{
            isNewSchema: false,
            schemaName: value.name,
            schemaVersion: value.version?.toString(),
          }}
        />
      )}
      {previewSchema && value && SchemaPreviewDialog && (
        <SchemaPreviewDialog
          open
          onClose={closePreviewSchema}
          schemaName={value.name}
          schemaVersion={value.version}
        />
      )}
    </>
  );
});

export interface SchemaFormPropsValue {
  inputSchema?: SchemaFormValue;
  outputSchema?: SchemaFormValue;
  enforceSchema?: boolean;
}

export interface SchemaFormProps {
  value?: SchemaFormPropsValue;
  onChange: (value?: SchemaFormPropsValue) => void;
  hideOutputSchema?: boolean;
  hideInputSchema?: boolean;
  hideEnforceSchema?: boolean;
}

export const SchemaForm: FunctionComponent<SchemaFormProps> = ({
  onChange,
  value,
  hideInputSchema,
  hideOutputSchema,
  hideEnforceSchema,
}) => {
  const inputSchemaRef = useRef<{ refetch: () => void }>(null);
  const outputSchemaRef = useRef<{ refetch: () => void }>(null);

  const handleEnforceSchemaChange = useCallback(
    ({
      inputSchema,
      outputSchema,
    }: {
      inputSchema?: SchemaFormValue;
      outputSchema?: SchemaFormValue;
    }) => {
      if (!inputSchema && !outputSchema) {
        return false;
      } else {
        return true;
      }
    },
    [],
  );

  const handleEnforceSchemaSwitchChange = useCallback(
    (checked: boolean) => {
      onChange({
        ...value,
        enforceSchema: checked,
      });
    },
    [onChange, value],
  );

  const handleOnInputSchemaChange = useCallback(
    (schema?: SchemaFormValue) => {
      const enforceSchema = handleEnforceSchemaChange({
        inputSchema: schema,
        outputSchema: value?.outputSchema,
      });
      onChange({
        ...value,
        inputSchema: schema,
        enforceSchema,
      });
    },
    [onChange, value, handleEnforceSchemaChange],
  );
  const handleOnOutputSchemaChange = useCallback(
    (schema?: SchemaFormValue) => {
      const enforceSchema = handleEnforceSchemaChange({
        inputSchema: value?.inputSchema,
        outputSchema: schema,
      });
      onChange({
        ...value,
        outputSchema: schema,
        enforceSchema,
      });
    },
    [onChange, value, handleEnforceSchemaChange],
  );

  const triggerRefetchOnBothSchemas = useCallback(() => {
    if (inputSchemaRef.current) {
      inputSchemaRef.current.refetch();
    }
    if (outputSchemaRef.current) {
      outputSchemaRef.current.refetch();
    }
  }, []);

  const showEnforceSchemaSwitch = useMemo(() => {
    return !!(value?.inputSchema || value?.outputSchema);
  }, [value?.inputSchema, value?.outputSchema]);

  return (
    <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
      <Grid
        container
        spacing={4}
        sx={{
          marginBottom: 2,
        }}
      >
        {!hideEnforceSchema && (
          <Grid size={12} sx={{ paddingTop: 2 }}>
            <EnforceSchema
              onChange={handleEnforceSchemaSwitchChange}
              value={value?.enforceSchema}
              showEnforceSchemaSwitch={showEnforceSchemaSwitch}
            />
          </Grid>
        )}
        {!hideInputSchema && (
          <Grid size={12}>
            <SchemaFormItem
              ref={inputSchemaRef}
              onChange={handleOnInputSchemaChange}
              value={value?.inputSchema}
              label="Input Schema"
              onRefetch={triggerRefetchOnBothSchemas}
            />
          </Grid>
        )}
        {!hideOutputSchema && (
          <Grid size={12}>
            <SchemaFormItem
              ref={outputSchemaRef}
              onChange={handleOnOutputSchemaChange}
              value={value?.outputSchema}
              label="Output Schema"
              onRefetch={triggerRefetchOnBothSchemas}
            />
          </Grid>
        )}
      </Grid>
    </TaskFormSection>
  );
};
