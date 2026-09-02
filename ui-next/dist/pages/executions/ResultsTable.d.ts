import { ReactNode } from "react";
export interface ResultsTableProps {
    resultObj: any;
    error?: any;
    busy?: boolean;
    page: number;
    rowsPerPage: number;
    setPage: (page: number) => void;
    setSort: (id: string, direction: string) => void;
    setRowsPerPage?: (rowsPerPage: number) => void;
    showMore?: boolean;
    title?: ReactNode;
    refetchExecution: () => void;
    handleError?: (error: any) => void;
    handleClearError?: () => void;
    filterOn: boolean;
    handleReset: () => void;
}
export default function ResultsTable({ resultObj, error, busy, page, rowsPerPage, setPage, setSort, setRowsPerPage, title, refetchExecution, handleError, handleClearError, filterOn, handleReset, }: ResultsTableProps): import("react").JSX.Element;
