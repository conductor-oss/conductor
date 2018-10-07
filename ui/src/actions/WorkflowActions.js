import http from '../core/HttpClient';


export function getWorkflowDetails(workflowId){
  return function (dispatch) {
    dispatch({
      type: 'GET_WORKFLOW_DETAILS',
      workflowId
    });


    return http.get('/api/wfe/id/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_WORKFLOW_DETAILS',
        data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function terminateWorkflow(workflowId){
  return function (dispatch) {
    dispatch({
      type: 'REQUESTED_TERMINATE_WORKFLOW',
      workflowId
    });


    return http.delete('/api/wfe/terminate/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_TERMINATE_WORKFLOW',
        workflowId
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function restartWorfklow(workflowId){
  return function (dispatch) {
    dispatch({
      type: 'REQUESTED_RESTART_WORKFLOW',
      workflowId
    });


    return http.post('/api/wfe/restart/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_RESTART_WORKFLOW',
        workflowId
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function retryWorfklow(workflowId){
  return function (dispatch) {
    dispatch({
      type: 'REQUESTED_RETRY_WORKFLOW',
      workflowId
    });


    return http.post('/api/wfe/retry/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_RETRY_WORKFLOW',
        workflowId
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function pauseWorfklow(workflowId) {
  return function (dispatch) {
    dispatch({
      type: 'REQUESTED_PAUSE_WORKFLOW',
      workflowId
    });


    return http.post('/api/wfe/pause/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_PAUSE_WORKFLOW',
        workflowId
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function resumeWorfklow(workflowId) {
  return function (dispatch) {
    dispatch({
      type: 'REQUESTED_RESUME_WORKFLOW',
      workflowId
    });


    return http.post('/api/wfe/resume/' + workflowId).then((data) => {
      dispatch({
        type: 'RECEIVED_RESUME_WORKFLOW',
        workflowId
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

//metadata
export function getWorkflowDefs() {
  return function (dispatch) {
    dispatch({
      type: 'LIST_WORKFLOWS'
    });

    return http.get('/api/wfe/metadata/workflow').then((data) => {
      dispatch({
        type: 'RECEIVED_LIST_WORKFLOWS',
        workflows : data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function getWorkflowMetaDetails(name, version){
  return function (dispatch) {
    dispatch({
      type: 'GET_WORKFLOW_DEF',
      name,
      version
    });


    return http.get('/api/wfe/metadata/workflow/' + name + '/' + version).then((data) => {
      dispatch({
        type: 'RECEIVED_WORKFLOW_DEF',
        name,
        version,
        workflowMeta: data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function getTaskDefs() {

  return function (dispatch) {
    dispatch({
      type: 'GET_TASK_DEFS'
    });


    return http.get('/api/wfe/metadata/taskdef').then((data) => {
      dispatch({
        type: 'RECEIVED_TASK_DEFS',
        taskDefs: data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}

export function getQueueData() {

  return function (dispatch) {
    dispatch({
      type: 'GET_POLL_DATA'
    });


    return http.get('/api/wfe/queue/data').then((data) => {
      dispatch({
        type: 'RECEIVED_POLL_DATA',
        queueData: data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}


export function getEventHandlers() {

  return function (dispatch) {
    dispatch({
      type: 'LIST_EVENT_HANDLERS'
    });


    return http.get('/api/events').then((data) => {
      dispatch({
        type: 'RECEIVED_LIST_EVENT_HANDLERS',
        events : data
      });
    }).catch((e) => {
      dispatch({
        type: 'REQUEST_ERROR',
        e
      });
    });
  }
}
