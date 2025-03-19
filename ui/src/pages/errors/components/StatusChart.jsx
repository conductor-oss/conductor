import React from 'react';
import PropTypes from 'prop-types';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip
} from 'recharts';
import { styles, getStatusColor } from '../errorsInspectorStyles';

const StatusChart = ({
  data,
  selectedStatus,
  onStatusClick,
  onResetFilter,
  isFilterActive
}) => {
  return (
    <div style={styles.card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
        <h2 style={styles.subtitle}>Errors by Status</h2>
        {isFilterActive && (
          <button
            onClick={onResetFilter}
            style={styles.filterButton}
            title="Reset status filter"
          >
            <span>Reset Filter</span>
            <span style={{ fontSize: "14px" }}>×</span>
          </button>
        )}
      </div>
      
      {isFilterActive && (
        <div style={styles.filterIndicator}>
          Filtered by status: <strong>{selectedStatus}</strong>
        </div>
      )}
      
      <div style={styles.chartContainer}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              outerRadius={100}
              fill="#8884d8"
              dataKey="value"
              label={({name, percent}) => `${name}: ${(percent * 100).toFixed(0)}%`}
              onClick={(data) => {
                onStatusClick(data.name);
              }}
              style={{ cursor: 'pointer' }}
            >
              {data.map((entry, index) => (
                <Cell 
                  key={`cell-${index}`} 
                  fill={getStatusColor(entry.name)}
                  stroke={entry.name === selectedStatus ? "#000" : "none"}
                  strokeWidth={entry.name === selectedStatus ? 2 : 0}
                />
              ))}
            </Pie>
            <Tooltip 
              content={({ active, payload }) => {
                if (active && payload && payload.length) {
                  const data = payload[0].payload;
                  return (
                    <div style={styles.tooltipContainer}>
                      <p style={{ margin: 0 }}><strong>{data.name}</strong></p>
                      <p style={{ margin: 0 }}>Count: {data.value}</p>
                      <p style={styles.tooltipHint}>
                        Click to filter by this status
                      </p>
                    </div>
                  );
                }
                return null;
              }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

StatusChart.propTypes = {
  /** Array of status data for the chart */
  data: PropTypes.arrayOf(PropTypes.shape({
    name: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired
  })).isRequired,
  /** Currently selected status */
  selectedStatus: PropTypes.string,
  /** Callback when a status is clicked */
  onStatusClick: PropTypes.func.isRequired,
  /** Callback to reset the status filter */
  onResetFilter: PropTypes.func.isRequired,
  /** Whether the status filter is active */
  isFilterActive: PropTypes.bool.isRequired
};

export default StatusChart; 