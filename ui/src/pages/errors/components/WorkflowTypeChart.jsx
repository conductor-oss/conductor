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

const WorkflowTypeChart = ({
  data,
  selectedWorkflowType,
  onWorkflowTypeClick,
  onResetFilter,
  workflowDefsError
}) => {
  return (
    <div style={styles.card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
        <h2 style={styles.subtitle}>Errors by Workflow Type</h2>
        {selectedWorkflowType && (
          <button
            onClick={onResetFilter}
            style={styles.filterButton}
            title="Reset workflow type filter"
          >
            <span>Reset Filter</span>
            <span style={{ fontSize: "14px" }}>×</span>
          </button>
        )}
      </div>
      
      {selectedWorkflowType && (
        <div style={styles.filterIndicator}>
          Filtered by: <strong>{selectedWorkflowType}</strong>
        </div>
      )}
      
      {workflowDefsError && (
        <div style={styles.errorIndicator}>
          Error loading workflow definitions. Chart may not reflect all available workflow types.
        </div>
      )}
      
      <div style={styles.chartContainer}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <Pie
              data={data}
              dataKey="count"
              nameKey="name"
              cx="50%"
              cy="50%"
              outerRadius={130}
              label={({name, percent}) => `${name}: ${(percent * 100).toFixed(0)}%`}
              onClick={onWorkflowTypeClick}
              cursor="pointer"
            >
              {data.map((entry, index) => (
                <Cell 
                  key={`cell-${index}`} 
                  fill={CHART_COLORS[index % CHART_COLORS.length]}
                  fillOpacity={entry.name === selectedWorkflowType ? 1 : 0.7}
                  stroke={entry.name === selectedWorkflowType ? "#000" : "none"}
                  strokeWidth={entry.name === selectedWorkflowType ? 2 : 0}
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
                      <p style={{ margin: 0 }}>Count: {data.count}</p>
                      <p style={{ margin: 0 }}>Average Time: {data.avgTime.toFixed(2)}ms</p>
                      <p style={styles.tooltipHint}>
                        Click to filter by this workflow type
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

WorkflowTypeChart.propTypes = {
  /** Array of workflow type data for the chart */
  data: PropTypes.arrayOf(PropTypes.shape({
    name: PropTypes.string.isRequired,
    count: PropTypes.number.isRequired,
    avgTime: PropTypes.number.isRequired
  })).isRequired,
  /** Currently selected workflow type */
  selectedWorkflowType: PropTypes.string,
  /** Callback when a workflow type is clicked */
  onWorkflowTypeClick: PropTypes.func.isRequired,
  /** Callback to reset the workflow type filter */
  onResetFilter: PropTypes.func.isRequired,
  /** Error loading workflow definitions */
  workflowDefsError: PropTypes.any
};

export default WorkflowTypeChart; 