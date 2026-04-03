// Define color palette
export const colors = {
  gray14: '#eeeeee',
  blue: '#1f83db',
  green: '#41b957',
  yellow: '#ffc658',
  red: '#e50914',
  purple: '#8a2be2',
  teal: '#00ced1',
};

// Chart colors
export const CHART_COLORS = [
  '#8884d8', '#83a6ed', '#8dd1e1', '#82ca9d', '#a4de6c', 
  '#d0ed57', '#ffc658', '#ff8042', '#ff6361', '#bc5090',
  '#58508d', '#003f5c', '#7a5195', '#ef5675', '#ffa600'
];

// Time range options
export const TIME_RANGE_OPTIONS = [
  { label: '5 Minutes', value: '5m', milliseconds: 5 * 60 * 1000 },
  { label: '30 Minutes', value: '30m', milliseconds: 30 * 60 * 1000 },
  { label: '60 Minutes', value: '60m', milliseconds: 60 * 60 * 1000 },
  { label: '3 Hours', value: '3h', milliseconds: 3 * 60 * 60 * 1000 },
  { label: '6 Hours', value: '6h', milliseconds: 6 * 60 * 60 * 1000 },
  { label: '12 Hours', value: '12h', milliseconds: 12 * 60 * 60 * 1000 },
  { label: '24 Hours', value: '24h', milliseconds: 24 * 60 * 60 * 1000 },
  { label: '3 Days', value: '3d', milliseconds: 3 * 24 * 60 * 60 * 1000 },
  { label: '7 Days', value: '7d', milliseconds: 7 * 24 * 60 * 60 * 1000 },
  { label: '1 Month', value: '1M', milliseconds: 30 * 24 * 60 * 60 * 1000 },
  { label: '3 Months', value: '3M', milliseconds: 90 * 24 * 60 * 60 * 1000 }
];

