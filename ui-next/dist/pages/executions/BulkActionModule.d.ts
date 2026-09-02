import React from "react";
export default function BulkActionModule({ selectedRows, refetchExecution, handleError, }: {
    selectedRows: any[];
    refetchExecution: () => void;
    handleError: (error: any) => void;
}): React.JSX.Element;
