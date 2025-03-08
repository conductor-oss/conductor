import React from 'react';
import { styles } from '../errorsInspectorStyles';
import PropTypes from 'prop-types';

const SummaryCard = ({ 
  title, 
  value, 
  totalValue, 
  color, 
  percentage, 
  noDataMessage,
  style = {}
}) => {
  const isEmpty = value === 0;
  
  return (
    <div style={{ ...styles.card, ...style }}>
      <h3 style={{ color: "#666", fontWeight: "500", marginBottom: "5px" }}>{title}</h3>
      {totalValue ? (
        <>
          <p style={styles.summaryCardValue(isEmpty)}>
            {value.toLocaleString()}
            <span style={{ fontSize: "14px", color: "#666", fontWeight: "normal", marginLeft: "8px" }}>
              of {totalValue.toLocaleString()}
            </span>
          </p>
          <p style={{ fontSize: "12px", color: "#666" }}>
            {totalValue > 0 ? `${((value / totalValue) * 100).toFixed(1)}% of total` : '0%'}
          </p>
          {isEmpty && noDataMessage && (
            <p style={styles.noDataMessage}>{noDataMessage}</p>
          )}
        </>
      ) : (
        <>
          <p style={{ 
            fontSize: "24px", 
            fontWeight: "bold", 
            color: color || "#666",
            margin: "5px 0"
          }}>
            {value.toLocaleString()}
          </p>
          {percentage !== undefined && (
            <p style={{ fontSize: "12px", color: "#666" }}>
              {percentage}
            </p>
          )}
          {isEmpty && noDataMessage && (
            <p style={styles.noDataMessage}>{noDataMessage}</p>
          )}
        </>
      )}
    </div>
  );
};

// Add prop types documentation
SummaryCard.propTypes = {
  /** Title of the card */
  title: PropTypes.string.isRequired,
  /** Main numeric value to display */
  value: PropTypes.number.isRequired,
  /** Optional total value for showing "X of Y" format */
  totalValue: PropTypes.number,
  /** Optional color for the value text */
  color: PropTypes.string,
  /** Optional percentage text to show below the value */
  percentage: PropTypes.string,
  /** Optional message to show when value is 0 */
  noDataMessage: PropTypes.string,
  /** Optional additional styles to merge with the base card style */
  style: PropTypes.object
};

export default SummaryCard; 