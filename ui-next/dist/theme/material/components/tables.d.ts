declare const tables: {
    MuiTablePagination: {
        styleOverrides: {
            select: {
                paddingRight: string;
            };
            selectRoot: {
                top: number;
            };
        };
    };
    MuiTableCell: {
        styleOverrides: {
            root: {
                fontSize: string;
            };
            head: {
                fontWeight: number;
                color: string;
            };
        };
    };
    MuiTableRow: {
        styleOverrides: {
            root: {
                "&.Mui-selected:hover": {
                    backgroundColor: string;
                };
                "&.Mui-selected": {
                    backgroundColor: string;
                };
            };
        };
    };
};
export default tables;
