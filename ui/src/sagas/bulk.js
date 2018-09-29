import {call, put, takeLatest} from 'redux-saga/effects';
import {PERFORM_BULK_OPERATION} from '../actions/bulk';
import {failBulkOperation, receiveBulkOperationResponse} from '../actions/bulk';
import http from '../core/HttpClient';


function* sendBulkRequest(action) {
  const {operation, workflows} = action;

  console.log(action, operation, workflows);

  const url = `/api/wfe/bulk/${operation}`;

  let response;

  try {
    switch (operation) {
      case "retry":
      case "restart":
        response = yield call(http.post, url, workflows);
        break;
      case "pause":
      case "resume":
        response = yield call(http.put, url, workflows);
        break;
      case "terminate":
        response = yield call(http.delete, url, workflows);
        break;
      default:
        throw "Invalid operation requested.";
    }
  } catch (e) {
    yield put(failBulkOperation(e.message));
    return
  }

  const {bulkErrorResults, bulkSuccessfulResults} = response;

  yield put(receiveBulkOperationResponse(bulkSuccessfulResults, bulkErrorResults));
}

export default [
  takeLatest(PERFORM_BULK_OPERATION, sendBulkRequest)
];
