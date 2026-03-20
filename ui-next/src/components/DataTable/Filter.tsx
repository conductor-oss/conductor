import { Box, Button, MenuItem, Popover, Tooltip } from "@mui/material";
import { MagnifyingGlass as SearchIcon } from "@phosphor-icons/react";
import Input from "components/Input";
import Select from "components/Select";
import _get from "lodash/get";
import _isNil from "lodash/isNil";
import { FunctionComponent, useState } from "react";
import { getColumnId, getColumnLabel, getColumnLabelById } from "./helpers";
import { FilterObjectItem } from "./state";
import { RenderableColumn } from "./types";

export interface FilterProps {
  columns: RenderableColumn[];
  filterObj?: FilterObjectItem;
  setFilterObj: (filterObject: FilterObjectItem) => void;
}

export const Filter: FunctionComponent<FilterProps> = ({
  columns,
  filterObj,
  setFilterObj,
}) => {
  const [anchorEl, setAnchorEl] = useState(null);

  const handleClick = (event: any) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleValueChange = (v: string) => {
    setFilterObj({
      columnName: filterObj!.columnName,
      substring: v,
    });
  };

  const handleColumnChange = (c: string) => {
    setFilterObj({
      columnName: c,
      substring: "",
    });
  };

  return (
    <>
      <Tooltip title="Search">
        <Box sx={{ textAlign: "center" }}>
          <Button
            size="small"
            variant="text"
            startIcon={<SearchIcon />}
            onClick={handleClick}
            color={_get(filterObj, "substring") !== "" ? "primary" : "inherit"}
            disabled={_isNil(filterObj)}
          >
            Search
          </Button>
        </Box>
      </Tooltip>
      <Popover
        onClose={handleClose}
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
        PaperProps={{
          style: { padding: 10, display: "flex", flexDirection: "row" },
        }}
      >
        <Select
          label="Field"
          sx={{ marginRight: 4, width: 200 }}
          onChange={(e: any) => handleColumnChange(e.target.value)}
          value={filterObj!.columnName}
          renderValue={(v: any) => getColumnLabelById(v, columns)}
          displayEmpty={true}
        >
          {columns
            .filter((col) => col.searchable !== false)
            .map((col) => (
              <MenuItem value={getColumnId(col)} key={getColumnId(col)}>
                {getColumnLabel(col)}
              </MenuItem>
            ))}
        </Select>
        <Input
          clearable
          label="Substring"
          value={filterObj!.substring}
          onChange={handleValueChange}
        />
      </Popover>
    </>
  );
};
