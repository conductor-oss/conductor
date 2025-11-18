import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';

import _ from 'lodash';
import { useWorkflowSearch } from '../../data/workflow';
import { useWorkflowDefs } from '../../data/workflow';
import ResultsTable from '../executions/ResultsTable';
import SummaryCard from './components/SummaryCard';
import WorkflowTypeChart from './components/WorkflowTypeChart';
import StatusChart from './components/StatusChart';
import FailureReasonChart from './components/FailureReasonChart';
import TimeSeriesChart from './components/TimeSeriesChart';
import TimeRangeDropdown from './components/TimeRangeDropdown';
import LiveTailButton from './components/LiveTailButton';
import { colors, TIME_RANGE_OPTIONS, styles, cssStyles } from './errorsInspectorStyles';
import useWorkflowErrorGroups, { filterWorkflowsByReason } from './hooks/useWorkflowErrorGroups';
import Notification from './components/Notification';

const ErrorsInspector = () => {
  const [data, setData] = useState(null);
  const [filteredData, setFilteredData] = useState(null);
  const [workflowTypeFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [isLoading, setIsLoading] = useState(true);
  const [metrics, setMetrics] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [rowsPerPage, setRowsPerPage] = useState(15);
  const [selectedWorkflowDefs, setSelectedWorkflowDefs] = useState([]);
  const [sortField, setSortField] = useState("startTime");
  const [sortDirection, setSortDirection] = useState("desc");
  const [statusFilterFromChart, setStatusFilterFromChart] = useState(false);
  const [selectedWorkflowType, setSelectedWorkflowType] = useState(null);
  const [selectedTimePeriod, setSelectedTimePeriod] = useState(null);
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(false);
  const [timeUntilRefresh, setTimeUntilRefresh] = useState(60);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [notification, setNotification] = useState(null);
  const [selectedTimeRange, setSelectedTimeRange] = useState('24h'); // Default to 24 hours
  const [selectedReasonForIncompletion, setSelectedReasonForIncompletion] = useState(null);
  const [reasonFilterFromChart, setReasonFilterFromChart] = useState(false);
  
  // Reference to store the interval ID for cleanup
  const refreshIntervalRef = useRef(null);

  const { defs: workflowDefs, error: workflowDefsError } = useWorkflowDefs();
  
  // Fallback for workflow definitions if the hook isn't working
  const [fallbackDefs, setFallbackDefs] = useState([]);

  // Define calculateMetrics function before it's used in useEffect
  const calculateMetrics = useCallback((workflows) => {
    if (!workflows || workflows.length === 0) {
      return {
        workflowTypes: {},
        statusCounts: {},
        executionTimeByType: {},
        correlationIds: {},
        patientIds: {},
        totalExecutionTime: 0,
        totalWorkflows: 0,
        completedWorkflows: 0,
        failedWorkflows: 0,
        runningWorkflows: 0,
        pausedWorkflows: 0,
        terminatedWorkflows: 0,
        timedOutWorkflows: 0,
        reasonsForIncompletion: {},
        avgExecutionTime: 0
      };
    }

    const metrics = {
      workflowTypes: {},
      statusCounts: {},
      executionTimeByType: {},
      correlationIds: {},
      reasonsForIncompletion: {},
      totalExecutionTime: 0,
      totalWorkflows: workflows.length,
      completedWorkflows: 0,
      failedWorkflows: 0,
      runningWorkflows: 0,
      pausedWorkflows: 0,
      terminatedWorkflows: 0,
      timedOutWorkflows: 0,
      avgExecutionTime: 0
    };
    
    workflows.forEach(workflow => {
      // Count by workflow type
      metrics.workflowTypes[workflow.workflowType] = 
        (metrics.workflowTypes[workflow.workflowType] || 0) + 1;
      
      // Count by status
      metrics.statusCounts[workflow.status] = 
        (metrics.statusCounts[workflow.status] || 0) + 1;
      
      // Track completed vs failed vs running vs terminated vs timed out
      if (workflow.status === "COMPLETED") {
        metrics.completedWorkflows++;
      } else if (workflow.status === "FAILED") {
        metrics.failedWorkflows++;
      } else if (workflow.status === "RUNNING") {
        metrics.runningWorkflows++;
      } else if (workflow.status === "PAUSED") {
        metrics.pausedWorkflows++;  
      } else if (workflow.status === "TERMINATED") {
        metrics.terminatedWorkflows++;
      } else if (workflow.status === "TIMED_OUT") {
        metrics.timedOutWorkflows++;
      }
      
      // Track reasons for incompletion
      if (workflow.reasonForIncompletion) {
        metrics.reasonsForIncompletion[workflow.reasonForIncompletion] = 
          (metrics.reasonsForIncompletion[workflow.reasonForIncompletion] || 0) + 1;
      }
      
      // Sum execution times by type
      if (!metrics.executionTimeByType[workflow.workflowType]) {
        metrics.executionTimeByType[workflow.workflowType] = {
          count: 0,
          totalTime: 0,
          avgTime: 0,
          minTime: Infinity,
          maxTime: 0
        };
      }
      
      const typeStats = metrics.executionTimeByType[workflow.workflowType];
      typeStats.count++;
      typeStats.totalTime += workflow.executionTime;
      typeStats.minTime = Math.min(typeStats.minTime, workflow.executionTime);
      typeStats.maxTime = Math.max(typeStats.maxTime, workflow.executionTime);
      typeStats.avgTime = typeStats.totalTime / typeStats.count;
      
      // Track total execution time
      metrics.totalExecutionTime += workflow.executionTime;
      
      // Count by correlation ID
      if (workflow.correlationId) {
        metrics.correlationIds[workflow.correlationId] = 
          (metrics.correlationIds[workflow.correlationId] || 0) + 1;
      }
    });
    
    // Calculate average execution time
    metrics.avgExecutionTime = metrics.totalExecutionTime / metrics.totalWorkflows;
    
    return metrics;
  }, []);

  useEffect(() => {
    // If workflowDefs is not an array or is empty, try to fetch directly
    if (!Array.isArray(workflowDefs) || workflowDefs.length === 0) {
      console.log("Fetching workflow definitions directly as fallback");
      
      fetch('/api/metadata/workflow')
        .then(response => {
          if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
          }
          return response.json();
        })
        .then(data => {
          console.log("Fallback workflow definitions:", data);
          if (Array.isArray(data)) {
            setFallbackDefs(data);
          }
        })
        .catch(error => {
          console.error("Error fetching fallback workflow definitions:", error);
        });
    }
  }, [workflowDefs]);
  
  // Use fallback definitions if the hook didn't provide valid data
  const effectiveWorkflowDefs = Array.isArray(workflowDefs) && workflowDefs.length > 0 
    ? workflowDefs 
    : fallbackDefs;

  // Debug workflow definitions
  useEffect(() => {
    console.log("Workflow definitions:", workflowDefs);
  }, [workflowDefs]);

  // Create a unique list of workflow definition names from effectiveWorkflowDefs
  const uniqueWorkflowDefs = useMemo(() => {
    if (!Array.isArray(effectiveWorkflowDefs)) return [];
    
    // Use a Set to get unique workflow names
    const uniqueNames = new Set();
    const uniqueDefs = [];
    
    effectiveWorkflowDefs.forEach(def => {
      if (!uniqueNames.has(def.name)) {
        uniqueNames.add(def.name);
        uniqueDefs.push(def);
      }
    });
    
    return uniqueDefs;
  }, [effectiveWorkflowDefs]);

  // Initialize selectedWorkflowDefs with all unique workflow definitions when they load
  useEffect(() => {
    if (uniqueWorkflowDefs.length > 0 && selectedWorkflowDefs.length === 0) {
      console.log("Setting initial workflow defs:", uniqueWorkflowDefs);
      setSelectedWorkflowDefs(uniqueWorkflowDefs.map(def => def.name));
    }
  }, [uniqueWorkflowDefs, selectedWorkflowDefs.length]);

  // Handle workflow definitions fetch error
  useEffect(() => {
    if (workflowDefsError) {
      console.error('Error fetching workflow definitions:', workflowDefsError);
      setNotification({
        message: "Failed to load workflow definitions. Some filtering options may be unavailable.",
        type: "error",
        timestamp: new Date()
      });
      
      // Clear notification after 5 seconds
      const timer = setTimeout(() => {
        setNotification(null);
      }, 5000);
      
      return () => clearTimeout(timer);
    }
  }, [workflowDefsError]);

  // Function to build the query string based on selected time range and workflow types
  const buildQueryString = useCallback(() => {
    let queryParts = [];
    
    // Always add time range filter since 'all' option is removed
    const selectedOption = TIME_RANGE_OPTIONS.find(option => option.value === selectedTimeRange);
    if (selectedOption && selectedOption.milliseconds) {
      const cutoffTime = new Date(Date.now() - selectedOption.milliseconds).getTime();
      queryParts.push(`startTime>${cutoffTime}`);
    }
    
    // Add workflow type filter if not all workflow types are selected
    if (uniqueWorkflowDefs.length > 0 && selectedWorkflowDefs.length > 0 && 
        selectedWorkflowDefs.length !== uniqueWorkflowDefs.length) {
      
      // For multiple workflow types, use the IN operator
      if (selectedWorkflowDefs.length === 1) {
        // For a single workflow type, use a simple condition
        queryParts.push(`workflowType="${selectedWorkflowDefs[0]}"`);
      } else {
        // For multiple workflow types, use the IN operator with comma-separated values
        queryParts.push(`workflowType IN (${selectedWorkflowDefs.join(',')})`);
      }
    }
    
    // Add status filter for errored workflows (FAILED, TERMINATED, TIMED_OUT)
    queryParts.push(`status IN (FAILED,TERMINATED,TIMED_OUT,PAUSED)`);
    
    // Log the query for debugging
    const finalQuery = queryParts.join(' AND ');
    console.log("Query string:", finalQuery);
    
    return finalQuery;
  }, [selectedTimeRange, selectedWorkflowDefs, uniqueWorkflowDefs]);

  // Create a search object with the time range query
  const searchObj = useMemo(() => ({
    rowsPerPage,
    page: currentPage,
    sort: `${sortField}:${sortDirection.toUpperCase()}`,
    freeText: "",
    query: buildQueryString(),
    refreshTrigger
  }), [rowsPerPage, currentPage, sortField, sortDirection, buildQueryString, refreshTrigger]);
  
  // Call the workflow search hook and get loading state
  const { data: workflowData, error: searchError, isLoading: isSearching } = useWorkflowSearch(searchObj);

  // Process API data
  useEffect(() => {
    if (workflowData) {
      // Use API data
      setData(workflowData);
      setFilteredData(workflowData.results || []);
      setIsLoading(false);
      
      // Calculate metrics immediately when data is received
      if (workflowData.results && workflowData.results.length > 0) {
        setMetrics(calculateMetrics(workflowData.results));
        
      } else {
        // No results found
        console.log(`No data found with time range: ${selectedTimeRange}`);
        // Set empty metrics for no data
        setMetrics(calculateMetrics([]));
      }
    } else if (searchError) {
      console.error('Error fetching workflow data:', searchError);
      setData({ results: [], totalHits: 0 });
      setFilteredData([]);
      setIsLoading(false);
      setMetrics(calculateMetrics([]));
    }
  }, [workflowData, searchError, selectedTimeRange, calculateMetrics]);
  

  useEffect(() => {
    if (!data || !data.results) return;

    // Apply filters
    let results = [...data.results];
    
    if (workflowTypeFilter !== "all") {
      results = results.filter(workflow => workflow.workflowType === workflowTypeFilter);
    }
    
    if (statusFilter !== "all") {
      results = results.filter(workflow => workflow.status === statusFilter);
    }
    
    // Apply selected workflow type filter from chart click
    if (selectedWorkflowType) {
      results = results.filter(workflow => workflow.workflowType === selectedWorkflowType);
    }
    
    // Apply selected time period filter from chart click
    if (selectedTimePeriod) {
      const selectedTime = new Date(selectedTimePeriod);
      const startTime = new Date(selectedTime);
      const endTime = new Date(selectedTime);
      
      // Set the start time to the beginning of the hour
      startTime.setMinutes(0);
      startTime.setSeconds(0);
      startTime.setMilliseconds(0);
      
      // Set the end time to the end of the hour
      endTime.setMinutes(59);
      endTime.setSeconds(59);
      endTime.setMilliseconds(999);
      
      results = results.filter(workflow => {
        const workflowTime = new Date(workflow.startTime);
        return workflowTime >= startTime && workflowTime <= endTime;
      });
      
      // If no results found with exact hour, expand the range to ±1 hour
      if (results.length === 0 && selectedTimePeriod) {
        startTime.setHours(startTime.getHours() - 1);
        endTime.setHours(endTime.getHours() + 1);
        
        results = data.results.filter(workflow => {
          const workflowTime = new Date(workflow.startTime);
          return workflowTime >= startTime && workflowTime <= endTime;
        });
      }
    }
    
    // Apply selected reason for incompletion filter
    if (selectedReasonForIncompletion) {
      // Get all workflows that match the selected reason group
      const matchingWorkflows = filterWorkflowsByReason(data.results, selectedReasonForIncompletion);
      
      // Update results with all matching workflows
      results = results.filter(workflow => 
        matchingWorkflows.some(match => match.workflowId === workflow.workflowId)
      );
    }
    
    setFilteredData(results);
    
    // Calculate metrics based on filtered data
    setMetrics(calculateMetrics(results));
  }, [
    data, 
    workflowTypeFilter, 
    statusFilter, 
    selectedWorkflowType, 
    selectedTimePeriod, 
    selectedReasonForIncompletion, 
    uniqueWorkflowDefs, 
    calculateMetrics
  ]);

  const getWorkflowTypeData = useCallback(() => {
    if (!filteredData || filteredData.length === 0) return [];
    
    // Group by workflow type
    const groupedByType = _.groupBy(filteredData, 'workflowType');
    
    return Object.entries(groupedByType).map(([type, workflows]) => {
      const totalTime = workflows.reduce((sum, w) => sum + (w.executionTime || 0), 0);
      const avgTime = workflows.length > 0 ? totalTime / workflows.length : 0;
      
      return {
      name: type,
        count: workflows.length,
        avgTime: avgTime,
        totalTime: totalTime
      };
    });
  }, [filteredData]);

  const getStatusData = useCallback(() => {
    if (!filteredData || filteredData.length === 0) return [];
    
    // Group by status
    const groupedByStatus = _.groupBy(filteredData, 'status');
    
    return Object.entries(groupedByStatus).map(([status, workflows]) => ({
      name: status,
      value: workflows.length
    }));
  }, [filteredData]);

  // Replace the getReasonForIncompletionData function with the hook
  const reasonForIncompletionData = useWorkflowErrorGroups(filteredData);

  const getTimeseriesData = useCallback(() => {
    if (!filteredData || filteredData.length === 0) return [];
    
    // Group by hour
    const grouped = _.groupBy(filteredData, workflow => {
      const date = new Date(workflow.startTime);
      return new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()).toISOString();
    });
    
    // Convert to array and sort by time
    return Object.entries(grouped)
      .map(([timeKey, workflows]) => ({
        time: new Date(timeKey).getTime(),
        count: workflows.length
      }))
      .sort((a, b) => a.time - b.time);
  }, [filteredData]);

  // Memoize chart data calculations to prevent unnecessary recalculations
  const workflowTypeData = useMemo(() => getWorkflowTypeData(), [getWorkflowTypeData]);
  const statusData = useMemo(() => getStatusData(), [getStatusData]);
  const timeseriesData = useMemo(() => getTimeseriesData(), [getTimeseriesData]);

  // Handle workflow type bar click
  const handleWorkflowTypeClick = (data) => {
    if (data && data.name) {
      const workflowType = data.name;
      // If clicking the same type, toggle it off
      if (selectedWorkflowType === workflowType) {
        setSelectedWorkflowType(null);
      } else {
        setSelectedWorkflowType(workflowType);
        // Reset to first page when filter changes
        setCurrentPage(1);
      }
    }
  };

  // Reset workflow type filter
  const resetWorkflowTypeFilter = () => {
    setSelectedWorkflowType(null);
  };

  // Handle time period click
  const handleTimePeriodClick = (data) => {
    if (data && data.activePayload && data.activePayload.length > 0) {
      const clickedData = data.activePayload[0].payload;
      // If clicking the same time period, toggle it off
      if (selectedTimePeriod === clickedData.time) {
        setSelectedTimePeriod(null);
      } else {
        setSelectedTimePeriod(clickedData.time);
        // Reset to first page when filter changes
        setCurrentPage(1);
      }
    }
  };

  // Reset time period filter
  const resetTimePeriodFilter = () => {
    setSelectedTimePeriod(null);
  };

  

  // Function to refresh data
  const refreshData = useCallback(() => {
    // Increment the refresh trigger to force a re-fetch
    setRefreshTrigger(prev => prev + 1);
    
    // Reset the countdown timer
    setTimeUntilRefresh(60);
    
    // Show loading indicator briefly
    setIsLoading(true);
    
    // Hide loading indicator after a short delay if it's still showing
    setTimeout(() => {
      setIsLoading(prevLoading => {
        if (prevLoading) {
          return false;
        }
        return prevLoading;
      });
    }, 500);
  }, []);
  
  // Set up auto-refresh interval
  useEffect(() => {
    // Clear any existing interval
    if (refreshIntervalRef.current) {
      clearInterval(refreshIntervalRef.current);
    }
    
    // Only set up the interval if auto-refresh is enabled
    if (autoRefreshEnabled) {
      // Set up countdown timer that updates every second
      refreshIntervalRef.current = setInterval(() => {
        setTimeUntilRefresh(prevTime => {
          if (prevTime <= 1) {
            // Time to refresh
            refreshData();
            return 60; // Reset to 60 seconds
          }
          return prevTime - 1;
        });
      }, 1000);
    }
    
    // Clean up interval on component unmount or when autoRefreshEnabled changes
    return () => {
      if (refreshIntervalRef.current) {
        clearInterval(refreshIntervalRef.current);
      }
    };
  }, [autoRefreshEnabled, refreshData]);

  // Effect to handle data refresh completion
  useEffect(() => {
    if (workflowData && isLoading) {
      // Data has been refreshed
      setIsLoading(false);
    }
  }, [workflowData, isLoading]);
  
  // Effect to handle search errors
  useEffect(() => {
    if (searchError) {
      // Show error notification
      setNotification({
        message: "Failed to refresh data. Will try again during next live tail update.",
        type: "error",
        timestamp: new Date()
      });
      
      // Clear notification after 5 seconds
      const timer = setTimeout(() => {
        setNotification(null);
      }, 5000);
      
      return () => clearTimeout(timer);
    }
  }, [searchError]);

  // Reset auto-expansion state on component mount
  useEffect(() => {
    console.log("Initializing errored workflows dashboard with time range: 24h");
    
    // Start with 24 hours time range
    setSelectedTimeRange('24h');
    
    // Return cleanup function
    return () => {
      console.log("Dashboard component unmounting");
    };
  }, []);

  // Debug log for time range changes
  useEffect(() => {
    console.log(`Time range changed to: ${selectedTimeRange}`);
  }, [selectedTimeRange]);

  // Add handler for reason for incompletion chart click
  const handleReasonForIncompletionClick = (data) => {
    if (data && data.name) {
      const reason = data.name;
      // If clicking the same reason, toggle it off
      if (selectedReasonForIncompletion === reason) {
        setSelectedReasonForIncompletion(null);
        setReasonFilterFromChart(false);
      } else {
        setSelectedReasonForIncompletion(reason);
        setReasonFilterFromChart(true);
        // Reset to first page when filter changes
        setCurrentPage(1);
      }
    }
  };

  // Reset reason for incompletion filter
  const resetReasonForIncompletionFilter = () => {
    setSelectedReasonForIncompletion(null);
    setReasonFilterFromChart(false);
  };

  // Show loading spinner when searching
  if (isSearching) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "64px" }}>
        <div className="spinner" style={{ width: "40px", height: "40px", border: "4px solid #ccc", borderTop: "4px solid #1f83db", borderRadius: "50%", animation: "spin 1s linear infinite" }}></div>
        <p style={{ fontSize: "18px", marginLeft: "10px" }}>Loading errored workflow data...</p>
      </div>
    );
  }

  return (
    <div style={styles.main}>
      {/* Add style for notifications and animations */}
      <style>
        {cssStyles}
      </style>
      
      {/* Notification component */}
      {notification && (
        <Notification
          message={notification.message}
          type={notification.type}
          timestamp={notification.timestamp}
          onClose={() => setNotification(null)}
        />
      )}
      
      <div style={styles.headerContainer}>
        <h1 style={styles.title}>
          Errored Workflows Dashboard
        </h1>
        <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
          {/* Time Range Dropdown */}
          <TimeRangeDropdown
            selectedTimeRange={selectedTimeRange}
            timeRangeOptions={TIME_RANGE_OPTIONS}
            onTimeRangeChange={setSelectedTimeRange}
            onFilterChange={(newTimeRange) => {
              // Reset to first page when changing time range
              setCurrentPage(1);
              
              // Show notification if filters are active
              const hasActiveFilters = selectedWorkflowType || selectedTimePeriod || statusFilter !== "all";
              if (hasActiveFilters) {
                // Get the new time range label
                const timeRangeOption = TIME_RANGE_OPTIONS.find(option => option.value === newTimeRange);
                setNotification({
                  message: `Time range changed to Since ${timeRangeOption.label} while maintaining existing filters`,
                  type: "success",
                  timestamp: new Date()
                });
                
                // Clear notification after 3 seconds
                setTimeout(() => {
                  setNotification(null);
                }, 3000);
              }
            }}
          />
          
          {/* Live Tail button */}
          <LiveTailButton
            isEnabled={autoRefreshEnabled}
            isLoading={isLoading}
            timeUntilRefresh={timeUntilRefresh}
            onToggle={() => {
              const newState = !autoRefreshEnabled;
              setAutoRefreshEnabled(newState);
              
              // If enabling, refresh immediately and reset the timer
              if (newState) {
                refreshData();
                setTimeUntilRefresh(60);
              }
            }}
          />
        </div>
      </div>

      {/* Summary Cards */}
      {metrics && (
        <div style={styles.summaryCardContainer}>
          <SummaryCard
            title="Total Errored Workflows"
            value={filteredData.length}
            totalValue={data?.totalHits}
            noDataMessage="No errored workflow data found in the selected time range"
            style={styles.summaryCard(filteredData)}
          />
          
          <SummaryCard
            title="Failed"
            value={metrics.failedWorkflows}
            color={colors.red}
            percentage={filteredData && filteredData.length > 0 ? 
              `${((metrics.failedWorkflows / filteredData.length) * 100).toFixed(1)}%` : 
              undefined}
          />
          
          <SummaryCard
            title="Terminated"
            value={metrics.terminatedWorkflows}
            color={colors.purple}
            percentage={filteredData && filteredData.length > 0 ? 
              `${((metrics.terminatedWorkflows / filteredData.length) * 100).toFixed(1)}%` : 
              undefined}
          />
          
          <SummaryCard
            title="Timed Out"
            value={metrics.timedOutWorkflows}
            color={colors.yellow}
            percentage={filteredData && filteredData.length > 0 ? 
              `${((metrics.timedOutWorkflows / filteredData.length) * 100).toFixed(1)}%` : 
              undefined}
          />

          <SummaryCard
            title="Paused"
            value={metrics.pausedWorkflows}
            color={colors.blue}
            percentage={filteredData && filteredData.length > 0 ? 
              `${((metrics.pausedWorkflows / filteredData.length) * 100).toFixed(1)}%` : 
              undefined}
          />  
        </div>
      )}

      {/* Main Charts */}
      <div style={styles.gridContainer}>
        {/* Workflow Type Distribution */}
        <WorkflowTypeChart
          data={workflowTypeData}
          selectedWorkflowType={selectedWorkflowType}
          onWorkflowTypeClick={handleWorkflowTypeClick}
          onResetFilter={resetWorkflowTypeFilter}
          workflowDefsError={workflowDefsError}
        />

        {/* Status Distribution */}
        <StatusChart
          data={statusData}
          selectedStatus={statusFilter}
          onStatusClick={(status) => {
            setStatusFilter(status);
            setStatusFilterFromChart(true);
            setCurrentPage(1);
          }}
          onResetFilter={() => {
            setStatusFilter("all");
            setStatusFilterFromChart(false);
          }}
          isFilterActive={statusFilterFromChart}
        />

        {/* Reason for Incompletion Distribution */}
        <FailureReasonChart
          data={reasonForIncompletionData}
          selectedReason={selectedReasonForIncompletion}
          onReasonClick={handleReasonForIncompletionClick}
          onResetFilter={resetReasonForIncompletionFilter}
          isFilterActive={reasonFilterFromChart}
        />

        {/* Time Series Analysis */}
        <TimeSeriesChart
          data={timeseriesData}
          selectedTimePeriod={selectedTimePeriod}
          onTimePeriodClick={handleTimePeriodClick}
          onResetFilter={resetTimePeriodFilter}
          isFilterActive={!!selectedTimePeriod}
        />
      </div>

      {/* Results Table using imported component */}
      <div style={{...styles.card, marginTop: "20px"}}>
        <h2 style={styles.subtitle}>Errored Workflow Details</h2>
        
          {filteredData && filteredData.length > 0 ? (
          <ResultsTable 
            resultObj={{ 
              results: filteredData,
              totalHits: data?.totalHits || filteredData.length 
            }}
            error={null}
            busy={isLoading}
            page={currentPage}
            rowsPerPage={rowsPerPage}
            sort={`${sortField}:${sortDirection.toUpperCase()}`}
            setPage={setCurrentPage}
            setRowsPerPage={setRowsPerPage}
            setSort={(field, direction) => {
              setSortField(field);
              setSortDirection(direction === 'ASC' ? 'asc' : 'desc');
              setCurrentPage(1); // Reset to first page when sorting changes
            }}
            showMore={false}
          />
          ) : (
            <div style={styles.noResultsContainer}>
              No errored workflow data available
            </div>
          )}
        </div>
      </div>
  );
};

export default ErrorsInspector;
