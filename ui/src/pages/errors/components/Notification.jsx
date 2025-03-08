import React from 'react';
import PropTypes from 'prop-types';
import { colors } from '../errorsInspectorStyles';

const Notification = ({ 
  message, 
  type, 
  timestamp, 
  onClose 
}) => {
  return (
    <div 
      className={`notification ${type}`}
      role="alert"
      aria-live="polite"
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <span>{message}</span>
        <button 
          onClick={onClose}
          style={{
            background: "none",
            border: "none",
            cursor: "pointer",
            marginLeft: "10px",
            fontSize: "16px",
            color: type === "success" ? "#2e7d32" : colors.red,
            padding: "0"
          }}
          aria-label="Close notification"
        >
          ×
        </button>
      </div>
      <div style={{ fontSize: "12px", marginTop: "5px", opacity: 0.8 }}>
        {timestamp.toLocaleTimeString()}
      </div>
    </div>
  );
};

Notification.propTypes = {
  message: PropTypes.string.isRequired,
  type: PropTypes.oneOf(['success', 'error']).isRequired,
  timestamp: PropTypes.instanceOf(Date).isRequired,
  onClose: PropTypes.func.isRequired
};

export default Notification; 