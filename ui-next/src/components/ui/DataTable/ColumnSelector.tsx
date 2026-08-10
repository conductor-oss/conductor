import { useCallback, useState, FunctionComponent } from "react";
import { ListItemText, Menu, MenuItem, Tooltip, Box } from "@mui/material";
import { Columns } from "@phosphor-icons/react";
import _isEmpty from "lodash/isEmpty";
import { adjust } from "utils/array";
import { getColumnLabel, getColumnId } from "./helpers";
import { SerializableColumn } from "./state/types";
import Button from "components/ui/buttons/MuiButton";
import MuiCheckbox from "components/ui/MuiCheckbox";

interface ColumnSorterProps {
  columns: SerializableColumn[];
  defaultShowColumns: string[];
  onColumnVisibilityChange: (
    columnsOrder: SerializableColumn[],
    columnsVisibility: SerializableColumn[],
  ) => void;
}

export const ColumnsSelector: FunctionComponent<ColumnSorterProps> = ({
  columns,
  defaultShowColumns,
  onColumnVisibilityChange,
}) => {
  const [anchorEl, setAnchorEl] = useState(null);

  const handleClick = (event: any) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleChange = useCallback(
    ({ checked, columnIdx }: { checked: boolean; columnIdx: number }) => {
      const changedVisibility = adjust(
        columnIdx,
        () => ({
          ...columns[columnIdx],
          omit: checked,
        }),
        columns,
      );
      onColumnVisibilityChange(columns, changedVisibility);
    },
    [onColumnVisibilityChange, columns],
  );

  const reset = useCallback(() => {
    const resetVisibility = columns.map((c) => ({
      ...c,
      omit: _isEmpty(defaultShowColumns)
        ? false
        : !defaultShowColumns.includes(getColumnId(c)),
    }));

    onColumnVisibilityChange(columns, resetVisibility);
  }, [onColumnVisibilityChange, columns, defaultShowColumns]);

  return (
    <>
      <Tooltip title="Show columns">
        <Box sx={{ textAlign: "center" }}>
          <Button
            sx={{ textAlign: "center" }}
            variant="text"
            size="small"
            startIcon={<Columns />}
            onClick={handleClick}
            color="inherit"
          >
            Columns
          </Button>
        </Box>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
      >
        {columns
          .map((column, columnIdx) => (
            <MenuItem
              key={column.id || columnIdx}
              value={column.id}
              sx={{
                borderBottom: "1px solid rgba(128,128,128,.25)",
              }}
              onClick={() => {
                const isChecked = column.omit;
                handleChange({
                  checked: !isChecked,
                  columnIdx,
                });
              }}
            >
              <MuiCheckbox checked={!column.omit} />
              <ListItemText
                primary={getColumnLabel(column)}
                secondary={column.tooltip ? column.tooltip : undefined}
              />
            </MenuItem>
          ))
          .concat(
            <MenuItem key="_reset" value="_reset" onClick={reset}>
              <ListItemText>Reset to default</ListItemText>
            </MenuItem>,
          )}
      </Menu>
    </>
  );
};
