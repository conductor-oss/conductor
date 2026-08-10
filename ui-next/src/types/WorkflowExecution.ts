import { QueryDispatch, SetStateAction } from "react-router-use-location-state";
import { TaskExecutionResult } from "./TaskExecution";

export type QueryFTType = {
  query: string;
  freeText: string;
};

export type ResultObjType = TaskExecutionResult;

export interface DoSearchProps {
  resultObj: ResultObjType;
  queryFT: QueryFTType;
  buildQuery: (defaultStartTime?: string) => QueryFTType;
  setQueryFT: (value: QueryFTType) => void;
  refetch: () => void;
  setPage: QueryDispatch<SetStateAction<number>>;
  setRecentTaskSearch?: () => void;
}
