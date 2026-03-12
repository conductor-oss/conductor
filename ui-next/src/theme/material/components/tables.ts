import { fontSizes, fontWeights, colors } from "../../tokens/variables";

const tables = {
  MuiTablePagination: {
    styleOverrides: {
      select: {
        paddingRight: "32px !important",
      },
      selectRoot: {
        top: 1,
      },
    },
  },
  MuiTableCell: {
    styleOverrides: {
      root: {
        fontSize: fontSizes.fontSize2,
      },
      head: {
        //border: 'none',
        fontWeight: fontWeights.fontWeight1,
        color: colors.gray05,
      },
    },
  },
  MuiTableRow: {
    styleOverrides: {
      root: {
        "&.Mui-selected:hover": {
          backgroundColor: colors.gray12,
        },
        "&.Mui-selected": {
          backgroundColor: `${colors.gray12} !important`,
        },
      },
    },
  },
};

export default tables;
