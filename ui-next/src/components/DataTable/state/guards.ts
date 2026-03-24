import { DoneInvokeEvent } from "xstate";
import { DataTableMachineContext, SerializableColumn } from "./types";
import { getColumnId } from "../helpers";

import _isNil from "lodash/isNil";

export const noLocalStorageKey = (context: DataTableMachineContext) =>
  _isNil(context.localStorageKey);

export const isLocalStorageContentTrusted = (
  { columnOrderAndVisibility }: DataTableMachineContext,
  { data }: DoneInvokeEvent<SerializableColumn[]>,
) => {
  const existingColumns: string[] = columnOrderAndVisibility.map(getColumnId);
  return data.every((a) => !_isNil(a) && existingColumns.includes(a?.id));
};
