import {
  Box,
  CircularProgress,
  Grid,
  GridProps,
  Theme,
  Tooltip,
} from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import { ArrowDown as SortIcon } from "@phosphor-icons/react";
import { useMachine, useSelector } from "@xstate/react";
import { path as _path } from "lodash/fp";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _isString from "lodash/isString";
import _noop from "lodash/noop";
import _omit from "lodash/omit";
import _stubTrue from "lodash/stubTrue";
import {
  FunctionComponent,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import RawDataTable, {
  TableProps,
  TableStyles,
} from "react-data-table-component";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { createTableTitle, logger, useLocalStorage } from "utils";
import { LOCAL_STORAGE_KEY } from "utils/constants/common";
import { ColumnsSelector } from "./ColumnSelector";
import { Filter } from "./Filter";
import { TagFilter } from "components/TagFilter";
import {
  createDefaultFilterObject,
  defaultFilterItemsSorter,
  formatForColumn,
} from "./helpers";
import { QuickSearch, QuickSearchProps } from "./QuickSearch";
import { dataTableMachine } from "./state";
import {
  DataTableEventTypes,
  FilterObjectItem,
  SerializableColumn,
} from "./state/types";
import { dataTableStyles } from "./styles";
import { ColumnCustomType, LegacyColumn, RenderableColumn } from "./types";

export const DEFAULT_ROWS_PER_PAGE = 15;

const headerdefaultbackcolor = "#f8f8f8";

export interface DataTableProps extends TableProps<any> {
  columns: LegacyColumn[]; //Next step should be enforcing the use of id. in all tables
  localStorageKey?: string;
  customActions?: ReactNode[];
  defaultShowColumns?: string[];
  showColumnSelector?: boolean;
  hideSearch?: boolean;
  titleComponent?: ReactNode;
  onFilterChange?: (filterObj?: FilterObjectItem) => void; // Dont really understand the undefined here
  initialFilterObj?: FilterObjectItem;
  quickSearchEnabled?: boolean;
  quickSearchPlaceholder?: string;
  quickSearchComponent?: FunctionComponent<QuickSearchProps>;
  createButton?: ReactNode;
  sortByDefault?: boolean;
  onSearchTermChange?: (searchTerm: string) => void;
  description?: ReactNode;
  searchTerm?: string;
  autoFocus?: boolean;
  useGlobalRowsPerPage?: boolean;
  searchModalContainerProps?: GridProps;
  onRowMouseEnter?: (row: any) => void;
  filterByTags?: boolean;
}

export const DataTable: FunctionComponent<DataTableProps> = (props) => {
  const {
    localStorageKey,
    columns,
    customActions,
    data = [],
    // options,
    defaultShowColumns = [],
    pagination = true,
    paginationPerPage = 15,
    showColumnSelector = true,
    paginationServer = false,
    hideSearch = false,
    title,
    titleComponent,
    noDataComponent,
    onFilterChange,
    initialFilterObj,
    quickSearchEnabled = false,
    quickSearchPlaceholder = "Search",
    customStyles,
    createButton,
    paginationComponent,
    sortByDefault = true,
    onSearchTermChange = _noop,
    description,
    quickSearchComponent: QuickSearchComponent = QuickSearch,
    searchTerm: inputSearchTerm = "",
    autoFocus = true,
    onChangeRowsPerPage,
    onColumnOrderChange,
    useGlobalRowsPerPage = true,
    searchModalContainerProps,
    onRowMouseEnter,
    filterByTags = false,
    ...rest
  } = props;
  const { mode } = useContext(ColorModeContext);
  const [persistRowsPerPage, setPersistRowsPerPage] = useLocalStorage(
    LOCAL_STORAGE_KEY.ROWS_PER_PAGE,
    paginationPerPage,
  );

  // Tag filter state
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const handleChangeRowsPerPage = (
    rowsPerPage: number,
    currentPage: number,
  ) => {
    if (useGlobalRowsPerPage) {
      setPersistRowsPerPage(rowsPerPage);
    }

    if (onChangeRowsPerPage) {
      onChangeRowsPerPage(rowsPerPage, currentPage);
    }
  };

  // Checking responsive width
  const isValidWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );

  // Prepare column renderer.
  const renderedColumns = columns.map((column): RenderableColumn => {
    const {
      id: _id,
      name: _name,
      wrap = true,
      sortable = true,
      selector: customSelector,
      ...rest
    } = column;
    const format = formatForColumn(column as LegacyColumn);
    return {
      id: column.id,
      selector: customSelector || ((row: any) => row[column.name as string]),
      name: column.label,
      sortable: sortable,
      wrap: wrap,
      // type,
      // label,
      omit: _isEmpty(defaultShowColumns)
        ? false
        : !defaultShowColumns.includes(column.id),
      reorder: true,
      format,
      ...rest,
    };
  });

  const [, send, actor] = useMachine(dataTableMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      columnOrderAndVisibility: renderedColumns, // default to column renderer, TODO defaultShowColumns may hold the order
      localStorageKey,
      filterObj: initialFilterObj || createDefaultFilterObject(renderedColumns),
      searchTerm: "",
    },
  });

  // Will hold the order and visibility
  const columnOrderVisibility = useSelector(
    actor,
    (state) => state.context.columnOrderAndVisibility,
  );

  const filterObj = useSelector(actor, (state) => state.context.filterObj);

  const searchTerm = useSelector(actor, (state) => state.context.searchTerm);

  // converts rendered columns to a map, extracting selector that can hold closures.
  const renderedColumnsMap = Object.fromEntries(
    renderedColumns.map((c) => [c.id, _omit(c, ["omit"])]),
  );

  // Using order and selector construct the resultant.
  const orderedColumns = columnOrderVisibility.map((col) => ({
    ...col,
    ...renderedColumnsMap[col.id],
  }));

  const columnsWithTooltips = orderedColumns.map((column) => {
    if (column.tooltip) {
      return {
        ...column,
        name: (
          <Tooltip title={column.tooltip} placement="top">
            <Box>{column.label}</Box>
          </Tooltip>
        ),
      };
    }
    return column;
  });

  const handleColumnOrderChange = (
    cols: LegacyColumn[],
    viewCols: SerializableColumn[],
  ) => {
    const viewColumnsNameMapI = Object.fromEntries(
      viewCols.map((c) => [c.id, c]),
    );
    const columnResult = cols.map(({ id }) => viewColumnsNameMapI[id]);
    send({
      type: DataTableEventTypes.SET_ORDER_AND_VISIBILITY,
      data: columnResult,
    });

    if (onColumnOrderChange) {
      onColumnOrderChange(columnResult);
    }
  };

  const handleSearchTermChange = useCallback(
    (searchTerm: string) => {
      send({
        type: DataTableEventTypes.SET_SEARCH_TERM,
        searchTerm,
      });
      onSearchTermChange(searchTerm);
    },
    [onSearchTermChange, send],
  );

  useEffect(() => {
    handleSearchTermChange(inputSearchTerm);
  }, [handleSearchTermChange, inputSearchTerm]);

  const handleFilterObjectChange = (filterObj: FilterObjectItem) => {
    send({
      type: DataTableEventTypes.SET_FILTER_OBJ,
      filterObj,
    });

    // This makes no sense
    if (onFilterChange) {
      if (!_isEmpty(filterObj.substring)) {
        onFilterChange(filterObj);
      } else {
        onFilterChange(undefined); // This makes no sense to me.
      }
    }
  };

  const searchTermMaybeFilterFn = useMemo(() => {
    if (!quickSearchEnabled) return _stubTrue; // If quick search not enabled return true.

    const searchableColumns = (columns as LegacyColumn[]).reduce(
      (result: string[], col: LegacyColumn): string[] => {
        if (col.searchable !== false) {
          return result.concat(col.name as string);
        }

        return result;
      },
      [],
    );

    return (row: any) => {
      // Don't need to filter cell that has null or undefined value
      const rowSearchableColumns = searchableColumns.filter(
        (key) => !_isNil(_path(key, row)),
      );

      return rowSearchableColumns.reduce((prev, curr) => {
        const fCol = columns.find((column) => column.name === curr);
        const rowVal = _path(curr, row);

        const searchableFuncRes =
          fCol?.searchableFunc != null
            ? fCol
                .searchableFunc(rowVal)
                .toLowerCase()
                .includes(searchTerm?.toLowerCase())
            : rowVal
                .toString()
                .toLowerCase()
                .includes(searchTerm?.toLowerCase());

        return searchableFuncRes || prev;
      }, false);
    };
  }, [columns, quickSearchEnabled, searchTerm]);

  // Tag filter function
  const tagFilterFn = useCallback(
    (row: any) => {
      if (selectedTags?.length === 0) return true;

      if (!row?.tags || !Array.isArray(row?.tags)) return false;

      return selectedTags.some((selectedTag) => {
        return row?.tags?.some((tag: any) => {
          if (tag && tag.key && tag.value) {
            const tagString = `${tag?.key}:${tag?.value}`;
            return tagString === selectedTag;
          }
          return false;
        });
      });
    },
    [selectedTags],
  );

  const filteredItems = useMemo(() => {
    let filtered = data;

    // Apply search term filter
    filtered = filtered.filter(searchTermMaybeFilterFn);

    // Apply tag filter if enabled
    if (filterByTags) {
      filtered = filtered.filter(tagFilterFn);
    }

    // Apply column filter if present
    if (filterObj !== undefined) {
      const column = renderedColumns.find(
        (col) => col.id === filterObj.columnName,
      ) as RenderableColumn; // This will search on all columns regardless of the column being omitted
      if (filterObj.substring && filterObj.columnName) {
        try {
          const regexp = new RegExp(filterObj.substring, "i");
          const filterObjFilterFn = (row: any, rowIdx: number) => {
            let target;
            if (
              !_isNil(column?.type) &&
              (column.type === ColumnCustomType.JSON ||
                column.type === ColumnCustomType.DATE ||
                column.searchable === "calculated")
            ) {
              target = column.format(row, rowIdx);

              if (!_isString(target)) {
                target = JSON.stringify(target);
              }
            } else {
              target = column?.selector(row, rowIdx);
            }

            // Convert non-string values (including booleans) to strings for regex matching
            if (!_isString(target)) {
              target = String(target);
            }

            return regexp.test(target);
          };

          filtered = filtered.filter(filterObjFilterFn);
        } catch (e) {
          // Bad or incomplete Regexp
          logger.error(e);
          return [];
        }
      }
    }

    return filtered;
  }, [
    data,
    filterObj,
    searchTermMaybeFilterFn,
    renderedColumns,
    filterByTags,
    tagFilterFn,
  ]);

  return (
    <>
      {quickSearchEnabled && (
        <QuickSearchComponent
          quickSearchPlaceholder={quickSearchPlaceholder}
          searchTerm={searchTerm}
          onChange={handleSearchTermChange}
          createButton={createButton}
          description={description}
          autoFocusValue={autoFocus}
          searchModalContainerProps={searchModalContainerProps}
        />
      )}
      <Box
        id="data-table-container"
        sx={{
          // Looks like some things
          // can't be overridden via `customStyles`
          "& .rdt_TableCol_Sortable": {
            opacity: 0.6,
            transition: "opacity 0.2s",
            "&:hover": {
              opacity: 1,
              transition: "opacity 0.2s",
              color: mode === "light" ? colors.gray00 : colors.gray14,
            },
          },
          "& .rdt_TableHeader": {
            color: mode === "light" ? colors.gray00 : colors.gray14,
            flex: 0,
          },
          "& .rdt_TableRow": {
            minHeight: 0,
          },
          "& .rdt_TableHeadRow": {
            "& .rdt_TableCol": {
              "& .rdt_TableCol_Sortable": {
                overflow: "unset",
                "& .lnndaO": {
                  // override ellipsis
                  overflow: "unset",
                  whiteSpace: "unset",
                  textOverflow: "unset",
                },
              },
              "& input[type='checkbox']": {
                top: 2,
              },
            },
          },
          "& .rdt_TableHead": {
            background: props.fixedHeader ? headerdefaultbackcolor : "initial",
          },
          "& .rdt_TableCell input[type='checkbox']": {
            top: 2,
          },
        }}
        style={{
          height: "100%",
          minHeight: 0,
          maxHeight: "100%",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <RawDataTable
          customStyles={{ ...dataTableStyles, ...customStyles } as TableStyles}
          theme={mode === "light" ? "default" : "dark"}
          highlightOnHover={true}
          sortIcon={<SortIcon />}
          onRowMouseEnter={onRowMouseEnter}
          title={
            titleComponent ? (
              titleComponent
            ) : (
              <Box id="data-table-title" style={{ fontSize: "14px" }}>
                {title
                  ? title
                  : createTableTitle({ filteredData: filteredItems, data })}
              </Box>
            )
          }
          columns={columnsWithTooltips}
          // Sort strategy:
          // 1. updateTime 1st (desc)
          // 2. If updateTime isn't exist compare with createTime (desc)
          // 3. name (asc)
          data={
            sortByDefault
              ? defaultFilterItemsSorter(filteredItems)
              : filteredItems
          }
          onColumnOrderChange={(col) =>
            handleColumnOrderChange(col as LegacyColumn[], orderedColumns)
          }
          pagination={pagination}
          paginationServer={paginationServer}
          // The persistRowsPerPage variable will be used only if
          // the default paginationComponent is used
          paginationPerPage={
            paginationComponent || !useGlobalRowsPerPage
              ? paginationPerPage
              : persistRowsPerPage
          }
          paginationRowsPerPageOptions={[15, 30, 100]}
          noDataComponent={noDataComponent}
          paginationComponent={paginationComponent}
          progressComponent={
            <div
              style={{
                padding: "50px 0",
              }}
            >
              <CircularProgress id="data-table-circular-progress-component" />
            </div>
          }
          actions={
            <Grid
              container
              spacing={1}
              alignItems="center"
              justifyContent="flex-end"
              flex={[1, 1, "auto"]}
              sx={{
                mt: isValidWidth ? 1 : null,
                overflowY: "auto",
                opacity: 0.6,
                width: "100%",
              }}
            >
              {customActions &&
                customActions.length > 0 &&
                customActions.map((component, index) => (
                  <Grid key={index} size={isValidWidth ? 6 : undefined}>
                    {component}
                  </Grid>
                ))}

              {!paginationServer && !quickSearchEnabled && !hideSearch && (
                <Grid size={isValidWidth ? 6 : undefined}>
                  <Filter
                    columns={orderedColumns}
                    filterObj={filterObj}
                    setFilterObj={handleFilterObjectChange}
                  />
                </Grid>
              )}

              {filterByTags && (
                <Grid size={isValidWidth ? 6 : undefined}>
                  <TagFilter
                    data={data}
                    onTagFilterChange={setSelectedTags}
                    selectedTags={selectedTags}
                  />
                </Grid>
              )}

              {showColumnSelector && (
                <Grid size={isValidWidth ? 6 : undefined}>
                  <ColumnsSelector
                    columns={orderedColumns}
                    onColumnVisibilityChange={handleColumnOrderChange}
                    defaultShowColumns={defaultShowColumns}
                  />
                </Grid>
              )}
            </Grid>
          }
          onChangeRowsPerPage={handleChangeRowsPerPage}
          {...rest}
        />
      </Box>
    </>
  );
};
export default DataTable;