// Component styles
export const styles = {
  wrapper: {
    height: "100%",
    overflow: "hidden",
    display: "flex",
    flexDirection: "row",
    position: "relative",
  },
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
    backgroundColor: colors.blue,
    color: "white",
    padding: "8px 16px",
    borderRadius: "4px",
    border: "none",
    cursor: "pointer",
  },
  toolbar: {
    backgroundColor: colors.gray14,
    padding: "10px",
    borderRadius: "4px",
    marginBottom: "15px",
  },
  workflowName: {
    fontWeight: "bold",
  },
  main: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    padding: "20px",
    overflowY: "auto",
    height: "100%",
    maxHeight: "100vh"
  },
  row: {
    display: "flex",
    flexDirection: "row",
    marginBottom: "20px",
    gap: "20px",
  },
  fields: {
    margin: "30px 0",
    flex: 1,
    display: "flex",
    flexDirection: "column",
    gap: "15px",
  },
  runInfo: {
    marginLeft: "-350px",
  },
  card: {
    backgroundColor: "white",
    padding: "20px",
    borderRadius: "6px",
    boxShadow: "0 2px 10px rgba(0, 0, 0, 0.1)",
  },
  chartContainer: {
    height: "300px",
    width: "100%",
  },
  title: {
    fontSize: "20px",
    fontWeight: "bold",
    marginBottom: "15px",
  },
  subtitle: {
    fontSize: "16px",
    fontWeight: "bold",
    marginBottom: "10px",
  },
  gridContainer: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fill, minmax(48%, 1fr))",
    gap: "20px",
    width: "100%",
  },
  table: {
    width: "100%",
    borderCollapse: "collapse",
  },
  tableHeader: {
    backgroundColor: colors.gray14,
    padding: "10px",
    textAlign: "left",
    borderBottom: "1px solid #ddd",
  },
  tableCell: {
    padding: "10px",
    borderBottom: "1px solid #ddd",
  },
  textArea: {
    width: "100%",
    height: "120px",
    padding: "10px",
    borderRadius: "4px",
    border: "1px solid #ddd",
  },
  statusTag: {
    padding: "4px 8px",
    borderRadius: "4px",
    fontSize: "12px",
    fontWeight: "bold",
  },
  statusCompleted: {
    backgroundColor: "#e6f7e6",
    color: "#2e7d32",
  },
  statusFailed: {
    backgroundColor: "#ffebee",
    color: colors.red,
  },
  statusTerminated: {
    backgroundColor: "#f3e5f5",
    color: colors.purple,
  },
  statusInProgress: {
    backgroundColor: "#e3f2fd",
    color: colors.blue,
  },
  select: {
    padding: "8px",
    borderRadius: "4px",
    border: "1px solid #ddd",
    width: "100%",
  },
  filterContainer: {
    display: "flex",
    gap: "20px",
    marginBottom: "20px",
  },
  filterItem: {
    flex: 1,
  },
  summaryCardContainer: {
    display: "grid",
    gridTemplateColumns: "repeat(5, 1fr)",
    gap: "16px",
    marginBottom: "20px",
    width: "100%"
  },
  // Additional styles for dynamic elements
  headerContainer: {
    display: "flex", 
    justifyContent: "space-between", 
    alignItems: "center", 
    marginBottom: "15px",
    position: "relative"
  },
  timeRangeSelect: {
    padding: "6px 10px",
    paddingLeft: "30px",
    paddingRight: "20px",
    borderRadius: "4px",
    border: "1px solid #ddd",
    backgroundColor: "#f0f7ff",
    color: colors.blue,
    fontWeight: "normal",
    appearance: "none",
    WebkitAppearance: "none",
    MozAppearance: "none",
    cursor: "pointer"
  },
  timeRangeIconContainer: {
    position: "absolute", 
    left: "8px", 
    top: "50%",
    transform: "translateY(-50%)",
    fontSize: "18px", 
    color: colors.blue,
    pointerEvents: "none"
  },
  dropdownArrow: {
    position: "absolute", 
    right: "8px", 
    top: "50%",
    transform: "translateY(-50%)",
    pointerEvents: "none",
    color: colors.blue,
    fontSize: "12px"
  },
  liveTailButton: (autoRefreshEnabled, isLoading) => ({
    backgroundColor: autoRefreshEnabled ? colors.blue : "transparent",
    color: autoRefreshEnabled ? "white" : "#666",
    border: "1px solid " + (autoRefreshEnabled ? colors.blue : "#ddd"),
    borderRadius: "4px",
    padding: "6px 12px",
    cursor: isLoading ? "not-allowed" : "pointer",
    display: "flex",
    alignItems: "center",
    gap: "6px",
    fontSize: "14px",
    fontWeight: "500",
    opacity: isLoading ? 0.7 : 1
  }),
  refreshProgress: (timeUntilRefresh) => ({
    width: `${(timeUntilRefresh / 60) * 100}%`
  }),
  summaryCard: (filteredData) => ({
    backgroundColor: filteredData && filteredData.length === 0 ? "#fff8e1" : "white",
    borderLeft: filteredData && filteredData.length === 0 ? "4px solid #ffc107" : "none",
    animation: filteredData && filteredData.length === 0 ? "flash 1s ease-in-out" : "none",
    padding: "16px",
    borderRadius: "6px",
    boxShadow: "0 2px 10px rgba(0, 0, 0, 0.1)",
  }),
  summaryCardValue: (isEmpty) => ({
    fontSize: "24px", 
    fontWeight: "bold",
    color: isEmpty ? "#f57c00" : "inherit"
  }),
  noDataMessage: {
    fontSize: "14px", 
    color: "#f57c00", 
    fontWeight: "500",
    marginTop: "8px"
  },
  filterButton: {
    backgroundColor: colors.blue,
    color: "white",
    border: "none",
    borderRadius: "4px",
    padding: "4px 8px",
    fontSize: "12px",
    cursor: "pointer",
    display: "flex",
    alignItems: "center",
    gap: "5px"
  },
  filterIndicator: {
    marginBottom: "10px", 
    padding: "6px 10px", 
    backgroundColor: "#f0f7ff", 
    borderRadius: "4px",
    fontSize: "14px"
  },
  errorIndicator: {
    marginBottom: "10px", 
    padding: "6px 10px", 
    backgroundColor: "#ffebee", 
    borderRadius: "4px",
    fontSize: "14px",
    color: colors.red
  },
  noResultsContainer: {
    padding: '40px', 
    textAlign: 'center', 
    color: '#666',
    backgroundColor: '#f9f9f9',
    borderRadius: '4px'
  },
  tooltipContainer: {
    backgroundColor: '#fff', 
    padding: '10px', 
    border: '1px solid #ccc',
    borderRadius: '4px',
    maxWidth: '300px'
  },
  tooltipTitle: {
    margin: 0, 
    fontWeight: 'bold', 
    marginBottom: '5px'
  },
  tooltipScrollContainer: {
    maxHeight: '100px', 
    overflowY: 'auto', 
    padding: '3px',
    border: '1px solid #eee',
    borderRadius: '3px'
  },
  tooltipItem: {
    margin: '2px 0', 
    wordBreak: 'break-word'
  },
  tooltipHint: {
    margin: 0, 
    fontSize: '11px', 
    color: '#666', 
    marginTop: '5px'
  }
};

// CSS animations and special styles
export const cssStyles = `
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
  .spinner {
    display: inline-block;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 4px solid #ccc;
    border-top-color: #1f83db;
    animation: spin 1s linear infinite;
  }
  .refresh-progress {
    height: 2px;
    background-color: ${colors.blue};
    transition: width 1s linear;
    position: absolute;
    bottom: 0;
    left: 0;
  }
  @keyframes slideIn {
    from { transform: translateY(-100%); opacity: 0; }
    to { transform: translateY(0); opacity: 1; }
  }
  @keyframes slideOut {
    from { transform: translateY(0); opacity: 1; }
    to { transform: translateY(-100%); opacity: 0; }
  }
  .notification {
    position: fixed;
    top: 20px;
    right: 20px;
    padding: 12px 20px;
    border-radius: 4px;
    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
    z-index: 1000;
    animation: slideIn 0.3s ease forwards;
  }
  .notification.success {
    background-color: #e6f7e6;
    color: #2e7d32;
    border-left: 4px solid #2e7d32;
  }
  .notification.error {
    background-color: #ffebee;
    color: ${colors.red};
    border-left: 4px solid ${colors.red};
  }
  .notification.exiting {
    animation: slideOut 0.3s ease forwards;
  }
`;

// Helper functions
export const getHashCode = (str) => {
  let hash = 0;
  if (!str || str.length === 0) return hash;
  
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
};

export const getStatusColor = (status) => {
  if (status === 'FAILED') return colors.red;
  if (status === 'TERMINATED') return colors.purple;
  if (status === 'COMPLETED') return colors.green;
  if (status === 'RUNNING') return colors.blue;
  return CHART_COLORS[Math.abs(getHashCode(status) || status.length) % CHART_COLORS.length];
}; 