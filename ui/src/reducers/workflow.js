
const initialState = {
  byStatus: {},
  byId: {},
  storeStateId: 0,
  fetching: false,
  refetch: false,
  terminating: false,
  restarting: false,
  retrying: false,
  terminated: {},
  data: [],
  hash: ''
};

export default function workflows(state = initialState, action) {
  switch (action.type) {
    case 'GET_WORKFLOWS':
      return {
        ...state,
        error: false,
        fetching: true
      };
    case 'RECEIVED_WORKFLOWS':
      return {
        ...state,
        data: action.data.result,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'LOCATION_UPDATED':
      return {
        ...state,
        error: false,
        hash: action.location
      };
    case 'GET_WORKFLOW_DETAILS':
      return {
        ...state,
        fetching: true,
        error: false
      };
    case 'RECEIVED_WORKFLOW_DETAILS':
      return {
        ...state,
        data: action.data.result,
        meta: action.data.meta,
        subworkflows: action.data.subworkflows,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'REQUESTED_TERMINATE_WORKFLOW':
      return {
        ...state,
        fetching: true,
        terminating: true
      };
    case 'RECEIVED_TERMINATE_WORKFLOW':
      return {
        ...state,
        error: false,
        data:[],
        fetching: false,
        terminating: false,
        refetch: true
      };
    case 'RECEIVED_BULK_TERMINATE_WORKFLOW':
    case 'RECEIVED_BULK_RESTART_WORKFLOW':
    case 'RECEIVED_BULK_RETRY_WORKFLOW':
    case 'RECEIVED_BULK_PAUSE_WORKFLOW':
    case 'RECEIVED_BULK_RESUME_WORKFLOW':

      return {
        ...state,
        error:false,
        bulkProcessInFlight:false,
        bulkProcessSuccess: !action.data.bulkServerError && Object.keys(action.data.bulkErrorResults || {}).length === 0,
        bulkServerErrors: !(!action.data.bulkServerError && Object.keys(action.data.bulkErrorResults || {}).length === 0),
        bulkErrorResults: action.data.bulkErrorResults || {},
        bulkServerErrorMessage: action.data.bulkServerErrorMessage,
        bulkSuccessfulResults: action.data.bulkSuccessfulResults || []
      };
    case 'REQUESTED_BULK_TERMINATE_WORKFLOW':
    case 'REQUESTED_BULK_RESTART_WORKFLOW':
    case 'REQUESTED_BULK_RETRY_WORKFLOW':
    case 'REQUESTED_BULK_PAUSE_WORKFLOW':
    case 'REQUESTED_BULK_RESUME_WORKFLOW':
      return {
        ...state,
        bulkProcessInFlight:true,
        bulkProcessSuccess:false
      };
    case 'REQUESTED_RESTART_WORKFLOW':
      return {
        ...state,
        fetching: true,
        restarting: true
      };
    case 'REQUESTED_RETRY_WORKFLOW':
      return {
        ...state,
        fetching: true,
        retrying: true
      };
    case 'REQUESTED_PAUSE_WORKFLOW':
      return {
        ...state,
        fetching: true,
        pausing: true,
        resuming: false

      };
    case 'REQUESTED_RESUME_WORKFLOW':
      return {
        ...state,
        fetching: true,
        resuming: true,
        pausing: false
      };
    case 'RECEIVED_RESTART_WORKFLOW':
      return {
        ...state,
        error: false,
        data:[],
        fetching: false,
        restarting: false,
        refetch: true
      };
    case 'RECEIVED_RETRY_WORKFLOW':
      return {
        ...state,
        error: false,
        data:[],
        fetching: false,
        restarting: false,
        retrying: false,
        refetch: true
      };
    case 'RECEIVED_PAUSE_WORKFLOW':
      return {
        ...state,
        error: false,
        data:[],
        fetching: false,
        restarting: false,
        retrying: false,
        refetch: true,
        pausing: false,
        resuming: false
      };
    case 'RECEIVED_RESUME_WORKFLOW':
      return {
        ...state,
        error: false,
        data:[],
        fetching: false,
        restarting: false,
        retrying: false,
        refetch: true,
        resuming: false,
        pauing: false
      };
    case 'LIST_WORKFLOWS':
      return {
        ...state,
        error: false,
        fetching: true
      };
    case 'RECEIVED_LIST_WORKFLOWS':
      return {
        ...state,
        workflows: action.workflows.result,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'LIST_EVENT_HANDLERS':
      return {
        ...state,
        error: false,
        fetching: true
      };
    case 'RECEIVED_LIST_EVENT_HANDLERS':
      return {
        ...state,
        events: action.events,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'GET_WORKFLOW_DEF':
      return {
        ...state,
        fetching: true,
        error: false
      };
    case 'RECEIVED_WORKFLOW_DEF':
      return {
        ...state,
        meta: action.workflowMeta.result,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'REQUESTED_UPDATE_WORKFLOW_DEF':
      return {
        ...state,
        updating: true
      };
    case 'RECEIVED_UPDATE_WORKFLOW_DEF':
      return {
        ...state,
        error: false,
        updating: false,
        refetch: true
      };
    case 'GET_TASK_DEFS':
      return {
        ...state,
        error: false,
        fetching: true,
        refetch: false
      };
    case 'RECEIVED_TASK_DEFS':
      return {
        ...state,
        taskDefs: action.taskDefs.result,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'GET_POLL_DATA':
      return {
        ...state,
        error: false,
        fetching: true,
        refetch: false
      };
    case 'RECEIVED_POLL_DATA':
      return {
        ...state,
        queueData: action.queueData.polldata,
        error: false,
        fetching: false,
        refetch: false
      };
    case 'REQUEST_ERROR':
    return {
      ...state,
      error: true,
      exception: action.e,
      fetching: false,
      restarting: false,
      terminating: false,
      retrying: false,
      pausing: false,
      resumign: false,
      bulkProcessInFlight:false,
      bulkProcessSuccess:false
    };
    case 'GET_TASK_LOGS':
      return {
        ...state,
        fetching: true,
        error: false
      };
    case 'RECEIVED_GET_TASK_LOGS':
      return {
        ...state,
        logs: action.logs,
        error: false,
        fetching: false,
        refetch: false
      };
    default:
      return state;
    };
}
