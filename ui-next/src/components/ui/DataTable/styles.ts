import { createTheme } from "react-data-table-component";

createTheme("default", {
  background: {
    default: "transparent",
    text: {
      primary: "#777777",
      // secondary: "blue",
    },
    highlightOnHover: {
      text: "#000000",
      default: "rgba(128, 128, 128, .3)",
    },
  },
});

// createTheme creates a new theme named solarized that overrides the build in dark theme
createTheme("dark", {
  header: {
    style: {
      color: "#aaaaaa",
      fontSize: "14px",
    },
  },
  headRow: {
    style: {
      backgroundColor: "rgba(0,0,0,.03)",
      width: "100%",
    },
  },
  text: {
    primary: "#FFFFFF",
    secondary: "rgba(255, 255, 255, 0.7)",
    disabled: "rgba(0,0,0,.12)",
  },
  background: {
    default: "#111111",
  },
  context: {
    background: "var(--primaryDarker)",
    text: "#FFFFFF",
  },
  divider: {
    default: "rgba(81, 81, 81, .5)",
  },
  button: {
    default: "#FFFFFF",
    focus: "rgba(255, 255, 255, .54)",
    hover: "rgba(255, 255, 255, .12)",
    disabled: "rgba(255, 255, 255, .18)",
  },
  selected: {
    default: "rgba(0, 0, 0, .7)",
    text: "#FFFFFF",
  },
  highlightOnHover: {
    default: "rgba(128, 128, 128, .2)",
    text: "#FFFFFF",
  },
  striped: {
    default: "rgba(0, 0, 0, .87)",
    text: "#FFFFFF",
  },
});

export const dataTableStyles = {
  // Top title (e.g. "Page 1 of Many")
  header: {
    style: {
      color: "#111111",
      fontSize: "14px",
      flex: 0,
    },
  },
  headRow: {
    style: {
      backgroundColor: "rgba(0,0,0,.03)",
      width: "100%",
    },
  },
  subHeader: {
    style: {
      backgroundColor: "blue",
      color: "red",
    },
  },
  headCells: {
    style: {
      display: "flex",
      justifyContent: "start",
      alignItems: "center",
      textTransform: "uppercase",
      fontSize: "11px",
      fontWeight: 600,
      padding: "5px 16px",
    },
  },
  cells: {
    style: {
      padding: "10px 16px",
      alignItems: "center",
      fontWeight: 300,
    },
  },
  contextMenu: {
    style: {
      borderRadius: "8px 8px 0 0",
    },
  },
  pagination: {
    style: {
      flex: 0,
    },
  },
};
