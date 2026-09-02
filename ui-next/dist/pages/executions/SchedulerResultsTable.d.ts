export interface SchedulerResultsTableProps {
    resultObj: any;
    error: any;
    busy?: boolean;
    page: number;
    rowsPerPage: number;
    setPage: (page: number) => void;
    setSort: (id: string, direction: string) => void;
    setRowsPerPage?: (rowsPerPage: number) => void;
    refetchExecution: () => void;
    errorMessage: any;
    handleError: (error: any) => void;
    handleClearError: () => void;
    isFilterOn: boolean;
    handleReset: () => void;
}
export default function SchedulerResultsTable({ resultObj, error, busy, page, rowsPerPage, setPage, setSort, setRowsPerPage, refetchExecution, errorMessage, handleError, handleClearError, isFilterOn, handleReset, }: SchedulerResultsTableProps): import("react").JSX.Element;
