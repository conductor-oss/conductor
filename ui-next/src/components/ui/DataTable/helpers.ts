import fastDeepEqual from "fast-deep-equal";
import _get from "lodash/get";
import { TableColumn } from "react-data-table-component";
import { timestampRenderer } from "utils/index";
import { FilterObjectItem } from "./state/types";
import {
  ColumnCustomType,
  Format,
  LegacyColumn,
  RenderableColumn,
} from "./types";

type ColumnWithLabel = TableColumn<any> & { label?: string };

export const getColumnLabelById = (
  columnId: string,
  columns: ColumnWithLabel[],
) => {
  const col = columns.find(
    (c: ColumnWithLabel) => c.id === columnId || c.name === columnId,
  );
  return col?.label || col?.name;
};
export const getColumnLabel = (col: ColumnWithLabel) =>
  (col?.label || col.name!) as string;

export const getColumnId = (col: TableColumn<any>): string => {
  return col.id as string;
};

const compareString = (preString: string, curString: string) =>
  preString.toLowerCase().localeCompare(curString.toLowerCase());

export const defaultFilterItemsSorter = (filteredItems: any[]) =>
  filteredItems?.sort((preValue, curValue) => {
    if (preValue.updateTime) {
      if (curValue.updateTime) {
        return curValue.updateTime - preValue.updateTime;
      }

      if (curValue.createTime) {
        return curValue.createTime - preValue.updateTime;
      }
    } else if (preValue.createTime) {
      if (curValue.updateTime) {
        return curValue.updateTime - preValue.createTime;
      }

      if (curValue.createTime) {
        return curValue.createTime - preValue.createTime;
      }
    } else if (preValue.size && curValue.size) {
      return curValue.size - preValue.size;
    }
    // Compare name
    else if (preValue.name && curValue.name) {
      return compareString(preValue.name, curValue.name);
    }
    // Compare id (for group)
    else if (preValue.id && curValue.id) {
      return compareString(preValue.id, curValue.id);
    }

    return 0;
  });

export const formatForColumn = (column: LegacyColumn): Format<any> => {
  if (column?.type === ColumnCustomType.DATE) {
    return (row: any) => timestampRenderer(_get(row, column.name as string));
  } else if (column?.type === ColumnCustomType.JSON) {
    return (row: any) => JSON.stringify(_get(row, column.name as string));
  }

  if (column?.renderer) {
    return (row: any) =>
      column.renderer!(_get(row, column.name as string), row);
  }
  return (row: any) => _get(row, column.name as string);
};

export const createDefaultFilterObject = (
  renderedColumns: RenderableColumn[],
): FilterObjectItem | undefined => {
  const maybeColumnName = renderedColumns.find(
    (col: LegacyColumn) => col.searchable !== false,
  )?.id;
  if (maybeColumnName) {
    return {
      columnName: maybeColumnName,
      substring: "",
    };
  }
};

export const getNestedValue = <T>(obj: T, path: string): unknown => {
  return path.split(".").reduce((acc: any, part) => acc && acc[part], obj);
};

export const dynamicSort = <T>({
  objA,
  objB,
  propertyPath,
}: {
  objA: T;
  objB: T;
  propertyPath: string;
}): number => {
  const valueA = getNestedValue(objA, propertyPath);
  const valueB = getNestedValue(objB, propertyPath);

  if (
    valueA === undefined ||
    valueA === null ||
    valueB === undefined ||
    valueB === null
  ) {
    if (valueA === valueB) {
      return 0;
    }

    return valueA === undefined || valueA === null ? 1 : -1;
  }

  if (typeof valueA === "string" && typeof valueB === "string") {
    return valueA.toLowerCase().localeCompare(valueB.toLowerCase());
  }

  if (typeof valueA === "number" && typeof valueB === "number") {
    return valueA - valueB;
  }

  if (typeof valueA === "boolean" && typeof valueB === "boolean") {
    return valueA === valueB ? 0 : valueA ? -1 : 1;
  }

  if (Array.isArray(valueA) && Array.isArray(valueB)) {
    return Math.sign(valueA.length - valueB.length);
  }

  if (typeof valueA === "object" && typeof valueB === "object") {
    const result = fastDeepEqual(valueA, valueB);

    return result ? 0 : 1;
  }

  return valueA.toString().localeCompare(valueB.toString());
};
