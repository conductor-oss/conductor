import React from 'react';
import PropTypes from 'prop-types';
import AccessTimeIcon from '@material-ui/icons/AccessTime';
import { styles } from '../errorsInspectorStyles';

const TimeRangeDropdown = ({
  selectedTimeRange,
  timeRangeOptions,
  onTimeRangeChange,
  onFilterChange
}) => {
  const handleChange = (e) => {
    const newTimeRange = e.target.value;
    onTimeRangeChange(newTimeRange);
    onFilterChange(newTimeRange);
  };

  return (
    <div style={{ 
      position: "relative",
      fontSize: "14px", 
      color: "#666",
      marginRight: "10px"
    }}>
      <select 
        id="timeRange"
        value={selectedTimeRange}
        onChange={handleChange}
        style={styles.timeRangeSelect}
        aria-label="Select time range"
      >
        {timeRangeOptions.map(option => (
          <option key={option.value} value={option.value}>
            Since {option.label}
          </option>
        ))}
      </select>
      <AccessTimeIcon style={styles.timeRangeIconContainer} />
      <div style={styles.dropdownArrow}>▼</div>
    </div>
  );
};

TimeRangeDropdown.propTypes = {
  /** Currently selected time range value */
  selectedTimeRange: PropTypes.string.isRequired,
  /** Array of time range options */
  timeRangeOptions: PropTypes.arrayOf(PropTypes.shape({
    value: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    milliseconds: PropTypes.number.isRequired
  })).isRequired,
  /** Callback when time range changes */
  onTimeRangeChange: PropTypes.func.isRequired,
  /** Callback to handle filter changes */
  onFilterChange: PropTypes.func.isRequired
};

export default TimeRangeDropdown; 