import React from 'react';
import PropTypes from 'prop-types';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip
} from 'recharts';
import { styles, CHART_COLORS } from '../errorsInspectorStyles';

const FailureReasonChart = ({
  data,
  selectedReason,
  onReasonClick,
  onResetFilter,
  isFilterActive
}) => {
  return (
    <div style={styles.card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
        <h2 
          style={{...styles.subtitle, cursor: 'help'}} 
          title="Groups similar errors using hierarchical clustering based on both error messages and failed task names. Errors are automatically clustered based on their similarity, making it easier to identify common failure patterns."
        >
          Error Patterns
        </h2>
        {isFilterActive && (
          <button
            onClick={onResetFilter}
            style={styles.filterButton}
            title="Reset reason filter"
          >
            <span>Reset Filter</span>
            <span style={{ fontSize: "14px" }}>×</span>
          </button>
        )}
      </div>
      
      {selectedReason && (
        <div style={styles.filterIndicator}>
          Filtered by reason: <strong>{selectedReason}</strong>
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
              nameKey="name"
              label={({name, percent}) => {
                // Truncate long reason names for the label
                const displayName = name.length > 20 ? name.substring(0, 17) + '...' : name;
                return `${displayName}: ${(percent * 100).toFixed(0)}%`;
              }}
              onClick={onReasonClick}
              cursor="pointer"
            >
              {data.map((entry, index) => (
                <Cell 
                  key={`cell-${index}`} 
                  fill={CHART_COLORS[index % CHART_COLORS.length]}
                  fillOpacity={entry.name === selectedReason ? 1 : 0.7}
                  stroke={entry.name === selectedReason ? "#000" : "none"}
                  strokeWidth={entry.name === selectedReason ? 2 : 0}
                />
              ))}
            </Pie>
            <Tooltip 
              content={({ active, payload }) => {
                if (active && payload && payload.length) {
                  const data = payload[0].payload;
                  return (
                    <div style={styles.tooltipContainer}>
                      <p style={styles.tooltipTitle}>{data.name}</p>
                      <p style={{ margin: 0 }}>Count: {data.value}</p>
                      
                      {/* Show original reasons if this is a fuzzy-matched group */}
                      {data.originalReasons && data.originalReasons.length > 1 && (
                        <div style={{ marginTop: '8px', fontSize: '12px' }}>
                          <p style={{ margin: '0 0 3px 0', fontWeight: 'bold' }}>Includes:</p>
                          <div style={styles.tooltipScrollContainer}>
                            {data.originalReasons.slice(0, 10).map((reason, idx) => (
                              <p key={idx} style={styles.tooltipItem}>
                                • {reason}
                              </p>
                            ))}
                            {data.originalReasons.length > 10 && (
                              <p style={{ margin: '2px 0', fontStyle: 'italic' }}>
                                ...and {data.originalReasons.length - 10} more
                              </p>
                            )}
                          </div>
                        </div>
                      )}
                      
                      <p style={styles.tooltipHint}>
                        Click to filter by this reason
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

FailureReasonChart.propTypes = {
  /** Array of failure reason data for the chart */
  data: PropTypes.arrayOf(PropTypes.shape({
    name: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired,
    originalReasons: PropTypes.arrayOf(PropTypes.string),
    percentage: PropTypes.string,
    subgroups: PropTypes.arrayOf(PropTypes.shape({
      name: PropTypes.string.isRequired,
      count: PropTypes.number.isRequired,
      reasons: PropTypes.arrayOf(PropTypes.string),
      normalizedReasons: PropTypes.arrayOf(PropTypes.string)
    }))
  })).isRequired,
  /** Currently selected failure reason */
  selectedReason: PropTypes.string,
  /** Callback when a reason is clicked */
  onReasonClick: PropTypes.func.isRequired,
  /** Callback to reset the reason filter */
  onResetFilter: PropTypes.func.isRequired,
  /** Whether the reason filter is active */
  isFilterActive: PropTypes.bool.isRequired
};

export default FailureReasonChart; 