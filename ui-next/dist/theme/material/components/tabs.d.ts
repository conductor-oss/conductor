import { PaletteMode } from "@mui/material";
declare const tabs: (mode: PaletteMode) => {
    MuiTabs: {
        styleOverrides: {
            indicator: {
                height: number;
            };
            scroller: {
                backgroundColor: string;
            };
        };
    };
    MuiTab: {
        defaultProps: {
            disableRipple: boolean;
        };
        styleOverrides: {
            root: {
                textTransform: string;
                color: string | undefined;
                "&.Mui-selected": {
                    color: string;
                };
                fontSize: string;
            };
        };
    };
};
export default tabs;
