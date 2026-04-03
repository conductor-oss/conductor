import React from 'react';
import PropTypes from 'prop-types';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend
} from 'recharts';
import { styles, colors } from '../errorsInspectorStyles';

const TimeSeriesChart = ({
  data,
  selectedTimePeriod,
  onTimePeriodClick,
  onResetFilter,
  isFilterActive
}) => {
  return (
    <div style={styles.card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
        <h2 style={styles.subtitle}>Errors by Time Period</h2>
        {isFilterActive && (
          <button
            onClick={onResetFilter}
            style={styles.filterButton}
            title="Reset time period filter"
          >
            <span>Reset Filter</span>
            <span style={{ fontSize: "14px" }}>×</span>
          </button>
        )}
      </div>
      
      {selectedTimePeriod && (
        <div style={styles.filterIndicator}>
          Selected time range: <strong>{new Date(selectedTimePeriod).toLocaleString()}</strong>
          {data.length === 0 && " (expanded ±1 hour)"}
        </div>
      )}
      
      <div style={styles.chartContainer}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart
            data={data}
            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
            onClick={onTimePeriodClick}
          >
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis 
              dataKey="time" 
              type="number"
              domain={['auto', 'auto']}
              tickFormatter={(value) => new Date(value).toLocaleDateString()}
            />
            <YAxis />
            <Tooltip 
              labelFormatter={(value) => new Date(value).toLocaleString()}
              formatter={(value, name) => {
                if (name === "count") return [value, "Count"];
                return [value, name];
              }}
              content={({ active, payload }) => {
                if (active && payload && payload.length) {
                  return (
                    <div style={styles.tooltipContainer}>
                      <p style={{ margin: 0 }}><strong>{new Date(payload[0].payload.time).toLocaleString()}</strong></p>
                      <p style={{ margin: 0 }}>Count: {payload[0].payload.count}</p>
                      <p style={styles.tooltipHint}>
                        Click to filter by this time period
                      </p>
                    </div>
                  );
                }
                return null;
              }}
            />
            <Legend />
            <Line 
              type="monotone" 
              dataKey="count" 
              stroke={colors.blue} 
              name="Workflow Count" 
              activeDot={{ 
                r: 8,
                stroke: (entry) => entry.time === selectedTimePeriod ? "#000" : colors.blue,
                strokeWidth: (entry) => entry.time === selectedTimePeriod ? 2 : 0,
                fill: (entry) => entry.time === selectedTimePeriod ? "#fff" : colors.blue
              }}
              dot={{ 
                r: 4,
                stroke: (entry) => entry.time === selectedTimePeriod ? "#000" : "none",
                strokeWidth: (entry) => entry.time === selectedTimePeriod ? 2 : 0
              }}
              style={{ cursor: 'pointer' }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

TimeSeriesChart.propTypes = {
  /** Array of time series data for the chart */
  data: PropTypes.arrayOf(PropTypes.shape({
    time: PropTypes.number.isRequired,
    count: PropTypes.number.isRequired
  })).isRequired,
  /** Currently selected time period */
  selectedTimePeriod: PropTypes.number,
  /** Callback when a time period is clicked */
  onTimePeriodClick: PropTypes.func.isRequired,
  /** Callback to reset the time period filter */
  onResetFilter: PropTypes.func.isRequired,
  /** Whether the time period filter is active */
  isFilterActive: PropTypes.bool.isRequired
};

export default TimeSeriesChart; 