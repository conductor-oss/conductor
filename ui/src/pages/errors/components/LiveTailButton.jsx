import React from 'react';
import PropTypes from 'prop-types';
import { Tooltip as MUITooltip } from '@material-ui/core';
import PlayArrowIcon from '@material-ui/icons/PlayArrow';
import PauseIcon from '@material-ui/icons/Pause';
import { styles } from '../errorsInspectorStyles';

const LiveTailButton = ({
  isEnabled,
  isLoading,
  timeUntilRefresh,
  onToggle,
  showProgressBar = true
}) => {
  return (
    <>
      <MUITooltip title={isEnabled ? "Stop live tailing" : "Start live tailing"}>
        <div style={{ 
          display: "flex", 
          alignItems: "center", 
          marginRight: "10px"
        }}>
          <button 
            onClick={onToggle}
            disabled={isLoading}
            style={styles.liveTailButton(isEnabled, isLoading)}
            aria-label={isEnabled ? "Stop live tailing" : "Start live tailing"}
          >
            {isEnabled ? (
              <>
                <PauseIcon style={{ fontSize: "18px" }} />
                <span>live tail</span>
                {!isLoading && <span>({timeUntilRefresh}s)</span>}
              </>
            ) : (
              <>
                <PlayArrowIcon style={{ fontSize: "18px" }} />
                <span>live tail</span>
              </>
            )}
            {isLoading && (
              <span 
                className="spinner" 
                style={{ 
                  marginLeft: "5px", 
                  borderColor: isEnabled ? "white" : "#666", 
                  borderTopColor: "transparent" 
                }}
              />
            )}
          </button>
        </div>
      </MUITooltip>
      
      {/* Progress bar for auto-refresh */}
      {showProgressBar && isEnabled && !isLoading && (
        <div 
          className="refresh-progress" 
          style={styles.refreshProgress(timeUntilRefresh)}
          aria-hidden="true"
        />
      )}
    </>
  );
};

LiveTailButton.propTypes = {
  /** Whether live tailing is currently enabled */
  isEnabled: PropTypes.bool.isRequired,
  /** Whether data is currently being loaded */
  isLoading: PropTypes.bool.isRequired,
  /** Time in seconds until next refresh */
  timeUntilRefresh: PropTypes.number.isRequired,
  /** Callback when button is clicked */
  onToggle: PropTypes.func.isRequired,
  /** Whether to show the progress bar */
  showProgressBar: PropTypes.bool
};

export default LiveTailButton; 