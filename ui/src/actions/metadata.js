import http from '../core/HttpClient';

export const FETCH_WORKFLOW_METADATA = "FETCH_WORKFLOW_METADATA";
export const RECEIVE_WORKFLOW_METADATA = "RECEIVE_WORKFLOW_METADATA";
export const FAIL_WORKFLOW_METADATA = "FAIL_WORKFLOW_METADATA";

export function fetchWorkflowMetadata() {
  return {type: FETCH_WORKFLOW_METADATA};
}

export function receiveWorkflowMetadata(workflows) {
  return {type: RECEIVE_WORKFLOW_METADATA, workflows};
}

export function failWorkflowMetadata(error) {
  return function(dispatch) {
    dispatch({type: FAIL_WORKFLOW_METADATA, error});
    dispatch({type: 'REQUEST_ERROR', e});
  };
}

export function getWorkflowDefs() {
  return function (dispatch) {
    dispatch(fetchWorkflowMetadata());

    return http.get('/api/wfe/metadata/workflow').then(data => {
      dispatch(receiveWorkflowMetadata(data.result));
    }).catch((e) => {
      dispatch(failWorkflowMetadata(e));
    });
  }
}
