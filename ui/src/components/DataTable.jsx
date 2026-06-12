import React, { useMemo, useState } from "react";
import RawDataTable from "react-data-table-component";
import {
  Checkbox,
  MenuItem,
  ListItemText,
  IconButton,
  Menu,
  Tooltip,
  Popover,
} from "@material-ui/core";
import ViewColumnIcon from "@material-ui/icons/ViewColumn";
import SearchIcon from "@material-ui/icons/Search";
import { Heading, Select, Input } from "./";
import { timestampRenderer, timestampMsRenderer } from "../utils/helpers";
import { useLocalStorage } from "../utils/localstorage";

import _ from "lodash";
export const DEFAULT_ROWS_PER_PAGE = 15;

export default function DataTable(props) {
  const {
    localStorageKey,
    columns,
    data,
    options,
    defaultShowColumns,
    paginationPerPage = 15,
    showFilter = true,
    showColumnSelector = true,
    paginationServer = false,
    title,
    onFilterChange,
    initialFilterObj,
    ...rest
  } = props;

  const DEFAULT_FILTER_OBJ = {
    columnName: columns.find((col) => col.searchable !== false).name,
    substring: "",
  };

  // If no defaultColumns passed - use all columns
  const defaultColumns = useMemo(
    () =>
      props.defaultShowColumns || props.columns.map((col) => getColumnId(col)),
    [props.defaultShowColumns, props.columns]
  );

  const [tableState, setTableState] = useLocalStorage(
    localStorageKey,
    defaultColumns
  );

  const [filterObj, setFilterObj] = useState(
    initialFilterObj || DEFAULT_FILTER_OBJ
  );

  const handleFilterChange = (val) => {
    setFilterObj(val);
    if (onFilterChange) {
      if (!_.isEmpty(val.substring)) {
        onFilterChange(val);
      } else {
        onFilterChange(undefined);
      }
    }
  };

  // Append bodyRenderer for date fields;
  const dataTableColumns = useMemo(() => {
    let viewColumns = [];
    if (tableState) {
      for (let col of columns) {
        if (tableState.includes(getColumnId(col))) {
          viewColumns.push(col);
        }
      }
    } else {
      viewColumns = columns;
    }

    return viewColumns.map((column) => {
      let {
        id,
        name,
        label,
        type,
        renderer,
        wrap = true,
        sortable = true,
        ...rest
      } = column;

      const internalOptions = {};
      if (type === "date") {
        internalOptions.format = (row) => timestampRenderer(_.get(row, name));
      } else if (type === "date-ms") {
        internalOptions.format = (row) => timestampMsRenderer(_.get(row, name));
      } else if (type === "json") {
        internalOptions.format = (row) => JSON.stringify(_.get(row, name));
      }

      if (renderer) {
        internalOptions.format = (row) => renderer(_.get(row, name), row);
      }

      return {
        id: getColumnId(column),
        selector: name,
        name: getColumnLabel(column),
        sortable: sortable,
        wrap: wrap,
        type,
        ...internalOptions,
        ...rest,
      };
    });
  }, [tableState, columns]);

  const filteredItems = useMemo(() => {
    const column = dataTableColumns.find(
      (col) => col.id === filterObj.columnName
    );

    if (!filterObj.substring || !filterObj.columnName) {
      return data;
    } else {
      try {
        const regexp = new RegExp(filterObj.substring, "i");

        return data.filter((row) => {
          let target;
          if (
            column.type === "json" ||
            column.type === "date" ||
            column.type === "date-ms" ||
            column.searchable === "calculated"
          ) {
            target = column.format(row);

            if (!_.isString(target)) {
              target = JSON.stringify(target);
            }
          } else {
            target = _.get(row, column.selector);
          }

          return _.isString(target) && regexp.test(target);
        });
      } catch (e) {
        // Bad or incomplete Regexp
        console.log(e);
        return [];
      }
    }
  }, [data, dataTableColumns, filterObj]);

  return (
    <RawDataTable
      title={<Heading level={0}>{title}</Heading>}
      columns={dataTableColumns}
      data={filteredItems}
      pagination
      paginationServer={paginationServer}
      paginationPerPage={paginationPerPage}
      paginationRowsPerPageOptions={[15, 30, 100, 1000]}
      actions={
        <>
          {!paginationServer && showFilter && (
            <Filter
              columns={columns}
              filterObj={filterObj}
              setFilterObj={handleFilterChange}
            />
          )}
          {showColumnSelector && (
            <ColumnsSelector
              columns={columns}
              selected={tableState}
              setSelected={setTableState}
              defaultColumns={defaultColumns}
            />
          )}
        </>
      }
      {...rest}
    />
  );
}

function Filter({ columns, filterObj, setFilterObj }) {
  const [anchorEl, setAnchorEl] = React.useState(null);

  const handleClick = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleValueChange = (v) => {
    setFilterObj({
      columnName: filterObj.columnName,
      substring: v,
    });
  };

  const handleColumnChange = (c) => {
    setFilterObj({
      columnName: c,
      substring: "",
    });
  };

  return (
    <>
      <Tooltip title="Show Columns">
        <IconButton
          onClick={handleClick}
          label="Columns"
          color={_.get(filterObj, "substring") !== "" ? "primary" : "default"}
        >
          <SearchIcon />
        </IconButton>
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
          style={{ marginRight: 15, width: 200 }}
          onChange={(e) => handleColumnChange(e.target.value)}
          value={filterObj.columnName}
          renderValue={(v) => getColumnLabelById(v, columns)}
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
          style={{ marginRight: 15, width: 200 }}
          value={filterObj.substring}
          onChange={handleValueChange}
        />
      </Popover>
    </>
  );
}

function getColumnLabelById(columnId, columns) {
  const col = columns.find((c) => c.id === columnId || c.name === columnId);
  return col.label || col.name;
}

function getColumnLabel(col) {
  return col.label || col.name;
}

function getColumnId(col) {
  return col.id || col.name;
}

function ColumnsSelector({ columns, selected, setSelected, defaultColumns }) {
  const [anchorEl, setAnchorEl] = React.useState(null);

  const handleClick = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleChange = (columnId, checked) => {
    if (!checked && selected.includes(columnId)) {
      setSelected(selected.filter((v) => v !== columnId));
    } else {
      setSelected([...selected, columnId]);
    }
  };

  const reset = () => {
    setSelected(defaultColumns);
  };
  return (
    <>
      <Tooltip title="Show Columns">
        <IconButton onClick={handleClick} label="Columns">
          <ViewColumnIcon />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleClose}
        getContentAnchorEl={null}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
      >
        {[
          ...columns.map((column) => (
            <MenuItem
              key={getColumnId(column)}
              value={getColumnId(column)}
              dense
            >
              <Checkbox
                checked={selected.includes(getColumnId(column))}
                onChange={(e) =>
                  handleChange(getColumnId(column), e.target.checked)
                }
              />
              <ListItemText primary={getColumnLabel(column)} />
            </MenuItem>
          )),
          <MenuItem key="_reset" value="_reset" onClick={reset}>
            <ListItemText>Reset to default</ListItemText>
          </MenuItem>,
        ]}
      </Menu>
    </>
  );
}
